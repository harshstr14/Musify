package com.example.musify

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.example.musify.Home.RecentlyPlayedManager
import com.example.musify.databinding.ActivityMyPlaylistBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Download
import com.example.musify.songData.Image
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
import kotlin.math.abs

class MyPlaylistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyPlaylistBinding
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
    private val songList = ArrayList<SongItem>()
    private lateinit var songAdapter: SuggestionSongAdapter

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.LocalBinder
            musicPlayerService = binder.getService()
            bound = true

            // Observe LiveData directly with Activity as LifecycleOwner
            musicPlayerService?.currentSongLive?.observe(this@MyPlaylistActivity) { songItem ->
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
            musicPlayerService?.isPlayingLive?.observe(this@MyPlaylistActivity) { playing ->
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
        binding = ActivityMyPlaylistBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

        setStatusBarIconsTheme(this)

        binding.progressBar.fadeIn()
        binding.scrollView.fadeOut()

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

        val userID = auth.currentUser?.uid

        val name = intent.getStringExtra("name").toString()
        binding.playlistNameText.text = name

        val favSongRef = database.child(userID.toString()).child("Favourites").child("Songs")

        songAdapter = SuggestionSongAdapter(songList) { songItem ->
            if (songItem.isFav) {
                favSongRef.child(songItem.id).setValue(mapOf(
                    "id" to songItem.id,
                    "songName" to songItem.name,
                    "isFavourite" to true
                ))
                Toast.makeText(this, "Added To Favourite", Toast.LENGTH_SHORT).show()
            } else {
                favSongRef.child(songItem.id).removeValue()
                Toast.makeText(this, "Removed From Favourite", Toast.LENGTH_SHORT).show()
            }
        }

        binding.songRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.songRecyclerView.adapter = songAdapter
        binding.songRecyclerView.isNestedScrollingEnabled = false

        if (name != "Favourites") {
            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT) {
                override fun onMove(recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder,
                                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val songID = songList[position].id

                        val playListRef = database.child(userID.toString())
                            .child("Favourites")
                            .child("MyPlaylist")
                            .child(name)

                        playListRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val songsNode = currentData.child("Songs")
                                val songs = (songsNode.getValue(object : GenericTypeIndicator<Map<String, Any>>() {}) ?: emptyMap())
                                    .toMutableMap()

                                songs.remove(songID)

                                songsNode.value = songs

                                val totalSongsNode = currentData.child("total Songs")
                                val totalSongs = (totalSongsNode.getValue(Long::class.java) ?: 0L)
                                totalSongsNode.value = maxOf(0L, totalSongs - 1)

                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                when {
                                    error != null -> {
                                        Toast.makeText(this@MyPlaylistActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    !committed -> {
                                        Toast.makeText(this@MyPlaylistActivity, "Song not found in $name", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        songList.removeAt(position)
                                        songAdapter.notifyItemRemoved(position)
                                        Toast.makeText(this@MyPlaylistActivity, "Song removed from $name", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        })
                    }
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    val itemView = viewHolder.itemView
                    val background = "#E53935".toColorInt().toDrawable() // red shade
                    val icon = ContextCompat.getDrawable(this@MyPlaylistActivity, R.drawable.delete)

                    val iconMargin = (itemView.height - icon!!.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                    val iconBottom = iconTop + icon.intrinsicHeight

                    if (dX < 0) { // Swipe left
                        val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                        background.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                        )
                    } else {
                        background.setBounds(0, 0, 0, 0)
                    }

                    background.draw(c)
                    icon.draw(c)

                    // Add fade-in effect for icon as user swipes
                    val alpha = 1.0f - (abs(dX) / itemView.width.coerceAtMost(1).toFloat())
                    icon.alpha = ((1 - alpha) * 255).toInt()

                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            })

            itemTouchHelper.attachToRecyclerView(binding.songRecyclerView)
        }

        songAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val intent = Intent(this@MyPlaylistActivity, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                    putExtra("index", position)
                }

                ContextCompat.startForegroundService(this@MyPlaylistActivity, intent)
                RecentlyPlayedManager.addToRecentlyPlayed(this@MyPlaylistActivity,songList[position])
            }
        })

        if (userID != null) {
            if (name == "Favourites") {
                binding.imageView.setImageResource(R.drawable.liked)
                val songsReference = database.child(userID).child("Favourites").child("Songs")
                songsReference.get().addOnSuccessListener { songsSnapshot ->
                    if (songsSnapshot.exists()) {
                        val songIDList = ArrayList<String>()
                        for (songSnap in songsSnapshot.children) {
                            val songID  = songSnap.child("id").getValue(String::class.java)
                            if (songID != null) songIDList.add(songID)
                        }

                        songList.clear()
                        if (songIDList.isEmpty()) onDataLoaded() else fetchSongsByIDs(songIDList)
                    } else {
                        onDataLoaded()
                    }
                }
            } else {
                binding.imageView.setImageResource(R.drawable.myplaylist)
                val playListRef = database.child(userID).child("Favourites").child("MyPlaylist")
                playListRef.child(name).child("Songs").get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val songIDList = ArrayList<String>()
                        for (songSnap in snapshot.children) {
                            val songID = songSnap.child("id").getValue(String::class.java)
                            if (songID != null) songIDList.add(songID)
                        }

                        songList.clear()
                        if (songIDList.isEmpty()) onDataLoaded() else fetchSongsByIDs(songIDList)
                    } else {
                        onDataLoaded()
                    }
                }
            }
        }
    }
    fun fetchSongsByIDs(songIDs: List<String>) {
        Thread {
            val client = OkHttpClient()
            val tempList = mutableListOf<SongItem>()

            try {
                for (songID in songIDs) {
                    val request = Request.Builder()
                        .url("https://jiosaavn-api-stableone.vercel.app/api/songs/$songID")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        if (responseBody.isNotEmpty()) {
                            val songItem = parseSongJson(responseBody)
                            if (songItem != null) {
                                tempList.add(songItem)
                            } else {
                                Log.e("SAAVN_PARSE", "Failed to parse song for ID: $songID")
                            }
                        } else {
                            Log.e("SAAVN", "Empty response for ID: $songID")
                        }
                    } else {
                        Log.e("SAAVN", "Error: ${response.code}")
                    }
                }

                Log.d("SAAVN", "Fetched ${tempList.size} songs")

                runOnUiThread {
                    songList.clear()
                    songList.addAll(tempList)
                    songAdapter.notifyDataSetChanged()

                    val totalSongs = songList.size
                    val totalDuration = songList.sumOf { it.duration }
                    "Songs : $totalSongs".also { binding.totalSongText.text = it }
                    binding.durationText.text = formatDuration(totalDuration)

                    val userID = auth.currentUser?.uid
                    if (userID == null) {
                        Log.e("FAV", "User not logged in")
                        return@runOnUiThread
                    }

                    val favSongRef = database.child(userID).child("Favourites").child("Songs")

                    favSongRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val favoriteIds = snapshot.children.mapNotNull { it.key }
                            songList.forEach { song -> song.isFav = favoriteIds.contains(song.id) }
                            songAdapter.notifyDataSetChanged()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("FAV", "Error loading favourites", error.toException())
                        }
                    })
                    onDataLoaded()

                    val anim = AnimationUtils.loadAnimation(this,R.anim.nav_item_click)

                    binding.playButtonIcon.setOnClickListener {
                        binding.playButtonIcon.startAnimation(anim)
                        val intent = Intent(this, MusicPlayerService::class.java).apply {
                            action = MusicPlayerService.ACTION_PLAY_NEW
                            putParcelableArrayListExtra("playlist", songList)
                        }

                        ContextCompat.startForegroundService(this, intent)
                        musicPlayerService?.updateNotification()
                    }

                    binding.shuffleButton.setOnClickListener {
                        Toast.makeText(this,"Playing with Shuffle", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MusicPlayerService::class.java).apply {
                            action = MusicPlayerService.ACTION_PLAY_NEW
                            putParcelableArrayListExtra("playlist", songList)
                        }

                        ContextCompat.startForegroundService(this, intent)

                        binding.shuffleButton.startAnimation(anim)
                        musicPlayerService?.updateNotification()
                        musicPlayerService?.isShuffle?.value = !(musicPlayerService?.isShuffle?.value ?: false)
                    }
                }

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}", e)
            }
        }.start()
    }
    private fun parseSongJson(jsonString: String): SongItem? {
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return null

        val songArray = json.getJSONArray("data")
        val songObject = songArray.getJSONObject(0)

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
        val primaryArtists = artistsObj?.optJSONArray("primary")
        val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
            primaryArtists.getJSONObject(0).optString("name")
        } else ""

        return SongItem(id, name, artistName, image, duration, download)
    }
    private fun onDataLoaded() {
        if (songList.isEmpty()) {
            binding.progressBar.fadeOut()
            binding.scrollView.fadeOut()
            binding.noSongText.fadeIn()
        } else {
            binding.progressBar.fadeOut()
            binding.scrollView.fadeIn()
            binding.noSongText.fadeOut()
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
    private fun setDynamicBackground(imageUrl: String, imageView: AppCompatImageView, backgroundView: AppCompatImageView) {
        Glide.with(imageView.context)
            .asBitmap()
            .load(imageUrl)
            .override(500, 500)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {

                    Palette.from(resource).generate { palette ->
                        val darkVibrant = palette?.getDarkVibrantColor(Color.DKGRAY) ?: Color.DKGRAY
                        val vibrant = palette?.getVibrantColor(Color.BLACK) ?: Color.BLACK

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

                        val glassOverlay = GradientDrawable().apply {
                            colors = intArrayOf(
                                ColorUtils.setAlphaComponent(Color.WHITE, 90),
                                ColorUtils.setAlphaComponent(Color.WHITE, 20)
                            )
                            gradientType = GradientDrawable.LINEAR_GRADIENT
                            orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        }

                        val glowOverlay = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            gradientRadius = 700f
                            colors = intArrayOf(
                                ColorUtils.setAlphaComponent(vibrant, 100),
                                Color.TRANSPARENT
                            )
                            setGradientCenter(0.5f, 0.3f)
                        }

                        val layerDrawable = LayerDrawable(arrayOf(glowOverlay, baseGradient, glassOverlay))
                        layerDrawable.setLayerInset(0, -50, -50, -50, -50)

                        backgroundView.background = layerDrawable
                        backgroundView.background.alpha = 230
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Optional cleanup
                }
            })
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
    private fun enableEdgeToEdgeWithInsets(rootView: View) {
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