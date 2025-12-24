package com.example.musify

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.Home.RecentlyPlayedManager
import com.example.musify.databinding.ActivityArtistBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Artists
import com.example.musify.songData.Download
import com.example.musify.songData.Image
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class ArtistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArtistBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var musicPlayerService: MusicPlayerService ?= null
    private var bound = false
    private lateinit var miniPlayer: View
    private lateinit var songName: TextView
    private lateinit var artistName: TextView
    private lateinit var artistNameText: TextView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var favouriteIcon: AppCompatImageView
    private lateinit var apiUrl: String
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.LocalBinder
            musicPlayerService = binder.getService()
            bound = true

            // Observe LiveData directly with Activity as LifecycleOwner
            musicPlayerService?.currentSongLive?.observe(this@ArtistActivity) { songItem ->
                if (songItem == null) {
                    // Hide with animation
                    miniPlayer.animate()
                        .translationY(miniPlayer.height.toFloat()) // slide down
                        .alpha(0f) // fade out
                        .setDuration(500)
                        .withEndAction {
                            miniPlayer.visibility = View.GONE
                        }
                        .start()
                    val paddingInDp = 10
                    val scale = resources.displayMetrics.density
                    val paddingInPx = (paddingInDp * scale).toInt()
                    binding.constraintLayout0.setPadding(0,0,0,paddingInPx)
                } else {
                    if (miniPlayer.visibility != View.VISIBLE) {
                        // Prepare for animation
                        miniPlayer.translationY = miniPlayer.height.toFloat()
                        miniPlayer.alpha = 0f
                        miniPlayer.visibility = View.VISIBLE

                        val paddingInDp = 90
                        val scale = resources.displayMetrics.density
                        val paddingInPx = (paddingInDp * scale).toInt()
                        binding.constraintLayout0.setPadding(0,0,0,paddingInPx)

                        // Show with animation
                        miniPlayer.animate()
                            .translationY(0f) // slide up
                            .alpha(1f) // fade in
                            .setDuration(500)
                            .start()
                    }
                }
                updateMiniPlayer(songItem)
            }

            musicPlayerService?.isPlayingLive?.observe(this@ArtistActivity) { playing ->
                updatePlayPauseIcon(playing)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            musicPlayerService = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (!bound) {
            val serviceIntent = Intent(this, MusicPlayerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindService(serviceIntent, connection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityArtistBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightNavigationBars = false

        handleBottomNavPosition()

        setStatusBarIconsTheme(this)

        apiUrl = getString(R.string.API)

        binding.progressBar.fadeIn()
        binding.scrollView2.fadeOut()

        val artistID = intent.getStringExtra("artistID")

        fetchArtistByID(artistID!!)

        binding.backArrowIconImage.setOnClickListener {
            finish()
        }

        miniPlayer = findViewById(R.id.miniPlayer)
        songName = findViewById(R.id.songNameText)
        artistName = findViewById(R.id.artistNameText)
        lottieAnimationView = findViewById(R.id.lottieAnimationView)
        artistNameText = findViewById(R.id.artistNameTextView)
        playPauseButton = findViewById(R.id.playButton)
        nextButton = findViewById(R.id.appCompatImageView7)
        prevButton = findViewById(R.id.appCompatImageView3)

        favouriteIcon = findViewById(R.id.favouriteIcon1)

        playPauseButton.setOnClickListener {
            if (musicPlayerService?.isPlayingLive?.value == true) {
                musicPlayerService?.pause()
            } else {
                musicPlayerService?.resume()
            }
        }

        nextButton.setOnClickListener {
            musicPlayerService?.next()
        }

        prevButton.setOnClickListener {
            musicPlayerService?.previous()
        }

        miniPlayer.setOnClickListener {
            val intent = Intent(this, PlaySong::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_bottom,
                0
            )
            startActivity(intent, options.toBundle())
        }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference().child("Users")
    }
    private fun fetchArtistByID(artistID: String) {
        lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/artists?id=${artistID}")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        Log.e("SAAVN", "Error: ${response.code}")
                        throw Exception("Error: ${response.code}")
                    }

                    response.body.string()
                }

                if (responseBody.isEmpty()) {
                    Toast.makeText(this@ArtistActivity, "Empty response", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                parseArtistJson(responseBody)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private suspend fun parseArtistJson(jsonString: String) {
        var artistId = ""
        var artistName = ""
        var followerCount= 0
        var fanCount = ""
        var isVerified = false
        var imageUrl = ""
        val topSongsList = ArrayList<SongItem>()
        val topAlbumsList = mutableListOf<DataItem>()
        val singlesList = mutableListOf<DataItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val artistObject = json.getJSONObject("data")

            artistId = artistObject.optString("id")
            artistName = artistObject.optString("name")
            followerCount = artistObject.optInt("followerCount")
            fanCount = artistObject.optString("fanCount")
            isVerified = artistObject.optBoolean("isVerified", false)

            val imageArray = artistObject.optJSONArray("image")
            imageUrl = if (imageArray != null && imageArray.length() > 0) {
                imageArray.getJSONObject(2).optString("url")
            } else ""

            val topSongsArray = artistObject.optJSONArray("topSongs")
            if (topSongsArray != null) {
                for (i in 0 until topSongsArray.length()) {
                    val songObject = topSongsArray.getJSONObject(i)

                    val id = songObject.optString("id")
                    val name = songObject.optString("name")
                    val duration = songObject.optInt("duration")

                    val imageArray = songObject.optJSONArray("image")
                    val image = mutableListOf<Image>()
                    if (imageArray != null) {
                        for (j in 0 until imageArray.length()) {
                            val imageObject = imageArray.getJSONObject(j)
                            image.add(
                                Image(
                                    quality = imageObject?.optString("quality") ?: "",
                                    url = imageObject?.optString("url") ?: ""
                                )
                            )
                        }
                    }

                    val downloadArray = songObject.optJSONArray("downloadUrl")
                    val download = mutableListOf<Download>()
                    if (downloadArray != null) {
                        for (k in 0 until downloadArray.length()) {
                            val downloadObject = downloadArray.getJSONObject(k)
                            download.add(
                                Download(
                                    quality = downloadObject?.optString("quality") ?: "",
                                    url = downloadObject?.optString("url") ?: ""
                                )
                            )
                        }
                    }

                    val artistsObj = songObject.optJSONObject("artists")
                    val primaryArray = artistsObj?.optJSONArray("primary")
                    val primaryArtists = mutableListOf<Artists>()
                    for (i in 0 until (primaryArray?.length() ?: 0)) {
                        val artistsObject = primaryArray?.getJSONObject(i)
                        val artistsImage = artistsObject?.optJSONArray("image")

                        primaryArtists.add(
                            Artists(
                                id = artistsObject?.optString("id") ?: "",
                                name = artistsObject?.optString("name") ?: "",
                                role = artistsObject?.optString("role") ?: "",
                                image = artistsImage?.optJSONObject(1)?.optString("url") ?: "",
                                type = artistsObject?.optString("type") ?: ""
                            )
                        )
                    }

                    topSongsList.add(SongItem(id, name, primaryArtists, image, duration, download))
                }
            }

            val topAlbumsArray = artistObject.optJSONArray("topAlbums")
            if (topAlbumsArray != null) {
                for (i in 0 until topAlbumsArray.length()) {
                    val songObject = topAlbumsArray.getJSONObject(i)

                    val id = songObject.optString("id")
                    val name = songObject.optString("name")

                    val imageArray = songObject.optJSONArray("image")
                    val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                        imageArray.getJSONObject(1).optString("url")
                    } else ""

                    val artistsObj = songObject.optJSONObject("artists")
                    val primaryArtists = artistsObj?.optJSONArray("primary")
                    val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                        primaryArtists.getJSONObject(0).optString("name")
                    } else ""

                    topAlbumsList.add(DataItem(id, name, artistName, imageUrl))
                }
            }

            val singlesArray = artistObject.getJSONArray("singles")
            if (singlesArray != null) {
                for (i in 0 until singlesArray.length()) {
                    val songObject = singlesArray.getJSONObject(i)

                    val id = songObject.optString("id")
                    val name = songObject.optString("name")

                    val imageArray = songObject.optJSONArray("image")
                    val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                        imageArray.getJSONObject(1).optString("url")
                    } else ""

                    val artistsObj = songObject.optJSONObject("artists")
                    val primaryArtists = artistsObj?.optJSONArray("primary")
                    val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                        primaryArtists.getJSONObject(0).optString("name")
                    } else ""

                    singlesList.add(DataItem(id, name, artistName, imageUrl))
                }
            }
        }

        withContext(Dispatchers.Main) {
            Picasso.get().load(imageUrl).into(binding.imageView)
            binding.artistNameTextView.text = artistName

            if (isVerified) {
                binding.verifiedImage.setImageResource(R.drawable.verified)
            } else {
                binding.verifiedImage.visibility = View.INVISIBLE
            }

            val userID = auth.currentUser?.uid
            val favouriteReference = database.child(userID!!).child("Favourites").child("Artists").child(artistId)

            favouriteReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isFavourite = snapshot.child("isFavourite").getValue(Boolean::class.java) ?: false
                    favouriteIcon.isSelected = isFavourite
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            favouriteIcon.setOnClickListener {
                val anim = AnimationUtils.loadAnimation(this@ArtistActivity, R.anim.nav_item_click)
                favouriteIcon.startAnimation(anim)

                favouriteReference.get().addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        val songData = mapOf(
                            "id" to artistId,
                            "artistName" to artistName,
                            "isFavourite" to true
                        )
                        favouriteReference.setValue(songData).addOnSuccessListener {
                            favouriteIcon.isSelected = true
                            Toast.makeText(this@ArtistActivity, "Added To Favourite", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            favouriteIcon.isSelected = false
                            Toast.makeText(this@ArtistActivity, "Failed To Add in Favourite", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        favouriteReference.removeValue().addOnSuccessListener {
                            favouriteIcon.isSelected = false
                            Toast.makeText(this@ArtistActivity, "Removed From Favourite", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val followers = formatCount(followerCount.toLong())
            val listeners = formatCount(fanCount.toLong())
            "Followers : $followers".also { binding.followersText.text = it }
            "Listeners : $listeners".also { binding.listenerText.text = it }

            val favSongRef = database.child(userID).child("Favourites").child("Songs")

            val topSongsAdapter = SuggestionSongAdapter(topSongsList) { songItem ->
                if (songItem.isFav) {
                    val songData = mapOf(
                        "id" to songItem.id,
                        "songName" to songItem.name,
                        "isFavourite" to true
                    )
                    favSongRef.child(songItem.id).setValue(songData)
                    Toast.makeText(this@ArtistActivity, "Added To Favourite", Toast.LENGTH_SHORT).show()
                } else {
                    favSongRef.child(songItem.id).removeValue()
                    Toast.makeText(this@ArtistActivity, "Removed From Favourite", Toast.LENGTH_SHORT).show()
                }
            }
            binding.topSongsRecyclerView.layoutManager = LinearLayoutManager(this@ArtistActivity, LinearLayoutManager.VERTICAL, false)
            binding.topSongsRecyclerView.adapter = topSongsAdapter

            topSongsAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    val intent = Intent(this@ArtistActivity, MusicPlayerService::class.java).apply {
                        action = MusicPlayerService.ACTION_PLAY_NEW
                        putParcelableArrayListExtra("playlist", topSongsList)
                        putExtra("index", position)
                    }

                    ContextCompat.startForegroundService(this@ArtistActivity, intent)
                    RecentlyPlayedManager.addToRecentlyPlayed(this@ArtistActivity,topSongsList[position])
                }
            })

            favSongRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val favoriteIds = snapshot.children.mapNotNull { it.key }.toSet()

                        topSongsList.forEach { song ->
                            song.isFav = favoriteIds.contains(song.id)
                        }
                        topSongsAdapter.notifyDataSetChanged()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FAV", "Error loading favourites", error.toException())
                }
            })

            binding.textView15.visibility = if (topAlbumsList.isNotEmpty()) View.VISIBLE else View.GONE
            binding.topAlbumsRecyclerView.visibility = if (topAlbumsList.isNotEmpty()) View.VISIBLE else View.GONE

            val topAlbumsAdapter = AlbumAdapter(topAlbumsList)
            binding.topAlbumsRecyclerView.layoutManager = LinearLayoutManager(
                this@ArtistActivity,
                LinearLayoutManager.HORIZONTAL, false
            )
            binding.topAlbumsRecyclerView.adapter = topAlbumsAdapter
            topAlbumsAdapter.setOnItemClickListener(object : AlbumAdapter.OnItemClickListener {
                override fun omItemClick(position: Int) {
                    val intent = Intent(this@ArtistActivity, AlbumActivity::class.java)
                    intent.putExtra("id", topAlbumsList[position].id)
                    startActivity(intent)
                }
            })

            binding.textView16.visibility = if (singlesList.isNotEmpty()) View.VISIBLE else View.GONE
            binding.singlesRecyclerView.visibility = if (singlesList.isNotEmpty()) View.VISIBLE else View.GONE

            val singlesAdapter = AlbumAdapter(singlesList)
            binding.singlesRecyclerView.layoutManager = LinearLayoutManager(
                this@ArtistActivity,
                LinearLayoutManager.HORIZONTAL, false
            )
            binding.singlesRecyclerView.adapter = singlesAdapter
            singlesAdapter.setOnItemClickListener(object : AlbumAdapter.OnItemClickListener {
                override fun omItemClick(position: Int) {
                    val intent = Intent(this@ArtistActivity, AlbumActivity::class.java)
                    intent.putExtra("id", singlesList[position].id)
                    startActivity(intent)
                }
            })

            binding.progressBar.fadeOut()
            binding.scrollView2.fadeIn()
        }
    }
    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format(Locale.US,"%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format(Locale.US,"%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US,"%.1fK", count / 1_000.0)
            else -> count.toString()
        }.replace(".0", "")
    }
    private fun updateMiniPlayer(songItem: SongItem?) {
        val artistsName = songItem?.artist
            ?.takeIf { it.isNotEmpty() }     // only proceed if list not empty
            ?.joinToString(", ") { it.name } // join all artist names
            ?: "Unknown Artist"              // fallback if null or empty

        songName.text = Html.fromHtml(songItem?.name ?: "", Html.FROM_HTML_MODE_LEGACY)
        artistName.text = Html.fromHtml(artistsName,Html.FROM_HTML_MODE_LEGACY)
//        Picasso.get().load(songItem?.image[1]?.url).into(songImage)
//        setDynamicBackground(songItem?.image[1]?.url ?: "",songImage,background)
    }
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (isPlaying) {
            playPauseButton.setImageResource(R.drawable.pausebutton)
            lottieAnimationView.playAnimation()
        } else {
            playPauseButton.setImageResource(R.drawable.playbutton)
            lottieAnimationView.pauseAnimation()
        }
    }
    private fun Int.dpToPx(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()
    private fun handleBottomNavPosition() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Typical values:
            // Gesture: 16–24dp
            // 3-button: 48–80dp

            val threshold = 40.dpToPx(binding.root)

            binding.main.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (navBarHeight > threshold) {
                    navBarHeight   // 3-button → move up
                } else {
                    val marginInDp = 12
                    val scale = resources.displayMetrics.density
                    val marginInPx = (marginInDp * scale).toInt()
                    miniPlayer.setPadding(0,0,0,marginInPx)
                    0              // Gesture → stay at bottom
                }
            }

            insets
        }
    }
    private fun View.fadeIn(duration: Long = 300) {
        this.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(duration).start()
        }
    }
    private fun View.fadeOut(duration: Long = 300, onEnd: (() -> Unit)? = null) {
        this.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }
    private fun setStatusBarIconsTheme(activity: Activity) {
        val window = activity.window
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)

        // Detect current theme
        val isDarkTheme =
            (activity.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Set icon color automatically
        if (isDarkTheme) {
            // Light icons for dark theme
            insetsController.isAppearanceLightStatusBars = false
        } else {
            // Dark icons for light theme
            insetsController.isAppearanceLightStatusBars = false
        }
    }
}