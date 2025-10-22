package com.example.musify

import android.app.AlertDialog
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
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.example.musify.databinding.ActivityPlaySongBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Album
import com.example.musify.songData.Artists
import com.example.musify.songData.Download
import com.example.musify.songData.Image
import com.example.musify.songData.Song
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class PlaySong : AppCompatActivity() {
    private lateinit var binding: ActivityPlaySongBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var playButton: AppCompatImageView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var shuffleButton: AppCompatImageView
    private lateinit var repeatButton: AppCompatImageView
    private lateinit var favourite: AppCompatImageView
    private val songList = mutableListOf<Song>()
    private var musicService: MusicPlayerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.LocalBinder
            musicService = binder.getService()
            bound = true

            val duration = musicService?.getDuration() ?: 0
            seekBar.max = duration
            totalTime.text = formatTime(duration)

            val progress = musicService?.getCurrentPosition() ?: 0
            seekBar.progress = progress
            currentTime.text = formatTime(progress)

            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            musicService = null
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MusicPlayerService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
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
        binding = ActivityPlaySongBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference().child("Users")

        binding.downArrow.setOnClickListener {
            finish()
            overridePendingTransition(0, R.anim.slide_out_bottom)
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            overridePendingTransition(0, R.anim.slide_out_bottom)
        }

        playButton = findViewById(R.id.playButton)!!
        nextButton = findViewById(R.id.appCompatImageView7)!!
        prevButton = findViewById(R.id.appCompatImageView3)!!
        seekBar = findViewById(R.id.seekBar)!!
        currentTime = findViewById(R.id.currentTime)!!
        totalTime = findViewById(R.id.totalTime)!!
        shuffleButton = findViewById(R.id.shuffleButton)!!
        repeatButton = findViewById(R.id.repeatButton)!!
        favourite = findViewById(R.id.favouriteIcon)!!

        setupControls()
    }
    fun fetchSongByID(songID: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/songs/${songID}")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Log.d("SAAVN_RAW", responseBody)
                    parseSongsJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun parseSongsJson(jsonString: String) {
        songList.clear()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

        val songsArray = json.getJSONArray("data")
        Log.d("Song", "Songs array length: ${songsArray.length()}")

        val songJson = songsArray.getJSONObject(0)

        val id = songJson.optString("id")
        val name = songJson.optString("name")
        val type = songJson.optString("type")
        val year = songJson.optString("year")
        val releaseDate = songJson.optString("releaseDate")
        val duration = songJson.optInt("duration")
        val playCount = songJson.optInt("playCount")
        val language = songJson.optString("language")

        val albumObject = songJson.optJSONObject("album")
        val album = Album(
            id = albumObject?.optString("id") ?: "",
            name = albumObject?.optString("name") ?: ""
        )

        val artistsObject = songJson.optJSONObject("artists")
        val primaryArray = artistsObject?.optJSONArray("primary")
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

        val imageArray = songJson.optJSONArray("image")
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

        val downloadArray = songJson.optJSONArray("downloadUrl")
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

        val song = Song(
            id = id,
            name = name,
            type = type,
            year = year,
            releaseDate = releaseDate,
            duration = duration,
            playCount = playCount,
            language = language,
            album = album,
            artists = primaryArtists,
            image = image,
            downloadUrl = download
        )

        songList.add(song)

        runOnUiThread {
            val imageUrl = if (song.image.size > 2) song.image[2].url else song.image.firstOrNull()?.url
            if (!imageUrl.isNullOrEmpty()) {
                Picasso.get().load(imageUrl).into(binding.songImageView)
                setDynamicBackground(song.image[1].url, binding.songImageView, binding.scrollView)
            } else {
                binding.songImageView.setImageResource(R.drawable.playlist_image)
            }

            binding.songNameText.text = Html.fromHtml(song.name.ifEmpty { "Unknown Song" }, Html.FROM_HTML_MODE_LEGACY)
            binding.artistNameText.text = song.artists.firstOrNull()?.name ?: "Unknown Artist"
            binding.albumName.text = Html.fromHtml(song.album.name.ifEmpty { "Unknown Album" }, Html.FROM_HTML_MODE_LEGACY)

            if (song.album.id.isNotEmpty()) {
                fetchAlbumByID(song.album.id)
            }

            fetchSuggestionsByID(song.id)

            val artistsAdapter = ArtistsAdapter(song.artists)
            val recyclerView = findViewById<RecyclerView>(R.id.artistRecyclerView)
            recyclerView?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            recyclerView?.adapter = artistsAdapter

            artistsAdapter.setOnItemClickListener(object : ArtistsAdapter.OnItemClickListener {
                override fun omItemClick(position: Int) {
                    val clickedArtist = song.artists.getOrNull(position) ?: return
                    val intent = Intent(this@PlaySong, ArtistActivity::class.java)
                    intent.putExtra("artistID", clickedArtist.id)
                    startActivity(intent)
                }
            })

            binding.plusIcon.setOnClickListener {
                val dialogView = LayoutInflater.from(this).inflate(R.layout.playlist_dialog,null)
                val dialog = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create()

                val userID = auth.currentUser?.uid
                val playListRef = database.child(userID.toString()).child("Favourites").child("MyPlaylist")
                val playlistList = mutableListOf<PlaylistData>()

                val adapter = PlaylistNameAdapter(playlistList,
                    onPlusIconClick = { position ->
                        val playlistName = playlistList[position].name
                        val playlistRef = playListRef.child(playlistName)

                        playlistRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                // Get current playlist map safely
                                val playlist = (currentData.getValue(object : GenericTypeIndicator<Map<String, Any>>() {}) ?: emptyMap()).toMutableMap()

                                // Get existing Songs (may be null initially)
                                val songs = (playlist["Songs"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()

                                // ✅ If song already exists, don't commit changes
                                if (songs.containsKey(song.id)) {
                                    // No need to modify currentData → Transaction will treat it as no change
                                    return Transaction.abort()
                                }

                                // ✅ Add new song data
                                songs[song.id] = mapOf(
                                    "id" to song.id,
                                    "name" to song.name
                                )

                                // ✅ Safely increment totalSongs (handle missing or wrong type)
                                val totalSongs = (playlist["total Songs"] as? Number)?.toLong() ?: 0L
                                playlist["total Songs"] = totalSongs + 1

                                // ✅ Apply updates
                                playlist["Songs"] = songs
                                currentData.value = playlist

                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                when {
                                    error != null -> {
                                        Toast.makeText(this@PlaySong, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    !committed -> {
                                        Toast.makeText(this@PlaySong, "Song already exists in $playlistName", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        Toast.makeText(this@PlaySong, "Added to $playlistName", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        })
                    }
                )
                val recyclerView = dialogView.findViewById<RecyclerView>(R.id.playListNameRecyclerView)
                recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                recyclerView.adapter = adapter

                playListRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        playlistList.clear()
                        for (playListSnapShot in snapshot.children) {
                            val name = playListSnapShot.child("playList Name").getValue(String::class.java) ?: ""
                            val totalSongs = playListSnapShot.child("total Songs").getValue(Int::class.java) ?: 0
                            playlistList.add(PlaylistData(name, totalSongs))
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        if (playlistList.isEmpty()) {
                            val text = dialogView.findViewById<TextView>(R.id.noPlaylistText)
                            text.visibility = View.VISIBLE
                        }
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Failed to load playlists", Toast.LENGTH_SHORT).show()
                    if (playlistList.isEmpty()) {
                        val text = dialogView.findViewById<TextView>(R.id.noPlaylistText)
                        text.visibility = View.VISIBLE
                    }
                }

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
        }
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
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

        val albumObject = json.getJSONObject("data")

        val id = albumObject.optString("id")
        val imageArray = albumObject.optJSONArray("image")
        val imageUrl = imageArray?.getJSONObject(1)?.optString("url")

        runOnUiThread {
            val imageAlbum = findViewById<AppCompatImageView>(R.id.albumImage)
            Picasso.get().load(imageUrl).into(imageAlbum)
            val albumCardView = findViewById<CardView>(R.id.albumCardView)

            albumCardView?.setOnClickListener {
                val intent = Intent(this, AlbumActivity::class.java)
                intent.putExtra("id",id)
                startActivity(intent)
            }
        }
    }
    fun fetchSuggestionsByID(songID: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/songs/${songID}/suggestions?&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parseSuggestionSongJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun parseSuggestionSongJson(jsonString: String) {
        val songList = ArrayList<SongItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

        val songsArray = json.getJSONArray("data")
        Log.d("Song", "Songs array length: ${songsArray.length()}")

        for (i in 0 until songsArray.length()) {
            val song = songsArray.getJSONObject(i)

            val id = song.optString("id")
            val name = song.optString("name")
            val duration = song.optInt("duration")

            val downloadArray = song.optJSONArray("downloadUrl")
            val downloadUrl = if (downloadArray != null && downloadArray.length() > 0) {
                downloadArray.getJSONObject(2).optString("url")
            } else ""

            val imageArray = song.optJSONArray("image")
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

            val artistsObj = song.optJSONObject("artists")
            val primaryArtists = artistsObj?.optJSONArray("primary")
            val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                primaryArtists.getJSONObject(0).optString("name")
            } else ""

            songList.add(SongItem(id, name, artistName, image,duration,downloadUrl))
        }

        runOnUiThread {
            val recyclerView = findViewById<RecyclerView>(R.id.songRecyclerView)

            val userID = auth.currentUser?.uid
            val favSongRef = database.child(userID!!).child("Favourites").child("Songs")

            val suggestionSongAdapter = SuggestionSongAdapter(songList) { songItem ->
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
            recyclerView?.layoutManager = GridLayoutManager(this,3, GridLayoutManager.HORIZONTAL,false)
            recyclerView?.adapter = suggestionSongAdapter

            suggestionSongAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    if (musicService != null) {
                        val intent = Intent(this@PlaySong, MusicPlayerService::class.java).apply {
                            action = MusicPlayerService.ACTION_PLAY_NEW
                            putParcelableArrayListExtra("playlist", songList)
                            putExtra("index", position)
                        }

                        ContextCompat.startForegroundService(this@PlaySong, intent)
                    }
                    Home.RecentlyPlayedManager.addToRecentlyPlayed(this@PlaySong,songList[position])
                }
            })

            favSongRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val favoriteIds = snapshot.children.mapNotNull { it.key }

                        songList.forEach { song ->
                            song.isFav = favoriteIds.contains(song.id)
                        }
                        suggestionSongAdapter.notifyDataSetChanged()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FAV", "Error loading favourites", error.toException())
                }
            })
        }
    }
    private fun observeService() {
        musicService?.currentSongLive?.observe(this) { song ->
            if (song != null) {
                val songID = song.id
                fetchSongByID(songID)
                updateFavouriteUI(song)
            }
        }

        musicService?.isPlayingLive?.observe(this) { playing ->
            playButton.setImageResource(if (playing) R.drawable.pausebutton else R.drawable.playbutton)
        }

        musicService?.progressLive?.observe(this) { progress ->
            seekBar.progress = progress
            currentTime.text = formatTime(progress)
        }

        musicService?.durationLive?.observe(this) { duration ->
            seekBar.max = duration
            totalTime.text = formatTime(duration)
        }

        musicService?.isShuffle?.observe(this) { shuffle ->
            if (shuffle) shuffleButton.setColorFilter("#34A853".toColorInt()) else shuffleButton.clearColorFilter()
        }

        musicService?.repeatMode?.observe(this) { repeat ->
            if (repeat) repeatButton.setColorFilter("#34A853".toColorInt()) else repeatButton.clearColorFilter()
        }
    }
    private fun formatTime(millis: Int): String {
        val minutes = millis / 1000 / 60
        val seconds = (millis / 1000) % 60
        return String.format(Locale.US,"%d : %02d", minutes, seconds)
    }
    private fun setupControls() {
        val anim = AnimationUtils.loadAnimation(this,R.anim.nav_item_click)

        playButton.setOnClickListener {
            playButton.startAnimation(anim)
            if (musicService?.isPlayingLive?.value == true) {
                musicService?.pause()
            } else {
                musicService?.resume()
            }
        }

        nextButton.setOnClickListener {
            nextButton.startAnimation(anim)
            musicService?.next()
        }

        prevButton.setOnClickListener {
            prevButton.startAnimation(anim)
            musicService?.previous()
        }

        shuffleButton.setOnClickListener {
            shuffleButton.startAnimation(anim)
            musicService?.updateNotification()
            musicService?.isShuffle?.value = !(musicService?.isShuffle?.value ?: false)
        }

        repeatButton.setOnClickListener {
            repeatButton.startAnimation(anim)
            musicService?.updateNotification()
            musicService?.repeatMode?.value = !(musicService?.repeatMode?.value ?: false)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress.toLong())
                    currentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    fun setDynamicBackground(imageUrl: String, imageView: AppCompatImageView, backgroundView: View) {
        if (!isFinishing && !isDestroyed && imageView.isAttachedToWindow) {
            Glide.with(applicationContext)
                .asBitmap()
                .load(imageUrl)
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

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }
    private fun updateFavouriteUI(song: SongItem) {
        val userID = auth.currentUser?.uid ?: return
        val favouriteReference = database.child(userID).child("Favourites").child("Songs").child(song.id)

        favouriteReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isFavourite = snapshot.child("isFavourite").getValue(Boolean::class.java) ?: false
                favourite.isSelected = isFavourite
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        favourite.setOnClickListener {
            val anim = AnimationUtils.loadAnimation(this, R.anim.nav_item_click)
            favourite.startAnimation(anim)

            favouriteReference.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val songData = mapOf(
                        "id" to song.id,
                        "songName" to song.name,
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
    }
    fun enableEdgeToEdgeWithInsets(rootView: View) {
        val activity = rootView.context as ComponentActivity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            rootView.setPadding(
                rootView.paddingLeft,
                rootView.paddingTop,
                rootView.paddingRight,
                systemBars.bottom
            )

            insets
        }
    }
}