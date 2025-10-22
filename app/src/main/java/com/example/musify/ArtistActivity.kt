package com.example.musify

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.example.musify.databinding.ActivityArtistBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Image
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

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
    private lateinit var songImage: AppCompatImageView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var background: AppCompatImageView
    private lateinit var backgroundView: CardView
    private lateinit var favouriteIcon: AppCompatImageView

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
                } else {
                    if (miniPlayer.visibility != View.VISIBLE) {
                        // Prepare for animation
                        miniPlayer.translationY = miniPlayer.height.toFloat()
                        miniPlayer.alpha = 0f
                        miniPlayer.visibility = View.VISIBLE

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
        val intent = Intent(this, MusicPlayerService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
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

        enableEdgeToEdgeWithInsets(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = ContextCompat.getColor(
            this,
            if (isDark) R.color.status_bar_dark else R.color.status_bar_light
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decor = window.decorView
            decor.systemUiVisibility = if (isDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding.progressBar.fadeIn()
        binding.scrollView2.fadeOut()

        val artistID = intent.getStringExtra("artistID")

        fetchArtistByID(artistID!!)

        binding.backArrowIconImage.setOnClickListener {
            finish()
        }

        backgroundView = findViewById(R.id.backgroundView)
        miniPlayer = findViewById(R.id.miniPlayer)
        songName = findViewById(R.id.songNameText)
        artistName = findViewById(R.id.artistNameText)
        artistNameText = findViewById(R.id.artistNameTextView)
        songImage = findViewById(R.id.songImage)
        playPauseButton = findViewById(R.id.playButton)
        nextButton = findViewById(R.id.appCompatImageView7)
        prevButton = findViewById(R.id.appCompatImageView3)
        background = findViewById(R.id.backGroundImageView)

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
    fun fetchArtistByID(artistID: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/artists?id=${artistID}")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Log.d("SAAVN", "Artist API Response: $responseBody")
                    parseArtistJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun parseArtistJson(jsonString: String) {
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

        val artistObject = json.getJSONObject("data")

        val id = artistObject.optString("id")
        val name = artistObject.optString("name")
        val followerCount = artistObject.optInt("followerCount")
        val fanCount = artistObject.optString("fanCount")
        val isVerified = artistObject.optBoolean("isVerified", false)

        val imageArray = artistObject.optJSONArray("image")
        val imageUrl = if (imageArray != null && imageArray.length() > 0) {
            imageArray.getJSONObject(2).optString("url")
        } else ""

        val topSongsArray = artistObject.optJSONArray("topSongs")
        val topSongsList = ArrayList<SongItem>()
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
                val downloadUrl = if (downloadArray != null && downloadArray.length() > 0) {
                    downloadArray.getJSONObject(2).optString("url")
                } else ""

                val artistsObj = songObject.optJSONObject("artists")
                val primaryArtists = artistsObj?.optJSONArray("primary")
                val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                    primaryArtists.getJSONObject(0).optString("name")
                } else ""

                topSongsList.add(SongItem(id, name, artistName, image, duration, downloadUrl))
            }
        }

        val topAlbumsArray = artistObject.optJSONArray("topAlbums")
        val topAlbumsList = mutableListOf<DataItem>()
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
        val singlesList = mutableListOf<DataItem>()
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

        runOnUiThread {
            Picasso.get().load(imageUrl).into(binding.imageView)
            binding.artistNameTextView.text = name

            if (isVerified) {
                binding.verifiedImage.setImageResource(R.drawable.verified)
            } else {
                binding.verifiedImage.visibility = View.INVISIBLE
            }

            val userID = auth.currentUser?.uid
            val favouriteReference = database.child(userID!!).child("Favourites").child("Artists").child(id)

            favouriteReference.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isFavourite = snapshot.child("isFavourite").getValue(Boolean::class.java) ?: false
                    favouriteIcon.isSelected = isFavourite
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            favouriteIcon.setOnClickListener {
                val anim = AnimationUtils.loadAnimation(this, R.anim.nav_item_click)
                favouriteIcon.startAnimation(anim)

                favouriteReference.get().addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        val songData = mapOf(
                            "id" to id,
                            "artistName" to name,
                            "isFavourite" to true
                        )
                        favouriteReference.setValue(songData).addOnSuccessListener {
                            Toast.makeText(this, "Added To Favourite", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            Toast.makeText(this, "Failed To Add in Favourite", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        favouriteReference.removeValue()
                        Toast.makeText(this, "Removed From Favourite", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Added To Favourite", Toast.LENGTH_SHORT).show()
                } else {
                    favSongRef.child(songItem.id).removeValue()
                    Toast.makeText(this, "Removed From Favourite", Toast.LENGTH_SHORT).show()
                }
            }
            binding.topSongsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            binding.topSongsRecyclerView.adapter = topSongsAdapter

            topSongsAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    if (musicPlayerService != null) {
                        val intent = Intent(this@ArtistActivity, MusicPlayerService::class.java).apply {
                            action = MusicPlayerService.ACTION_PLAY_NEW
                            putParcelableArrayListExtra("playlist", topSongsList)
                            putExtra("index", position)
                        }

                        ContextCompat.startForegroundService(this@ArtistActivity, intent)
                    }
                    Home.RecentlyPlayedManager.addToRecentlyPlayed(this@ArtistActivity,topSongsList[position])
                }
            })

            favSongRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val favoriteIds = snapshot.children.mapNotNull { it.key }

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
                this,
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
                this,
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
    fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format(Locale.US,"%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format(Locale.US,"%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US,"%.1fK", count / 1_000.0)
            else -> count.toString()
        }.replace(".0", "") // remove trailing .0
    }
    private fun updateMiniPlayer(songItem: SongItem?) {
        songName.text = Html.fromHtml(songItem?.name ?: "", Html.FROM_HTML_MODE_LEGACY)
        artistName.text = songItem?.artist
        Picasso.get().load(songItem?.image[1]?.url).into(songImage)
        setDynamicBackground(songItem?.image[1]?.url ?: "",songImage,background)
    }
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (isPlaying) {
            playPauseButton.setImageResource(R.drawable.pausebutton)
        } else {
            playPauseButton.setImageResource(R.drawable.playbutton)
        }
    }
    fun setDynamicBackground(imageUrl: String, imageView: AppCompatImageView, backgroundView: AppCompatImageView) {
        Glide.with(imageView.context)
            .asBitmap()
            .load(imageUrl)
            .override(500, 500)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {

                    Palette.from(resource).generate { palette ->
                        val darkVibrant = palette?.getDarkVibrantColor(Color.DKGRAY) ?: Color.DKGRAY
                        val vibrant = palette?.getVibrantColor(Color.BLACK) ?: Color.BLACK

                        // 🌈 Base gradient (vibrant glass)
                        val baseGradient = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                ColorUtils.setAlphaComponent(darkVibrant, 180),
                                ColorUtils.setAlphaComponent(vibrant, 180)
                            )
                        ).apply {
                            gradientType = GradientDrawable.LINEAR_GRADIENT
                            cornerRadius = 0f
                        }

                        // 💎 Frosted glass overlay (soft white tint)
                        val glassOverlay = GradientDrawable().apply {
                            colors = intArrayOf(
                                ColorUtils.setAlphaComponent(Color.WHITE, 90),
                                ColorUtils.setAlphaComponent(Color.WHITE, 20)
                            )
                            gradientType = GradientDrawable.LINEAR_GRADIENT
                            orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        }

                        // 🌟 Glow effect (outer light aura)
                        val glowOverlay = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            gradientRadius = 700f
                            colors = intArrayOf(
                                ColorUtils.setAlphaComponent(vibrant, 100),
                                Color.TRANSPARENT
                            )
                            setGradientCenter(0.5f, 0.3f) // position glow (center/top)
                        }

                        // 🧊 Combine layers
                        val layerDrawable = LayerDrawable(arrayOf(glowOverlay, baseGradient, glassOverlay))
                        layerDrawable.setLayerInset(0, -50, -50, -50, -50) // glow extends beyond bounds

                        backgroundView.background = layerDrawable
                        backgroundView.background.alpha = 230 // control overall transparency (0–255)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Optional cleanup
                }
            })
    }

    fun enableEdgeToEdgeWithInsets(rootView: View) {
        val activity = rootView.context as ComponentActivity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            rootView.setPadding(
                rootView.paddingLeft,
                systemBars.top,
                rootView.paddingRight,
                systemBars.bottom
            )

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
}