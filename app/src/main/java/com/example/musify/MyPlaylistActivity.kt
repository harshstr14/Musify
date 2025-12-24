package com.example.musify

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.Home.RecentlyPlayedManager
import com.example.musify.databinding.ActivityMyPlaylistBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Artists
import com.example.musify.songData.Download
import com.example.musify.songData.Image
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

class MyPlaylistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyPlaylistBinding
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
    private val songList = ArrayList<SongItem>()
    private lateinit var songAdapter: SuggestionSongAdapter
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
        binding.scrollView.fadeOut()

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

                private val deleteIcon = ContextCompat.getDrawable(this@MyPlaylistActivity,R.drawable.delete)!!
                private val BackgroundColor = Paint().apply {
                    color = "#E53935".toColorInt()
                    isAntiAlias = true
                }

                override fun onMove(recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder,
                                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {

                        val playListRef = database.child(userID.toString())
                            .child("Favourites")
                            .child("MyPlaylist")
                            .child(name)

                        playListRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val songsNode = currentData.child("Songs")
                                val songsList = when (val value = songsNode.value) {
                                    is List<*> -> value.toMutableList()
                                    null -> mutableListOf()
                                    else -> mutableListOf()
                                }

                                songsList.removeAt(position)

                                songsNode.value = songsList

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
                                        val totalSongs = songList.size
                                        "Songs : $totalSongs".also { binding.totalSongText.text = it }
                                        binding.songRecyclerView.itemAnimator?.apply {
                                            removeDuration = 250
                                        }
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
                    val itemHeight = itemView.height

                    val backgroundLeft: Float
                    val backgroundRight: Float

                    if (dX < 0) {
                        backgroundLeft = itemView.right + dX
                        backgroundRight = itemView.right.toFloat()

                        val rect = RectF(backgroundLeft, itemView.top.toFloat(), backgroundRight, itemView.bottom.toFloat())
                        c.drawRoundRect(rect, 100f, 100f, BackgroundColor)

                        val backgroundWidth = backgroundRight - backgroundLeft
                        val iconWidth = deleteIcon.intrinsicWidth
                        val iconHeight = deleteIcon.intrinsicHeight

                        // Icon stays centered inside the pink background
                        val iconLeft = backgroundRight - backgroundWidth / 2 - iconWidth / 2
                        val iconRight = iconLeft + iconWidth
                        val iconTop = itemView.top + (itemHeight - iconHeight) / 2
                        val iconBottom = iconTop + iconHeight

                        val progress = min(abs(dX) / itemView.width, 1f) // clamp 0–1
                        val alpha = progress // opacity increases as swipe increases
                        deleteIcon.alpha = (alpha * 255).toInt()

                        deleteIcon.setBounds(iconLeft.toInt(), iconTop, iconRight.toInt(), iconBottom)
                        deleteIcon.draw(c)
                    }

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
                        songList.clear()

                        val songIDList = songsSnapshot.children
                            .mapNotNull { it.child("id").getValue(String::class.java) }

                        if (songIDList.isEmpty()) {
                            onDataLoaded()
                        } else {
                            fetchSongsByIDs(songIDList)
                        }
                    } else {
                        onDataLoaded()
                    }
                }.addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to load favourite songs", e)
                    onDataLoaded()
                }
            } else {
                val playListRef = database.child(userID).child("Favourites")
                    .child("MyPlaylist").child(name)

                playListRef.get().addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        onDataLoaded()
                        return@addOnSuccessListener
                    }

                    val imageUrl = snapshot.child("imageUrl").getValue(String::class.java) ?: ""
                    if (imageUrl != "") {
                        Picasso.get().load(imageUrl).into(binding.imageView)
                    } else {
                        binding.imageView.setImageResource(R.drawable.myplaylist)
                    }

                    val songsSnapshot = snapshot.child("Songs")
                    val songIDList = songsSnapshot.children
                        .mapNotNull { it.child("id").getValue(String::class.java) }

                    songList.clear()
                    if (songIDList.isEmpty()) onDataLoaded() else fetchSongsByIDs(songIDList)

                }.addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to load playlist songs", e)
                    onDataLoaded()
                }
            }
        }
    }
    private fun fetchSongsByIDs(songIDs: List<String>) {
        lifecycleScope.launch {
            val initialLoadCount = 20.coerceAtMost(songIDs.size)
            val firstBatchIDs = songIDs.take(initialLoadCount)
            val remainingIDs = songIDs.drop(initialLoadCount)

            // Load first 10 songs
            val firstBatch = withContext(Dispatchers.IO) { fetchSongs(firstBatchIDs) }

            songList.clear()
            songList.addAll(firstBatch)
            songAdapter.notifyItemRangeInserted(0, firstBatch.size)
            updateSongStats()
            onDataLoaded()

            // Load remaining songs in the background
            if (remainingIDs.isNotEmpty()) {
                val remainingBatch = withContext(Dispatchers.IO) { fetchSongs(remainingIDs) }
                val startIndex = songList.size
                songList.addAll(remainingBatch)
                songAdapter.notifyItemRangeInserted(startIndex, remainingBatch.size)
                updateSongStats()
            }

            val userID = auth.currentUser?.uid
            if (userID == null) {
                Log.e("FAV", "User not logged in")
                return@launch
            }

            val favSongRef = database.child(userID).child("Favourites").child("Songs")

            favSongRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val favoriteIds = snapshot.children.mapNotNull { it.key }.toSet()
                    songList.forEach { song -> song.isFav = favoriteIds.contains(song.id) }
                    songAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FAV", "Error loading favourites", error.toException())
                }
            })

            val anim = AnimationUtils.loadAnimation(this@MyPlaylistActivity,R.anim.nav_item_click)

            binding.playButtonIcon.setOnClickListener {
                binding.playButtonIcon.startAnimation(anim)
                val intent = Intent(this@MyPlaylistActivity, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                }

                ContextCompat.startForegroundService(this@MyPlaylistActivity, intent)
                musicPlayerService?.updateNotification()
            }

            binding.shuffleButton.setOnClickListener {
                Toast.makeText(this@MyPlaylistActivity,"Playing with Shuffle", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@MyPlaylistActivity, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                }

                ContextCompat.startForegroundService(this@MyPlaylistActivity, intent)

                binding.shuffleButton.startAnimation(anim)
                musicPlayerService?.updateNotification()
                musicPlayerService?.isShuffle?.value = !(musicPlayerService?.isShuffle?.value ?: false)
            }
        }
    }
    private fun updateSongStats() {
        val totalSongs = songList.size
        val totalDuration = songList.sumOf { it.duration }
        binding.totalSongText.text = "Songs : $totalSongs"
        binding.durationText.text = formatDuration(totalDuration)
    }
    private fun fetchSongs(ids: List<String>): List<SongItem> {
        val list = mutableListOf<SongItem>()
        try {
            for (songID in ids) {
                val request = Request.Builder()
                    .url("$apiUrl/songs/$songID")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    val songItem = parseSongJson(responseBody)
                    if (songItem != null) list.add(songItem)
                }
            }
        } catch (e: Exception) {
            Log.e("SAAVN", "Exception fetching songs", e)
        }
        return list
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

        return SongItem(id, name, primaryArtists, image, duration, download)
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