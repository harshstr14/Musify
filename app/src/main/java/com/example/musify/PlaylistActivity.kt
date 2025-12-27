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
import com.example.musify.databinding.ActivityPlaylistBinding
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
import java.util.concurrent.TimeUnit

class PlaylistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var musicPlayerService: MusicPlayerService ?= null
    private var bound = false
    private lateinit var miniPlayer: View
    private lateinit var songName: TextView
    private lateinit var artistName: TextView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
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
            musicPlayerService?.currentSongLive?.observe(this@PlaylistActivity) { songItem ->
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

            musicPlayerService?.isPlayingLive?.observe(this@PlaylistActivity) { playing ->
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
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
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

        apiUrl = BuildConfig.API_BASE_URL

        binding.progressBar.fadeIn()
        binding.scrollView.fadeOut()

        val playlistID = intent.getStringExtra("id")

        fetchPlaylistByID(playlistID!!)

        binding.backArrowIconImage.setOnClickListener {
            finish()
        }

        miniPlayer = findViewById(R.id.miniPlayer)
        songName = findViewById(R.id.songNameText)
        artistName = findViewById(R.id.artistNameText)
        lottieAnimationView = findViewById(R.id.lottieAnimationView)
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
    private fun fetchPlaylistByID(playlistID: String) {
        lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/playlists?id=$playlistID&limit=40")
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
                    Toast.makeText(this@PlaylistActivity, "Empty response", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                parsePlaylistJson(responseBody)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private suspend fun parsePlaylistJson(jsonString: String) {
        val songList = ArrayList<SongItem>()
        var totalDuration = 0

        var playlistId = ""
        var playlistName = ""
        var description = ""
        var songCount = ""
        val playlistImages = mutableListOf<Image>()
        val primaryArtists = mutableListOf<Artists>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success",false)
            if (!success) return@withContext

            val playlistObject = json.getJSONObject("data")

            playlistId = playlistObject.optString("id")
            playlistName = playlistObject.optString("name")
            description = playlistObject.optString("description")
            songCount = playlistObject.optString("songCount")

            val artistsArray = playlistObject.optJSONArray("artists")
            for (i in 0 until (artistsArray?.length() ?: 0)) {
                val artistsObject = artistsArray?.getJSONObject(i)
                val artistsImage = artistsObject?.optJSONArray("image")

                primaryArtists.add(
                    Artists(
                        id = artistsObject?.optString("id") ?: "",
                        name = artistsObject?.optString("name") ?: "",
                        role = artistsObject?.optString("role") ?: "",
                        image = artistsImage?.optJSONObject(2)?.optString("url") ?: "",
                        type = artistsObject?.optString("type") ?: ""
                    )
                )
            }

            val imageArray = playlistObject.optJSONArray("image")
            if (imageArray != null) {
                for (j in 0 until imageArray.length()) {
                    val imageObject = imageArray.getJSONObject(j)
                    playlistImages.add(
                        Image(
                            quality = imageObject?.optString("quality") ?: "",
                            url = imageObject?.optString("url") ?: ""
                        )
                    )
                }
            }

            val songArray = playlistObject.optJSONArray("songs")
            if (songArray != null) {
                for (j in 0 until songArray.length()) {
                    val songObject = songArray.getJSONObject(j)

                    val id = songObject.optString("id")
                    val name = songObject.optString("name")
                    val duration = songObject.optInt("duration")
                    totalDuration += duration

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

                    songList.add(SongItem(id, name, primaryArtists, image, duration, download))
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (playlistImages.isEmpty()) {
                binding.imageView.setImageResource(R.drawable.playlist_image)
            } else {
                Picasso.get().load(playlistImages[2].url).into(binding.imageView)
            }
            binding.playlistNameText.text = Html.fromHtml(playlistName,Html.FROM_HTML_MODE_LEGACY)
            binding.descriptionText.text = Html.fromHtml(description,Html.FROM_HTML_MODE_LEGACY)
            "Song  :  $songCount".also { binding.totalSongText.text = it }
            Log.d("totalDuration","$totalDuration")
            val duration = formatDuration(totalDuration)
            binding.durationText.text = duration

            val userID = auth.currentUser?.uid
            val favouriteReference = database.child(userID!!).child("Favourites").child("Playlists").child(playlistId)

            favouriteReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isFavourite = snapshot.child("isFavourite").getValue(Boolean::class.java) ?: false
                    favouriteIcon.isSelected = isFavourite
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            favouriteIcon.setOnClickListener {
                val anim = AnimationUtils.loadAnimation(this@PlaylistActivity, R.anim.nav_item_click)
                favouriteIcon.startAnimation(anim)

                favouriteReference.get().addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        val songData = mapOf(
                            "id" to playlistId,
                            "playlistName" to playlistName,
                            "isFavourite" to true
                        )
                        favouriteReference.setValue(songData).addOnSuccessListener {
                            favouriteIcon.isSelected = true
                            Toast.makeText(this@PlaylistActivity, "Added To Favourite", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            favouriteIcon.isSelected = false
                            Toast.makeText(this@PlaylistActivity, "Failed To Add in Favourite", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        favouriteReference.removeValue().addOnSuccessListener {
                            favouriteIcon.isSelected = false
                            Toast.makeText(this@PlaylistActivity, "Removed From Favourite", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val favSongRef = database.child(userID).child("Favourites").child("Songs")

            val songAdapter = SuggestionSongAdapter(songList) { songItem ->
                if (songItem.isFav) {
                    val songData = mapOf(
                        "id" to songItem.id,
                        "songName" to songItem.name,
                        "isFavourite" to true
                    )
                    favSongRef.child(songItem.id).setValue(songData)
                    Toast.makeText(this@PlaylistActivity, "Added To Favourite", Toast.LENGTH_SHORT).show()
                } else {
                    favSongRef.child(songItem.id).removeValue()
                    Toast.makeText(this@PlaylistActivity, "Removed From Favourite", Toast.LENGTH_SHORT).show()
                }
            }
            binding.songRecyclerView.layoutManager = LinearLayoutManager(this@PlaylistActivity)
            binding.songRecyclerView.isNestedScrollingEnabled = false
            binding.songRecyclerView.adapter = songAdapter

            songAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    val intent = Intent(this@PlaylistActivity, MusicPlayerService::class.java).apply {
                        action = MusicPlayerService.ACTION_PLAY_NEW
                        putParcelableArrayListExtra("playlist", songList)
                        putExtra("index", position)
                    }

                    ContextCompat.startForegroundService(this@PlaylistActivity, intent)
                    RecentlyPlayedManager.addToRecentlyPlayed(this@PlaylistActivity,songList[position])
                }
            })

            favSongRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val favoriteIds = snapshot.children.mapNotNull { it.key }.toSet()

                        songList.forEach { song ->
                            song.isFav = favoriteIds.contains(song.id)
                        }
                        songAdapter.notifyDataSetChanged()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FAV", "Error loading favourites", error.toException())
                }
            })

            val artistsAdapter = ArtistsAdapter(primaryArtists)
            binding.artistRecyclerView.layoutManager = LinearLayoutManager(this@PlaylistActivity, LinearLayoutManager.HORIZONTAL,false)
            binding.artistRecyclerView.adapter = artistsAdapter

            artistsAdapter.setOnItemClickListener(object : ArtistsAdapter.OnItemClickListener {
                override fun omItemClick(position: Int) {
                    val intent = Intent(this@PlaylistActivity, ArtistActivity::class.java)
                    intent.putExtra("artistID",primaryArtists[position].id)
                    startActivity(intent)
                }
            })

            binding.progressBar.fadeOut()
            binding.scrollView.fadeIn()

            val anim = AnimationUtils.loadAnimation(this@PlaylistActivity,R.anim.nav_item_click)

            binding.playButtonIcon.setOnClickListener {
                binding.playButtonIcon.startAnimation(anim)
                val intent = Intent(this@PlaylistActivity, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                }

                ContextCompat.startForegroundService(this@PlaylistActivity, intent)
                musicPlayerService?.updateNotification()
            }

            binding.shuffleButton.setOnClickListener {
                Toast.makeText(this@PlaylistActivity,"Playing with Shuffle", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@PlaylistActivity, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                }

                ContextCompat.startForegroundService(this@PlaylistActivity, intent)

                binding.shuffleButton.startAnimation(anim)
                musicPlayerService?.updateNotification()
                musicPlayerService?.isShuffle?.value = !(musicPlayerService?.isShuffle?.value ?: false)
            }
        }
    }
    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return buildString {
            if (hours > 0) append("$hours h ")
            if (minutes > 0) append("$minutes min  ")
            if (seconds > 0) append("$seconds s")
            if (isEmpty()) append("0s") // handle 0 case
        }.trim()
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