package com.example.musify

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.util.Log
import android.view.View
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
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.example.musify.databinding.ActivityAlbumBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Artists
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

class AlbumActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlbumBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var musicPlayerService: MusicPlayerService ?= null
    private var bound = false
    private lateinit var miniPlayer: View
    private lateinit var songName: TextView
    private lateinit var artistName: TextView
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
            musicPlayerService?.currentSongLive?.observe(this@AlbumActivity) { songItem ->
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

            musicPlayerService?.isPlayingLive?.observe(this@AlbumActivity) { playing ->
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
        binding = ActivityAlbumBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

        binding.progressBar.fadeIn()
        binding.scrollView.fadeOut()

        val albumID = intent.getStringExtra("id")

        fetchAlbumByID(albumID!!)

        binding.backArrowIconImage.setOnClickListener {
            finish()
        }

        backgroundView = findViewById(R.id.backgroundView)
        miniPlayer = findViewById(R.id.miniPlayer)
        songName = findViewById(R.id.songNameText)
        artistName = findViewById(R.id.artistNameText)
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
    fun fetchAlbumByID(albumID: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/albums?id=${albumID}&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parseAlbumJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun parseAlbumJson(jsonString: String) {
        val songList = ArrayList<SongItem>()
        var totalDuration = 0

        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

        val albumObject = json.getJSONObject("data")

        val id = albumObject.optString("id")
        val name = albumObject.optString("name")
        val description = albumObject.optString("description")
        val songCount = albumObject.optString("songCount")

        val artistObject = albumObject.optJSONObject("artists")
        val primaryArray = artistObject?.optJSONArray("primary")
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

        val imageArray = albumObject.optJSONArray("image")
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

        val songArray = albumObject.optJSONArray("songs")
        if (songArray != null) {
            for (j in 0 until songArray.length()) {
                val songObject = songArray.getJSONObject(j)

                val id = songObject.optString("id")
                val name = songObject.optString("name")
                val duration = songObject.optInt("duration")
                totalDuration = totalDuration + duration

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

                songList.add(SongItem(id, name, artistName, image, duration, downloadUrl))
            }
        }

        runOnUiThread {
            Picasso.get().load(image[2].url).into(binding.imageView)
            binding.albumNameText.text = Html.fromHtml(name,Html.FROM_HTML_MODE_LEGACY)
            binding.descriptionText.text = Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY)
            "Song  :  $songCount".also { binding.totalSongText.text = it }
            Log.d("totalDuration","$totalDuration")
            val duration = formatDuration(totalDuration)
            binding.durationText.text = duration

            val userID = auth.currentUser?.uid
            val favouriteReference = database.child(userID!!).child("Favourites").child("Albums").child(id)

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
                            "albumName" to name,
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

            val favSongRef = database.child(userID).child("Favourites").child("Songs")

            val songAdapter = SuggestionSongAdapter(songList){ songItem ->
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
            binding.songRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL,false)
            binding.songRecyclerView.adapter = songAdapter
            binding.songRecyclerView.isNestedScrollingEnabled = false

            songAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    if (musicPlayerService != null) {
                        val intent = Intent(this@AlbumActivity, MusicPlayerService::class.java).apply {
                            action = MusicPlayerService.ACTION_PLAY_NEW
                            putParcelableArrayListExtra("playlist", songList)
                            putExtra("index", position)
                        }

                        ContextCompat.startForegroundService(this@AlbumActivity, intent)
                    }
                    Home.RecentlyPlayedManager.addToRecentlyPlayed(this@AlbumActivity,songList[position])
                }
            })

            favSongRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val favoriteIds = snapshot.children.mapNotNull { it.key }

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
            binding.artistRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,false)
            binding.artistRecyclerView.adapter = artistsAdapter

            artistsAdapter.setOnItemClickListener(object : ArtistsAdapter.OnItemClickListener {
                override fun omItemClick(position: Int) {
                    val intent = Intent(this@AlbumActivity, ArtistActivity::class.java)
                    intent.putExtra("artistID",primaryArtists[position].id)
                    startActivity(intent)
                }
            })

            binding.progressBar.fadeOut()
            binding.scrollView.fadeIn()

            val anim = AnimationUtils.loadAnimation(this,R.anim.nav_item_click)

            binding.playButtonIcon.setOnClickListener {
                binding.playButtonIcon.startAnimation(anim)
                if (musicPlayerService != null) {
                    val intent = Intent(this, MusicPlayerService::class.java).apply {
                        action = MusicPlayerService.ACTION_PLAY_NEW
                        putParcelableArrayListExtra("playlist", songList)
                    }

                    ContextCompat.startForegroundService(this, intent)
                }
            }

            binding.shuffleButton.setOnClickListener {
                Toast.makeText(this,"Playing with Shuffle", Toast.LENGTH_SHORT).show()
                if (musicPlayerService != null) {
                    val intent = Intent(this, MusicPlayerService::class.java).apply {
                        action = MusicPlayerService.ACTION_PLAY_NEW
                        putParcelableArrayListExtra("playlist", songList)
                    }

                    ContextCompat.startForegroundService(this, intent)
                }
                binding.shuffleButton.startAnimation(anim)
                musicPlayerService?.updateNotification()
                musicPlayerService?.isShuffle?.value = !(musicPlayerService?.isShuffle?.value ?: false)
            }
        }
    }
    fun formatDuration(totalSeconds: Int): String {
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
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    Palette.from(resource).generate { palette ->
                        if (palette == null) return@generate

                        val vibrant = palette.getVibrantColor(Color.BLACK)
                        val darkVibrant = palette.getDarkVibrantColor(Color.DKGRAY)

                        val gradientDrawable = GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(
                                vibrant.adjustAlpha(0.9f),
                                darkVibrant.adjustAlpha(0.9f)
                            )
                        )
                        gradientDrawable.cornerRadius = 0f
                        gradientDrawable.gradientType = GradientDrawable.LINEAR_GRADIENT

                        val glassColor = "#66FFFFFF".toColorInt() // semi-transparent white overlay
                        val glassLayer = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            colors = intArrayOf(glassColor, Color.TRANSPARENT)
                            gradientType = GradientDrawable.LINEAR_GRADIENT
                        }

                        val layerDrawable = LayerDrawable(arrayOf(gradientDrawable, glassLayer))
                        backgroundView.background = layerDrawable
                        backgroundView.background.alpha = 200 // adjust transparency (0–255)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Optional cleanup
                }
            })
    }
    fun Int.adjustAlpha(factor: Float): Int {
        val alpha = (Color.alpha(this) * factor).toInt()
        val red = Color.red(this)
        val green = Color.green(this)
        val blue = Color.blue(this)
        return Color.argb(alpha, red, green, blue)
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