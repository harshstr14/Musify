package com.example.musify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.databinding.FragmentFavouriteBinding
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Favourite : Fragment() {
    private lateinit var binding: FragmentFavouriteBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var musicPlayerService: MusicPlayerService ?= null
    private var bound = false
    private lateinit var miniPlayer: View
    private lateinit var songName: TextView
    private lateinit var artistName: TextView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var lottieAnimationView: LottieAnimationView
    private val songList = ArrayList<SongItem>()
    private val artistsList = ArrayList<Artists>()
    private val albumsList = ArrayList<DataItem>()
    private val playlistsList = ArrayList<DataItem>()
    private lateinit var songAdapter: SuggestionSongAdapter
    private lateinit var artistsAdapter: ArtistsAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var playListAdapter: PlayListAdapter
    private var songsLoaded = false
    private var artistsLoaded = false
    private var albumsLoaded = false
    private var playlistsLoaded = false
    private var category = "songs"
    private lateinit var apiUrl1: String
    private lateinit var apiUrl2: String
    private lateinit var apiUrl3: String
    private var isRefreshing = false
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

            musicPlayerService?.currentSongLive?.observe(viewLifecycleOwner) { songItem ->
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
                    val paddingInDp = 12
                    val paddingStartInDpArtists = 35
                    val paddingStartInDpAlbums = 25
                    val paddingStartInDpPlaylist = 25
                    val scale = resources.displayMetrics.density
                    val paddingStartInPxPlaylists = (paddingStartInDpPlaylist * scale).toInt()
                    val paddingStartInPxAlbums = (paddingStartInDpAlbums * scale).toInt()
                    val paddingStartInPxArtists = (paddingStartInDpArtists * scale).toInt()
                    val paddingInPx = (paddingInDp * scale).toInt()
                    binding.likedSongRecyclerView.setPadding(0,0,0,paddingInPx)
                    binding.artistsRecyclerView.setPadding(paddingStartInPxArtists,0,0,paddingInPx)
                    binding.playListRecyclerView.setPadding(paddingStartInPxPlaylists,0,0,paddingInPx)
                    binding.albumsRecyclerView.setPadding(paddingStartInPxAlbums,0,0,paddingInPx)
                } else {
                    if (miniPlayer.visibility != View.VISIBLE) {
                        // Prepare for animation
                        miniPlayer.translationY = miniPlayer.height.toFloat()
                        miniPlayer.alpha = 0f
                        miniPlayer.visibility = View.VISIBLE

                        val paddingInDp = 90
                        val paddingStartInDpArtists = 35
                        val paddingStartInDpAlbums = 25
                        val paddingStartInDpPlaylist = 25
                        val scale = resources.displayMetrics.density
                        val paddingStartInPxPlaylists = (paddingStartInDpPlaylist * scale).toInt()
                        val paddingStartInPxAlbums = (paddingStartInDpAlbums * scale).toInt()
                        val paddingStartInPxArtists = (paddingStartInDpArtists * scale).toInt()
                        val paddingInPx = (paddingInDp * scale).toInt()
                        binding.likedSongRecyclerView.setPadding(0,0,0,paddingInPx)
                        binding.artistsRecyclerView.setPadding(paddingStartInPxArtists,0,0,paddingInPx)
                        binding.playListRecyclerView.setPadding(paddingStartInPxPlaylists,0,0,paddingInPx)
                        binding.albumsRecyclerView.setPadding(paddingStartInPxAlbums,0,0,paddingInPx)

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
            musicPlayerService?.isPlayingLive?.observe(viewLifecycleOwner) { playing ->
                updatePlayPauseIcon(playing)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            musicPlayerService = null
        }
    }

    private suspend fun requestWithFallback(endpoint: String): String =
        withContext(Dispatchers.IO) {

            val apis = listOf(apiUrl1, apiUrl2, apiUrl3)

            for (baseUrl in apis) {
                try {
                    val request = Request.Builder()
                        .url("$baseUrl$endpoint")
                        .get()
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->

                        if (response.isSuccessful) {
                            return@withContext response.body?.string().orEmpty()
                        }

                        if (response.code in 500..599) {
                            Log.w("API", "Server error ${response.code} on $baseUrl, trying next...")
                            continue
                        }

                        if (response.code in 400..499) {
                            throw Exception("Client error ${response.code}")
                        }
                    }

                } catch (e: Exception) {
                    if (
                        e is java.net.SocketTimeoutException ||
                        e is java.net.ConnectException ||
                        e is java.net.UnknownHostException
                    ) {
                        Log.w("API", "Network error on $baseUrl, trying next...")
                        continue
                    } else {
                        throw e
                    }
                }
            }

            throw Exception("All APIs timed out")
        }

    override fun onStart() {
        super.onStart()
        if (!bound) {
            val serviceIntent = Intent(requireContext(), MusicPlayerService::class.java)
            ContextCompat.startForegroundService(requireContext(), serviceIntent)
            requireContext().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            requireContext().unbindService(connection)
            bound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFavouriteBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiUrl1 = BuildConfig.API_BASE_URL1
        apiUrl2 = BuildConfig.API_BASE_URL2
        apiUrl3 = BuildConfig.API_BASE_URL3

        miniPlayer = view.findViewById(R.id.miniPlayer)
        songName = view.findViewById(R.id.songNameText)
        artistName = view.findViewById(R.id.artistNameText)
        lottieAnimationView = view.findViewById(R.id.lottieAnimationView)
        playPauseButton = view.findViewById(R.id.playButton)
        nextButton = view.findViewById(R.id.appCompatImageView7)
        prevButton = view.findViewById(R.id.appCompatImageView3)

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
            val intent = Intent(requireContext(), PlaySong::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.slide_in_bottom,
                0
            )
            startActivity(intent, options.toBundle())
        }

        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.green
        )
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(
            R.color.dialog_background
        )

        binding.progressBar.fadeIn()
        binding.likedSongRecyclerView.fadeOut()
        binding.noFavText.fadeOut()

        val categoryList = listOf(
            "songs","artists","albums","playlists"
        )

        category = categoryList[0]

        val categoryAdapter = SearchCategoryAdapter(categoryList) { position ->
            category = categoryList[position]
            updateCategoryVisibility()
        }

        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext(),
            LinearLayoutManager.HORIZONTAL,false)
        binding.categoryRecyclerView.adapter = categoryAdapter

        auth = FirebaseAuth.getInstance()
        val userID = auth.currentUser?.uid

        database = FirebaseDatabase.getInstance().getReference().child("Users").child(userID!!)

        val favRef = database.child("Favourites").child("Songs")

        songAdapter = SuggestionSongAdapter(songList) { song ->
            val index = songList.indexOfFirst { it.id == song.id }
            if (index == -1) return@SuggestionSongAdapter

            if (song.isFav) {
                val songData = mapOf(
                    "id" to song.id,
                    "songName" to song.name,
                    "isFavourite" to true
                )

                favRef.child(song.id).setValue(songData)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Added to Favourites", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to add", Toast.LENGTH_SHORT).show()
                    }

            } else {
                favRef.child(song.id).removeValue()
                    .addOnSuccessListener {
                        // Run safely on UI thread
                        requireActivity().runOnUiThread {
                            binding.likedSongRecyclerView.post {
                                val safeIndex = songList.indexOfFirst { it.id == song.id }
                                if (safeIndex != -1 && safeIndex < songList.size) {
                                    songList.removeAt(safeIndex)
                                    songAdapter.notifyItemRemoved(safeIndex)
                                }
                            }
                            Toast.makeText(requireContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show()

                        }
                    }
                    .addOnFailureListener {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Failed to remove", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        binding.likedSongRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL,false)
        binding.likedSongRecyclerView.adapter = songAdapter

        songAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val intent = Intent(requireContext(), MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                    putExtra("index", position)
                }

                ContextCompat.startForegroundService(requireContext(), intent)
                Home.RecentlyPlayedManager.addToRecentlyPlayed(requireContext(),songList[position])
            }
        })

        artistsAdapter = ArtistsAdapter(artistsList)
        binding.artistsRecyclerView.layoutManager = GridLayoutManager(requireContext(),3, GridLayoutManager.VERTICAL,false)
        binding.artistsRecyclerView.adapter = artistsAdapter

        artistsAdapter.setOnItemClickListener(object : ArtistsAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), ArtistActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("artistID",artistsList[position].id)
                startActivity(intent)
            }
        })

        albumAdapter = AlbumAdapter(albumsList)
        binding.albumsRecyclerView.layoutManager = GridLayoutManager(requireContext(),3,
            GridLayoutManager.VERTICAL,false)
        binding.albumsRecyclerView.adapter = albumAdapter

        albumAdapter.setOnItemClickListener(object : AlbumAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), AlbumActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("id",albumsList[position].id)
                startActivity(intent)
            }
        })

        playListAdapter = PlayListAdapter(playlistsList)
        binding.playListRecyclerView.layoutManager = GridLayoutManager(requireContext(),3,
            GridLayoutManager.VERTICAL,false)
        binding.playListRecyclerView.adapter = playListAdapter

        playListAdapter.setOnItemClickListener(object : PlayListAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), PlaylistActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("id",playlistsList[position].id)
                startActivity(intent)
            }
        })

        //loadFavouriteData()
        loadData(userID)

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = true
            isRefreshing = true
            binding.progressBar.fadeOut()
            loadData(userID)
        }
    }
    private fun loadData(userID: String) {
        songsLoaded = false
        artistsLoaded = false
        albumsLoaded = false
        playlistsLoaded = false

        if (isRefreshing) {
            binding.progressBar.fadeOut()
        } else {
            binding.progressBar.fadeIn()
        }

        // clear old data
        songList.clear()
        artistsList.clear()
        albumsList.clear()
        playlistsList.clear()

        songAdapter.notifyDataSetChanged()
        artistsAdapter.notifyDataSetChanged()
        albumAdapter.notifyDataSetChanged()
        playListAdapter.notifyDataSetChanged()

        loadFavouriteSongs(userID)
        loadFavouriteArtists(userID)
        loadFavouriteAlbums(userID)
        loadFavouritePlaylists(userID)
    }
    private fun fetchSongsByIDs(songIDs: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Split the list into initial batch and remaining
            val initialLoadCount = 15.coerceAtMost(songIDs.size)
            val firstBatchIDs = songIDs.take(initialLoadCount)
            val remainingIDs = songIDs.drop(initialLoadCount)

            try {
                // Load first batch of songs
                val firstBatch = withContext(Dispatchers.IO) { fetchSongs(firstBatchIDs) }

                songList.clear()
                songList.addAll(firstBatch)
                songAdapter.notifyItemRangeInserted(0, firstBatch.size)
                onDataLoaded("songs")

                // Load remaining songs in the background
                if (remainingIDs.isNotEmpty()) {
                    val remainingBatch = withContext(Dispatchers.IO) { fetchSongs(remainingIDs) }
                    val startIndex = songList.size
                    songList.addAll(remainingBatch)
                    songAdapter.notifyItemRangeInserted(startIndex, remainingBatch.size)
                }

                // Load favourites from Firebase
                val userID = auth.currentUser?.uid
                if (userID != null) {
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
                } else {
                    Log.e("FAV", "User not logged in")
                }

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception in fetchSongsByIDs", e)
            }
        }
    }

    private fun fetchSongs(ids: List<String>): List<SongItem> {
        val list = mutableListOf<SongItem>()
        try {
            for (songID in ids) {
                val json = runBlocking {
                    requestWithFallback("/songs/$songID")
                }
                parseSongJson(json)?.let { list.add(it) }
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
    private fun fetchArtistsByIDs(artistsID: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tempList = mutableListOf<Artists>()

            try {
                withContext(Dispatchers.IO) {
                    for (artistID in artistsID) {
                        try {
                            val json = requestWithFallback("/artists/$artistID")
                            parseArtistJson(json)?.let { tempList.add(it) }
                        } catch (e: Exception) {
                            Log.e("API", "Artist failed: $artistID", e)
                        }
                    }
                }

                if (isAdded && view != null) {
                    binding.artistsRecyclerView.post {
                        artistsList.clear()
                        artistsList.addAll(tempList)
                        artistsAdapter.notifyDataSetChanged()
                        onDataLoaded("artists")
                    }
                }

            } catch (e: Exception) {
                    Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private fun parseArtistJson(jsonString: String): Artists?{
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return null

        val artistObject = json.getJSONObject("data")

        val id = artistObject.optString("id")
        val name = artistObject.optString("name")
        val type = artistObject.optString("type")

        val imageArray = artistObject.optJSONArray("image")
        val imageUrl = if (imageArray != null && imageArray.length() > 0) {
            imageArray.getJSONObject(2).optString("url")
        } else ""

        return Artists(id,name,"",imageUrl,type)
    }
    private fun fetchAlbumsByIDs(albumsID: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tempList = mutableListOf<DataItem>()

            withContext(Dispatchers.IO) {
                try {
                    for (albumID in albumsID) {
                        try {
                            val json = requestWithFallback("/albums?id=$albumID")
                            parseAlbumJson(json)?.let { tempList.add(it) }
                        } catch (e: Exception) {
                            Log.e("API", "Album failed: $albumID", e)
                        }
                    }

                    if (isAdded && view != null) {
                        binding.albumsRecyclerView.post {
                            albumsList.clear()
                            albumsList.addAll(tempList)
                            albumAdapter.notifyDataSetChanged()
                            onDataLoaded("albums")
                        }
                    }

                } catch (e: Exception) {
                    Log.e("SAAVN", "Exception: ${e.message}")
                }
            }
        }
    }
    private fun parseAlbumJson(jsonString: String): DataItem? {
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return null

        val albumObject = json.getJSONObject("data")

        val id = albumObject.optString("id")
        val name = albumObject.optString("name")

        val artistsObj = albumObject.optJSONObject("artists")
        val primaryArtists = artistsObj?.optJSONArray("primary")
        val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
            primaryArtists.getJSONObject(0).optString("name")
        } else ""

        val imageArray = albumObject.optJSONArray("image")
        val imageUrl = if (imageArray != null && imageArray.length() > 0) {
            imageArray.getJSONObject(2).optString("url")
        } else ""

        return DataItem(id,name,artistName,imageUrl)
    }
    private fun fetchPlaylistsByIDs(playlistsID: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tempList = mutableListOf<DataItem>()

            withContext(Dispatchers.IO) {
                try {
                    for (playlistID in playlistsID) {
                        try {
                            val json = requestWithFallback("/playlists?id=$playlistID")
                            parsePlaylistJson(json)?.let { tempList.add(it) }
                        } catch (e: Exception) {
                            Log.e("API", "Playlist failed: $playlistID", e)
                        }
                    }

                    if (isAdded && view != null) {
                        binding.playListRecyclerView.post {
                            playlistsList.clear()
                            playlistsList.addAll(tempList)
                            playListAdapter.notifyDataSetChanged()
                            onDataLoaded("playlists")
                        }
                    }

                } catch (e: Exception) {
                    Log.e("SAAVN", "Exception: ${e.message}")
                }
            }
        }
    }
    private fun parsePlaylistJson(jsonString: String): DataItem? {
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return null

        val albumObject = json.getJSONObject("data")

        val id = albumObject.optString("id")
        val name = albumObject.optString("name")

        val imageArray = albumObject.optJSONArray("image")
        val imageUrl = if (imageArray != null && imageArray.length() > 0) {
            imageArray.getJSONObject(2).optString("url")
        } else ""

        return DataItem(id,name,"",imageUrl)
    }
    private fun updateMiniPlayer(songItem: SongItem?) {
        val artistsName = songItem?.artist
            ?.takeIf { it.isNotEmpty() }     // only proceed if list not empty
            ?.joinToString(", ") { it.name } // join all artist names
            ?: "Unknown Artist"              // fallback if null or empty

        songName.text = Html.fromHtml(songItem?.name ?: "", Html.FROM_HTML_MODE_LEGACY)
        artistName.text = Html.fromHtml(artistsName,Html.FROM_HTML_MODE_LEGACY)
//        Picasso.get().load(songItem?.image[1]?.url).into(songImage)
//        setDynamicBackground(songItem?.image[1]?.url ?: "" ,songImage,background)
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
    private fun onDataLoaded(category: String) {
        when(category) {
            "songs" -> songsLoaded = true
            "artists" -> artistsLoaded = true
            "albums" -> albumsLoaded = true
            "playlists" -> playlistsLoaded = true
        }

        if (this.category == category) {
            activity?.runOnUiThread {
                updateCategoryVisibility()
            }
        }

        checkRefreshComplete()
    }
    private fun checkRefreshComplete() {
        if (songsLoaded && artistsLoaded && albumsLoaded && playlistsLoaded) {
            binding.swipeRefreshLayout.isRefreshing = false
            isRefreshing = false
            binding.progressBar.fadeOut()
        }
    }
    private fun updateCategoryVisibility() {
        if (!isAdded) return

        when (category) {
            "songs" -> {
                binding.likedSongRecyclerView.post {
                    if (songsLoaded && songList.isNotEmpty()) binding.likedSongRecyclerView.fadeIn()
                    else if (!songsLoaded) binding.likedSongRecyclerView.fadeOut()

                    binding.artistsRecyclerView.fadeOut()
                    binding.albumsRecyclerView.fadeOut()
                    binding.playListRecyclerView.fadeOut()

                    if (!songsLoaded) {
                        binding.progressBar.fadeIn()
                        binding.noFavText.fadeOut()
                    } else {
                        binding.progressBar.fadeOut()
                        if (songList.isEmpty()) binding.noFavText.fadeIn() else binding.noFavText.fadeOut()
                    }
                }
            }

            "artists" -> {
                binding.artistsRecyclerView.post {
                    if (artistsLoaded && artistsList.isNotEmpty()) binding.artistsRecyclerView.fadeIn()
                    else if (!artistsLoaded) binding.artistsRecyclerView.fadeOut()

                    binding.likedSongRecyclerView.fadeOut()
                    binding.albumsRecyclerView.fadeOut()
                    binding.playListRecyclerView.fadeOut()

                    if (!artistsLoaded) {
                        binding.progressBar.fadeIn()
                        binding.noFavText.fadeOut()
                    } else {
                        binding.progressBar.fadeOut()
                        if (artistsList.isEmpty()) binding.noFavText.fadeIn() else binding.noFavText.fadeOut()
                    }
                }
            }

            "albums" -> {
                binding.albumsRecyclerView.post {
                    if (albumsLoaded && albumsList.isNotEmpty()) binding.albumsRecyclerView.fadeIn()
                    else if (!albumsLoaded) binding.albumsRecyclerView.fadeOut()

                    binding.likedSongRecyclerView.fadeOut()
                    binding.artistsRecyclerView.fadeOut()
                    binding.playListRecyclerView.fadeOut()

                    if (!albumsLoaded) {
                        binding.progressBar.fadeIn()
                        binding.noFavText.fadeOut()
                    } else {
                        binding.progressBar.fadeOut()
                        if (albumsList.isEmpty()) binding.noFavText.fadeIn() else binding.noFavText.fadeOut()
                    }
                }
            }

            "playlists" -> {
                binding.playListRecyclerView.post {
                    if (playlistsLoaded && playlistsList.isNotEmpty()) binding.playListRecyclerView.fadeIn()
                    else if (!playlistsLoaded) binding.playListRecyclerView.fadeOut()

                    binding.likedSongRecyclerView.fadeOut()
                    binding.artistsRecyclerView.fadeOut()
                    binding.albumsRecyclerView.fadeOut()

                    if (!playlistsLoaded) {
                        binding.progressBar.fadeIn()
                        binding.noFavText.fadeOut()
                    } else {
                        binding.progressBar.fadeOut()
                        if (playlistsList.isEmpty()) binding.noFavText.fadeIn() else binding.noFavText.fadeOut()
                    }
                }
            }
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

    private fun loadFavouriteSongs(userID: String) {
        val songsReference = FirebaseDatabase.getInstance().getReference().child("Users").child(userID).child("Favourites")
            .child("Songs")
        songsReference.get().addOnSuccessListener { songsSnapshot ->
            if (songsSnapshot.exists()) {
                val songIDList = ArrayList<String>()
                for (songSnap in songsSnapshot.children) {
                    val songID  = songSnap.child("id").getValue(String::class.java)
                    if (songID != null) songIDList.add(songID)
                }

                songList.clear()
                if (songIDList.isEmpty()) onDataLoaded("songs") else fetchSongsByIDs(songIDList)
            } else {
                onDataLoaded("songs")
            }
        }.addOnFailureListener {
            onDataLoaded("songs")
        }
    }
    private fun loadFavouriteArtists(userID: String) {
        val artistsReference = FirebaseDatabase.getInstance().getReference().child("Users").child(userID).child("Favourites")
            .child("Artists")

        artistsReference.get().addOnSuccessListener { artistSnapshot ->
            if (artistSnapshot.exists()) {
                val artistIDList = mutableListOf<String>()
                for (artistSnap in artistSnapshot.children) {
                    val artistId = artistSnap.child("id").getValue(String::class.java)
                    if (artistId != null) artistIDList.add(artistId)
                }

                artistsList.clear()
                if (artistIDList.isEmpty()) onDataLoaded("artists") else fetchArtistsByIDs(artistIDList)
            } else {
                onDataLoaded("artists")
            }
        }.addOnFailureListener {
            onDataLoaded("artists")
        }
    }
    private fun loadFavouriteAlbums(userID: String) {
        val albumsReference = FirebaseDatabase.getInstance().getReference().child("Users").child(userID).child("Favourites")
            .child("Albums")

        albumsReference.get().addOnSuccessListener { artistSnapshot ->
            if (artistSnapshot.exists()) {
                val albumsIDList = mutableListOf<String>()
                for (albumSnap in artistSnapshot.children) {
                    val albumId = albumSnap.child("id").getValue(String::class.java)
                    if (albumId != null) albumsIDList.add(albumId)
                }

                albumsList.clear()
                if (albumsIDList.isEmpty()) onDataLoaded("albums") else fetchAlbumsByIDs(albumsIDList)
            } else {
                onDataLoaded("albums")
            }
        }.addOnFailureListener {
            onDataLoaded("albums")
        }
    }
    private fun loadFavouritePlaylists(userID: String) {
        val playlistReference = FirebaseDatabase.getInstance().getReference().child("Users").child(userID).child("Favourites")
            .child("Playlists")

        playlistReference.get().addOnSuccessListener { artistSnapshot ->
            if (artistSnapshot.exists()) {
                val playlistIDList = mutableListOf<String>()
                for (playlistSnap in artistSnapshot.children) {
                    val playlistId = playlistSnap.child("id").getValue(String::class.java)
                    if (playlistId != null) playlistIDList.add(playlistId)
                }

                playlistsList.clear()
                if (playlistIDList.isEmpty()) onDataLoaded("playlists") else fetchPlaylistsByIDs(playlistIDList)
            } else {
                onDataLoaded("playlists")
            }
        }.addOnFailureListener {
            onDataLoaded("playlists")
        }
    }
}