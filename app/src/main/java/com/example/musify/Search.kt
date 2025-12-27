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
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.Home.RecentlyPlayedManager
import com.example.musify.databinding.FragmentSearchBinding
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Search : Fragment() {
    private lateinit var binding: FragmentSearchBinding
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
    private lateinit var songAdapter: SuggestionSongAdapter
    private lateinit var artistsAdapter: ArtistsAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var playListAdapter: PlayListAdapter
    private var songList = ArrayList<SongItem>()
    private var artistsList = mutableListOf<Artists>()
    private var albumList = mutableListOf<DataItem>()
    private var playlistList = mutableListOf<DataItem>()
    private lateinit var apiUrl: String
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    object SearchHistoryManager {
        private const val PREF_NAME = "search_history"
        private const val KEY_HISTORY = "history"

        fun addSearch(context: Context, query: String) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val history = getHistory(context)

            if (query.isNotBlank()) {
                history.remove(query)
                history.add(0, query)
//                if (history.size > 10) {
//                    history.removeLast()
//                }
            }

            val json = JSONArray(history).toString()
            prefs.edit { putString(KEY_HISTORY, json) }
        }
        fun getHistory(context: Context): MutableList<String> {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
            val array = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            return list
        }
        fun clearHistory(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit { remove(KEY_HISTORY) }
        }
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
                    binding.searchRecyclerView.setPadding(0,0,0,paddingInPx)
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
                        binding.searchRecyclerView.setPadding(0,0,0,paddingInPx)
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
        binding = FragmentSearchBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiUrl = BuildConfig.API_BASE_URL

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference().child("Users")

        val userID = auth.currentUser?.uid
        val favSongRef = database.child(userID!!).child("Favourites").child("Songs")

        songAdapter = SuggestionSongAdapter(songList) { songItem ->
            if (songItem.isFav) {
                val songData = mapOf(
                    "id" to songItem.id,
                    "songName" to songItem.name,
                    "isFavourite" to true
                )
                favSongRef.child(songItem.id).setValue(songData)
                Toast.makeText(requireContext(), "Added To Favourite", Toast.LENGTH_SHORT).show()
            } else {
                favSongRef.child(songItem.id).removeValue()
                Toast.makeText(requireContext(), "Removed From Favourite", Toast.LENGTH_SHORT).show()
            }
        }
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL,false)
        binding.searchRecyclerView.adapter = songAdapter

        songAdapter.setOnItemClickListener(object : SuggestionSongAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val intent = Intent(requireContext(), MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_SONG
                    putParcelableArrayListExtra("playlist", songList)
                    putExtra("index", position)
                }
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(requireView().windowToken, 0)

                ContextCompat.startForegroundService(requireContext(), intent)
                RecentlyPlayedManager.addToRecentlyPlayed(requireContext(),songList[position])
            }
        })

        albumAdapter = AlbumAdapter(albumList)
        binding.albumsRecyclerView.layoutManager = GridLayoutManager(requireContext(),3,
            GridLayoutManager.VERTICAL,false)
        binding.albumsRecyclerView.adapter = albumAdapter

        albumAdapter.setOnItemClickListener(object : AlbumAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), AlbumActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("id",albumList[position].id)
                startActivity(intent)
            }
        })

        playListAdapter = PlayListAdapter(playlistList)
        binding.playListRecyclerView.layoutManager = GridLayoutManager(requireContext(),3,
            GridLayoutManager.VERTICAL,false)
        binding.playListRecyclerView.adapter = playListAdapter

        playListAdapter.setOnItemClickListener(object : PlayListAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), PlaylistActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("id",playlistList[position].id)
                startActivity(intent)
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

        miniPlayer = view.findViewById(R.id.miniPlayer)
        songName = view.findViewById(R.id.songNameText)
        artistName = view.findViewById(R.id.artistNameText)
        playPauseButton = view.findViewById(R.id.playButton)
        lottieAnimationView = view.findViewById(R.id.lottieAnimationView)
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

        val searchCategoryList = listOf(
            "songs","artists","albums","playlists"
        )

        var searchCategory = searchCategoryList[0]

        val searchCategoryAdapter = SearchCategoryAdapter(searchCategoryList) { position ->
            searchCategory = searchCategoryList[position]
            when (searchCategory) {
                "songs" -> {
                    if (songList.isNotEmpty()) {
                        binding.artistsRecyclerView.fadeOut()
                        binding.searchRecyclerView.fadeIn()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    } else {
                        binding.artistsRecyclerView.fadeOut()
                        binding.searchRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    }
                }
                "artists" -> {
                    if (artistsList.isNotEmpty()) {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeIn()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    } else {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    }
                }
                "playlists" -> {
                    if (playlistList.isNotEmpty()) {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeIn()
                        binding.albumsRecyclerView.fadeOut()
                    } else {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    }
                }
                "albums" -> {
                    if (albumList.isNotEmpty()) {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeIn()
                    } else {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    }
                }
            }
        }

        binding.searchCategoryRecyclerView.layoutManager = LinearLayoutManager(requireContext(),
            LinearLayoutManager.HORIZONTAL,false)
        binding.searchCategoryRecyclerView.adapter = searchCategoryAdapter

        val recentSearchList = SearchHistoryManager.getHistory(requireContext())
        val recentSearchAdapter = RecentSearchAdapter(recentSearchList) { query ->
            binding.customSearchView.setQuery(query,true)
        }
        binding.searchHistoryRecyclerView.layoutManager = LinearLayoutManager(requireContext(),
            LinearLayoutManager.VERTICAL,false)
        binding.searchHistoryRecyclerView.adapter = recentSearchAdapter

        binding.customSearchView.setOnQueryTextListener(object : CustomSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                if (query.isNotBlank()) {
                    // save the search
                    SearchHistoryManager.addSearch(requireContext(), query)

                    // hide recent list
                    binding.noRecentText.fadeOut()
                    binding.searchHistoryRecyclerView.fadeOut()
                    binding.recentSearchText.fadeOut()
                    binding.deleteIcon.fadeOut()

                    // trigger actual search
                    fetchSongDataByQuery(query)
                    fetchArtistsDataByQuery(query)
                    fetchPlaylistsDataByQuery(query)
                    fetchAlbumsDataByQuery(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isEmpty()) {
                    // show recent search
                    val recentList = SearchHistoryManager.getHistory(requireContext())
                    if (recentList.isNotEmpty()) {
                        // show recent search list
                        (binding.searchHistoryRecyclerView.adapter as RecentSearchAdapter)
                            .updateList(recentList)
                        binding.searchHistoryRecyclerView.fadeIn()
                        binding.recentSearchText.fadeIn()
                        binding.deleteIcon.fadeIn()
                        binding.noRecentText.fadeOut() // hide "No recent" since list exists
                    } else {
                        // show "No recent searches"
                        binding.noRecentText.fadeIn()
                        binding.searchHistoryRecyclerView.fadeIn()
                        binding.recentSearchText.fadeIn()
                        binding.deleteIcon.fadeIn()
                    }

                    // clear search results
                    clearAllSearchResults()
                    return true
                } else {
                    binding.noRecentText.fadeOut()
                    binding.searchHistoryRecyclerView.fadeOut()
                    binding.recentSearchText.fadeOut()
                    binding.deleteIcon.fadeOut()

                    // show search results
                    fetchSongDataByQuery(newText)
                    fetchArtistsDataByQuery(newText)
                    fetchPlaylistsDataByQuery(newText)
                    fetchAlbumsDataByQuery(newText)
                }

                when (searchCategory) {
                    "songs" -> {
                        binding.artistsRecyclerView.fadeOut()
                        binding.searchRecyclerView.fadeIn()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    }
                    "artists" -> {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeIn()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeOut()
                    }
                    "playlists" -> {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeIn()
                        binding.albumsRecyclerView.fadeOut()
                    }
                    "albums" -> {
                        binding.searchRecyclerView.fadeOut()
                        binding.artistsRecyclerView.fadeOut()
                        binding.playListRecyclerView.fadeOut()
                        binding.albumsRecyclerView.fadeIn()
                    }
                }
                return true
            }
        })

        if (recentSearchList.isNotEmpty()) {
            binding.noRecentText.fadeOut()
        } else {
            binding.noRecentText.fadeIn()
        }

        binding.deleteIcon.setOnClickListener {
            if (recentSearchList.isNotEmpty()) {
                SearchHistoryManager.clearHistory(requireContext())
                recentSearchList.clear()
                recentSearchAdapter.notifyDataSetChanged()
                binding.noRecentText.fadeIn()
                Toast.makeText(requireContext(),"Search history cleared",Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(),"Nothing to clear ",Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun fetchSongDataByQuery(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/songs?query=${query}&limit=30")
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
                    Toast.makeText(requireContext(), "Empty response", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                parseSongDataJson(responseBody)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private suspend fun parseSongDataJson(jsonString: String) {
        val list = ArrayList<SongItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success",false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val resultArray = data.getJSONArray("results")

            for (i in 0 until resultArray.length()) {
                val songObject = resultArray.getJSONObject(i)

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

                list.add(SongItem(id, name, primaryArtists, image, duration, download))
            }
        }

        withContext(Dispatchers.Main) {
            if (isAdded && view != null) {
                binding.searchRecyclerView.post {
                    songList.clear()
                    songList.addAll(list)
                    updateFavourite()
                    songAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun fetchArtistsDataByQuery(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/artists?query=${query}&limit=30")
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
                    Toast.makeText(requireContext(), "Empty response", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                parseArtistsDataJson(responseBody)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private suspend fun parseArtistsDataJson(jsonString: String) {
        val list = mutableListOf<Artists>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success",false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val resultArray = data.getJSONArray("results")

            for (i in 0 until resultArray.length()) {
                val artistObject = resultArray.getJSONObject(i)

                val id = artistObject.optString("id")
                val name = artistObject.optString("name")
                val role = artistObject.optString("role")
                val type = artistObject.optString("type")

                val imageArray = artistObject.optJSONArray("image")
                val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(1).optString("url")

                } else ""

                list.add(Artists(id, name, role, imageUrl,type))
            }
        }

        withContext(Dispatchers.Main) {
            if (isAdded && view != null) {
                binding.artistsRecyclerView.post {
                    artistsList.clear()
                    artistsList.addAll(list)
                    artistsAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun fetchPlaylistsDataByQuery(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/playlists?query=${query}&limit=30")
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
                    Toast.makeText(requireContext(), "Empty response", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                parsePlaylistDataJson(responseBody)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private suspend fun parsePlaylistDataJson(jsonString: String) {
        val list = mutableListOf<DataItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val songsArray = data.getJSONArray("results")

            for (i in 0 until songsArray.length()) {
                val song = songsArray.getJSONObject(i)

                val id = song.optString("id")
                val name = song.optString("name")

                val imageArray = song.optJSONArray("image")
                val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(1).optString("url")

                } else ""

                list.add(DataItem(id, name, "", imageUrl))
            }
        }

        withContext(Dispatchers.Main) {
            if (isAdded && view != null) {
                binding.playListRecyclerView.post {
                    playlistList.clear()
                    playlistList.addAll(list)
                    playListAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun fetchAlbumsDataByQuery(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/albums?query=${query}&limit=30")
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
                    Toast.makeText(requireContext(), "Empty response", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                parseAlbumsDataJson(responseBody)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    private suspend fun parseAlbumsDataJson(jsonString: String) {
        val list = mutableListOf<DataItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val songsArray = data.getJSONArray("results")

            for (i in 0 until songsArray.length()) {
                val song = songsArray.getJSONObject(i)

                val id = song.optString("id")
                val name = song.optString("name")

                val imageArray = song.optJSONArray("image")
                val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(1).optString("url")

                } else ""

                val artistsObj = song.optJSONObject("artists")
                val primaryArtists = artistsObj?.optJSONArray("primary")
                val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                    primaryArtists.getJSONObject(0).optString("name")
                } else ""

                list.add(DataItem(id, name, artistName, imageUrl))
            }
        }

        withContext(Dispatchers.Main) {
            if (isAdded && view != null) {
                binding.albumsRecyclerView.post {
                    albumList.clear()
                    albumList.addAll(list)
                    albumAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    private fun updateMiniPlayer(songItem: SongItem?) {
        val artistsName = songItem?.artist
            ?.takeIf { it.isNotEmpty() }     // only proceed if list not empty
            ?.joinToString(", ") { it.name } // join all artist names
            ?: "Unknown Artist"              // fallback if null or empty

        songName.text = Html.fromHtml(songItem?.name ?: "", Html.FROM_HTML_MODE_LEGACY)
        artistName.text = Html.fromHtml(artistsName,Html.FROM_HTML_MODE_LEGACY)
        //Picasso.get().load(songItem?.image[1]?.url).into(songImage)
        //setDynamicBackground(songItem?.image[1]?.url ?: "" ,songImage,background)
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
    private fun clearAllSearchResults() {
        songList.clear()
        binding.searchRecyclerView.fadeOut()
        songAdapter.notifyDataSetChanged()

        artistsList.clear()
        binding.artistsRecyclerView.fadeOut()
        artistsAdapter.notifyDataSetChanged()

        playlistList.clear()
        binding.playListRecyclerView.fadeOut()
        playListAdapter.notifyDataSetChanged()

        albumList.clear()
        binding.albumsRecyclerView.fadeOut()
        albumAdapter.notifyDataSetChanged()
    }
    private fun updateFavourite() {
        val userID = auth.currentUser?.uid
        val favSongRef = database.child(userID!!).child("Favourites").child("Songs")

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