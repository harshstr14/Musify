package com.example.musify

import android.content.ComponentName
import android.content.Context
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
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.example.musify.Home.RecentlyPlayedManager
import com.example.musify.databinding.FragmentSearchBinding
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
import org.json.JSONArray
import org.json.JSONObject

class Search : Fragment() {
    private lateinit var binding: FragmentSearchBinding
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
    private lateinit var songAdapter: SuggestionSongAdapter
    private lateinit var artistsAdapter: ArtistsAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var playListAdapter: PlayListAdapter
    private var songList = ArrayList<SongItem>()
    private var artistsList = mutableListOf<Artists>()
    private var albumList = mutableListOf<DataItem>()
    private var playlistList = mutableListOf<DataItem>()

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSearchBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", songList)
                    putExtra("index", position)
                }

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

        backgroundView = view.findViewById(R.id.backgroundView)
        miniPlayer = view.findViewById(R.id.miniPlayer)
        songName = view.findViewById(R.id.songNameText)
        artistName = view.findViewById(R.id.artistNameText)
        songImage = view.findViewById(R.id.songImage)
        playPauseButton = view.findViewById(R.id.playButton)
        nextButton = view.findViewById(R.id.appCompatImageView7)
        prevButton = view.findViewById(R.id.appCompatImageView3)
        background = view.findViewById(R.id.backGroundImageView)

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
                    fetchSongDataByQuery(query, "songs")
                    fetchArtistsDataByQuery(query, "artists")
                    fetchPlaylistsDataByQuery(query, "playlists")
                    fetchAlbumsDataByQuery(query, "albums")
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
                    fetchSongDataByQuery(newText, "songs")
                    fetchArtistsDataByQuery(newText, "artists")
                    fetchPlaylistsDataByQuery(newText, "playlists")
                    fetchAlbumsDataByQuery(newText, "albums")
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
    fun fetchSongDataByQuery(query: String,root: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/${root}?query=${query}&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parseSongDataJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    private fun parseSongDataJson(jsonString: String) {
        val list = ArrayList<SongItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

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
            val downloadUrl = if (downloadArray != null && downloadArray.length() > 0) {
                downloadArray.getJSONObject(2).optString("url")
            } else ""

            val artistsObj = songObject.optJSONObject("artists")
            val primaryArtists = artistsObj?.optJSONArray("primary")
            val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                primaryArtists.getJSONObject(0).optString("name")
            } else ""

            list.add(SongItem(id, name, artistName, image, duration, downloadUrl))
        }

        activity?.runOnUiThread {
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
    fun fetchArtistsDataByQuery(query: String,root: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/${root}?query=${query}&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parseArtistsDataJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    private fun parseArtistsDataJson(jsonString: String) {
        val list = mutableListOf<Artists>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

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
        activity?.runOnUiThread {
            if (isAdded && view != null) {
                binding.artistsRecyclerView.post {
                    artistsList.clear()
                    artistsList.addAll(list)
                    artistsAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    fun fetchPlaylistsDataByQuery(query: String,root: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/${root}?query=${query}&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parsePlaylistDataJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    private fun parsePlaylistDataJson(jsonString: String) {
        val list = mutableListOf<DataItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

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

        activity?.runOnUiThread {
            if (isAdded && view != null) {
                binding.playListRecyclerView.post {
                    playlistList.clear()
                    playlistList.addAll(list)
                    playListAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    fun fetchAlbumsDataByQuery(query: String,root: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/${root}?query=${query}&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parseAlbumsDataJson(responseBody)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    private fun parseAlbumsDataJson(jsonString: String) {
        val list = mutableListOf<DataItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

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

        activity?.runOnUiThread {
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
        songName.text = Html.fromHtml(songItem?.name ?: "", Html.FROM_HTML_MODE_LEGACY)
        artistName.text = songItem?.artist
        Picasso.get().load(songItem?.image[1]?.url).into(songImage)
        setDynamicBackground(songItem?.image[1]?.url ?: "" ,songImage,background)

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