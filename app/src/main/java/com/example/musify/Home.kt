package com.example.musify

import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.databinding.FragmentHomeBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Artists
import com.example.musify.songData.Download
import com.example.musify.songData.Image
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class Home : Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var recentPlayedList = ArrayList<SongItem>()
    private val newSongsList = ArrayList<SongItem>()
    private val todayTrendingSongList = ArrayList<SongItem>()
    private val artistsList = mutableListOf<Artists>()
    private val topAlbumList = mutableListOf<DataItem>()
    private val topPlayList = mutableListOf<DataItem>()
    private lateinit var recentPlayedAdapter: SongAdapter
    private lateinit var newSongAdapter: NewSongAdapter
    private lateinit var todayTrendingSongAdapter: SongAdapter
    private lateinit var artistsAdapter: ArtistsAdapter
    private lateinit var topAlbumAdapter: AlbumAdapter
    private lateinit var topPLayListAdapter: PlayListAdapter
    private var musicPlayerService: MusicPlayerService ?= null
    private var bound = false
    private lateinit var miniPlayer: View
    private lateinit var songName: TextView
    private lateinit var artistName: TextView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var shimmerFrameLayout: ShimmerFrameLayout
    private var totalRequests = 5
    private var completedRequests = 0
    private lateinit var apiUrl: String
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    object RecentlyPlayedManager {
        fun addToRecentlyPlayed(context: Context,song: SongItem,maxSize: Int = 20) {
            val pref = context.getSharedPreferences("MusifyPref", MODE_PRIVATE)
            val gson = Gson()
            val json = pref.getString("recently_played",null)

            val type = object: TypeToken<ArrayList<SongItem>>() {}.type
            val recentList: ArrayList<SongItem> = if (json != null) gson.fromJson(json,type) else arrayListOf()

            recentList.removeAll{it.id == song.id}
            recentList.add(0,song)

            if (recentList.size > maxSize) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    recentList.removeLast()
                } else {
                    recentList.removeAt(recentList.size - 1)
                }
            }

            pref.edit { putString("recently_played", gson.toJson(recentList)) }
        }
        fun getRecentPlayed(context: Context): ArrayList<SongItem> {
            val pref = context.getSharedPreferences("MusifyPref", MODE_PRIVATE)
            val json = pref.getString("recently_played", null)
            return if (json != null) {
                val type = object : TypeToken<ArrayList<SongItem>>() {}.type
                Gson().fromJson(json, type)
            } else arrayListOf()
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
                    binding.constraintLayout0.setPadding(0,0,0,0)
                } else {
                    if (miniPlayer.visibility != View.VISIBLE) {
                        // Prepare for animation
                        miniPlayer.translationY = miniPlayer.height.toFloat()
                        miniPlayer.alpha = 0f
                        miniPlayer.visibility = View.VISIBLE

                        val paddingInDp = 80
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
        binding = FragmentHomeBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiUrl = BuildConfig.API_BASE_URL

        showDialogOncePerLaunch()

        shimmerFrameLayout = binding.shimmerFrameLayout

        shimmerFrameLayout.startShimmer()
        shimmerFrameLayout.visibility= View.VISIBLE
        binding.scrollView.visibility = View.GONE

        auth = FirebaseAuth.getInstance()
        val userID = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference().child("Users")

        binding.textView1.text = getGreetingMessage()

        if (userID != null) {
            database.child(userID).get().addOnSuccessListener {
                val quality = it.child("quality").value.toString()
                if (quality == "96kbps") {
                    musicPlayerService?.qualityIndex = 2
                } else if (quality == "160kbps") {
                    musicPlayerService?.qualityIndex = 3
                }
                Log.d("Quality", quality)
                val imageUrl = it.child("photoUrl").value.toString()

                Picasso.get().load(imageUrl).into(binding.profileImage)
            }
        }

        binding.profileImage.setOnClickListener {
            val fragmentManager = parentFragmentManager
            val fragmentTransaction = fragmentManager.beginTransaction()
            fragmentTransaction.setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            )
            fragmentTransaction.replace(R.id.frameLayout, Profile())
            fragmentTransaction.addToBackStack(null)
            fragmentTransaction.commit()
        }

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

        recentPlayedList = RecentlyPlayedManager.getRecentPlayed(requireContext())
        recentPlayedAdapter = SongAdapter(recentPlayedList)
        binding.recentRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)
        binding.recentRecyclerView.adapter =  recentPlayedAdapter

        if (recentPlayedList.isNotEmpty()) {
            binding.textView.visibility = View.VISIBLE
            binding.recentRecyclerView.visibility = View.VISIBLE
        } else {
            binding.textView.visibility = View.GONE
            binding.recentRecyclerView.visibility = View.GONE
        }

        newSongAdapter = NewSongAdapter(newSongsList)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(),2,GridLayoutManager.HORIZONTAL,false)
        binding.recyclerView.adapter = newSongAdapter

        todayTrendingSongAdapter = SongAdapter(todayTrendingSongList)
        binding.recyclerView1.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)
        binding.recyclerView1.adapter = todayTrendingSongAdapter

        artistsAdapter = ArtistsAdapter(artistsList)
        binding.recyclerView2.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)
        binding.recyclerView2.adapter = artistsAdapter

        topAlbumAdapter = AlbumAdapter(topAlbumList)
        binding.recyclerView3.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)
        binding.recyclerView3.adapter = topAlbumAdapter

        topPLayListAdapter = PlayListAdapter(topPlayList)
        binding.recyclerView4.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)
        binding.recyclerView4.adapter = topPLayListAdapter

        fetchPlaylistsByID("6689255","songs",newSongsList,newSongAdapter)
        fetchPlaylistsByID("946682072","songs",todayTrendingSongList,todayTrendingSongAdapter)
        fetchArtistsByQuery("top artists","results",artistsList,artistsAdapter)
        fetchAlbumByQuery("latest","results",topAlbumList,topAlbumAdapter)
        fetchPlayListByQuery("Top","results",topPlayList,topPLayListAdapter)

        recentPlayedAdapter.setOnItemClickListener(object : SongAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", recentPlayedList)
                    putExtra("index", position)
                }

                ContextCompat.startForegroundService(requireContext(), intent)
                RecentlyPlayedManager.addToRecentlyPlayed(requireContext(),recentPlayedList[position])
            }
        })

        newSongAdapter.setOnItemClickListener(object : NewSongAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", newSongsList)
                    putExtra("index", position)
                }

                ContextCompat.startForegroundService(requireContext(), intent)
                RecentlyPlayedManager.addToRecentlyPlayed(requireContext(),newSongsList[position])
            }
        })

        todayTrendingSongAdapter.setOnItemClickListener(object : SongAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_NEW
                    putParcelableArrayListExtra("playlist", todayTrendingSongList)
                    putExtra("index", position)
                }

                ContextCompat.startForegroundService(requireContext(), intent)
                RecentlyPlayedManager.addToRecentlyPlayed(requireContext(),todayTrendingSongList[position])
            }
        })

        artistsAdapter.setOnItemClickListener(object : ArtistsAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), ArtistActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("artistID",artistsList[position].id)
                startActivity(intent)
            }
        })

        topAlbumAdapter.setOnItemClickListener(object : AlbumAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), AlbumActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("id",topAlbumList[position].id)
                startActivity(intent)
            }
        })

        topPLayListAdapter.setOnItemClickListener(object : PlayListAdapter.OnItemClickListener {
            override fun omItemClick(position: Int) {
                val intent = Intent(requireContext(), PlaylistActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra("id",topPlayList[position].id)
                startActivity(intent)
            }
        })
    }
    fun fetchPlaylistsByID(playListId: String,root: String,targetList: MutableList<SongItem>, adapter: SongAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/playlists?id=$playListId&limit=40")
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

                parseNewSongsJson(responseBody,root,targetList,adapter)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    fun fetchPlaylistsByID(playListId: String,root: String,targetList: MutableList<SongItem>, adapter: NewSongAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/playlists?id=$playListId&limit=40")
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

                parseNewSongsJson(responseBody,root,targetList,adapter)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    fun fetchAlbumByQuery(query: String, root: String, targetList: MutableList<DataItem>, adapter: AlbumAdapter) {
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

                parseAlbumListJson(responseBody,root,targetList,adapter)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    fun fetchArtistsByQuery(query: String, root: String, targetList: MutableList<Artists>, adapter: ArtistsAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/artists?query=${query}&limit=20")
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

                parseArtistsJson(responseBody,root,targetList,adapter)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    fun fetchPlayListByQuery(query: String, root: String, targetList: MutableList<DataItem>, adapter: PlayListAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/playlists?query=${query}&limit=20")
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

                parsePlaylistJson(responseBody,root,targetList,adapter)

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }
    }
    suspend fun parseNewSongsJson(jsonString: String, root: String, targetList: MutableList<SongItem>, adapter: SongAdapter) {
        val parsedSongs = mutableListOf<SongItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val songsArray = data.getJSONArray(root)
            Log.d("Song", "Songs array length: ${songsArray.length()}")

            for (i in 0 until songsArray.length()) {
                val song = songsArray.getJSONObject(i)

                val id = song.optString("id")
                val name = song.optString("name")
                val duration = song.optInt("duration")

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

                val downloadArray = song.optJSONArray("downloadUrl")
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

                val artistsObj = song.optJSONObject("artists")
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
                            image = artistsImage?.optJSONObject(2)?.optString("url") ?: "",
                            type = artistsObject?.optString("type") ?: ""
                        )
                    )
                }

                parsedSongs.add(SongItem(id, name, primaryArtists, image,duration,download))
            }
        }

        withContext(Dispatchers.Main) {
            targetList.addAll(parsedSongs)
            Log.d("Song", "Parsed ${parsedSongs.size} songs: $parsedSongs")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    suspend fun parseNewSongsJson(jsonString: String, root: String, targetList: MutableList<SongItem>, adapter: NewSongAdapter) {
        val parsedSongs = mutableListOf<SongItem>()

        withContext(Dispatchers.Default)  {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val songsArray = data.getJSONArray(root)
            Log.d("Song", "Songs array length: ${songsArray.length()}")

            for (i in 0 until songsArray.length()) {
                val song = songsArray.getJSONObject(i)

                val id = song.optString("id")
                val name = song.optString("name")
                val duration = song.optInt("duration")

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

                val downloadArray = song.optJSONArray("downloadUrl")
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

                val artistsObj = song.optJSONObject("artists")
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
                            image = artistsImage?.optJSONObject(2)?.optString("url") ?: "",
                            type = artistsObject?.optString("type") ?: ""
                        )
                    )
                }

                parsedSongs.add(SongItem(id, name, primaryArtists, image,duration,download))
            }
        }

        withContext(Dispatchers.Main) {
            targetList.addAll(parsedSongs)
            Log.d("Song", "Parsed ${parsedSongs.size} songs: $parsedSongs")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    suspend fun parseArtistsJson(jsonString: String, root: String, targetList: MutableList<Artists>, adapter: ArtistsAdapter) {
        val parsedArtist = mutableListOf<Artists>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success",false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val resultArray = data.getJSONArray(root)

            for (i in 0 until resultArray.length()) {
                val artistObject = resultArray.getJSONObject(i)

                val id = artistObject.optString("id")
                val name = artistObject.optString("name")
                val role = artistObject.optString("role")
                val type = artistObject.optString("type")

                val imageArray = artistObject.optJSONArray("image")
                val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(2).optString("url")

                } else ""

                parsedArtist.add(Artists(id, name, role, imageUrl,type))
            }
        }

        withContext(Dispatchers.Main) {
            targetList.addAll(parsedArtist)
            Log.d("Artists", "Parsed ${parsedArtist.size} artists : $parsedArtist")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    suspend fun parsePlaylistJson(jsonString: String, root: String, targetList: MutableList<DataItem>, adapter: PlayListAdapter) {
        val parsedPlaylist = mutableListOf<DataItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val songsArray = data.getJSONArray(root)
            Log.d("Song", "Songs array length: ${songsArray.length()}")

            for (i in 0 until songsArray.length()) {
                val song = songsArray.getJSONObject(i)

                val id = song.optString("id")
                val name = song.optString("name")

                val imageArray = song.optJSONArray("image")
                val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(2).optString("url")

                } else ""

                val artistsObj = song.optJSONObject("artists")
                val primaryArtists = artistsObj?.optJSONArray("primary")
                val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                    primaryArtists.getJSONObject(0).optString("name")
                } else ""

                parsedPlaylist.add(DataItem(id, name, artistName, imageUrl))
            }
        }

        withContext(Dispatchers.Main) {
            targetList.addAll(parsedPlaylist)
            Log.d("Playlists", "Parsed ${parsedPlaylist.size} Playlists : $parsedPlaylist")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    suspend fun parseAlbumListJson(jsonString: String, root: String, targetList: MutableList<DataItem>, adapter: AlbumAdapter) {
        val parsedAlbum = mutableListOf<DataItem>()

        withContext(Dispatchers.Default) {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", false)
            if (!success) return@withContext

            val data = json.getJSONObject("data")
            val songsArray = data.getJSONArray(root)
            Log.d("Song", "Songs array length: ${songsArray.length()}")

            for (i in 0 until songsArray.length()) {
                val song = songsArray.getJSONObject(i)

                val id = song.optString("id")
                val name = song.optString("name")

                val imageArray = song.optJSONArray("image")
                val imageUrl = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(2).optString("url")

                } else ""

                val artistsObj = song.optJSONObject("artists")
                val primaryArtists = artistsObj?.optJSONArray("primary")
                val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                    primaryArtists.getJSONObject(0).optString("name")
                } else ""

                parsedAlbum.add(DataItem(id, name, artistName, imageUrl))
            }
        }

        withContext(Dispatchers.Main) {
            targetList.addAll(parsedAlbum)
            Log.d("Album", "Parsed ${parsedAlbum.size} albums : $parsedAlbum")
            adapter.notifyDataSetChanged()
            onDataLoaded()
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
    private fun onDataLoaded() {
        completedRequests++
        if (completedRequests >= totalRequests) {
            activity?.runOnUiThread {
                shimmerFrameLayout.stopShimmer()
                shimmerFrameLayout.visibility = View.GONE
                binding.scrollView.visibility = View.VISIBLE
            }
        }
    }
    private fun getGreetingMessage(): String {
        val hour = LocalTime.now().hour

        return when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
    }
    private fun checkForUpdate(context: Context) {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersion = remoteConfig.getString("latest_version")
                val message = remoteConfig.getString("update_message")

                val currentVersion = getCurrentVersion(context)

                if (isNewVersionAvailable(currentVersion, latestVersion)) {
                    showDialog(context,message,latestVersion, currentVersion)
                }
            }
        }
    }
    private fun isNewVersionAvailable(current: String, latest: String): Boolean {
        val currentParts = current.split(".")
        val latestParts = latest.split(".")

        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
    private fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    private fun showDialog(context: Context,title: String, latestVersion: String, currentVersion: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.update_dialog,null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.textView).text = title
        "Version $currentVersion - $latestVersion".also { dialogView.findViewById<TextView>(R.id.textView20).text = it }

        dialogView.findViewById<TextView>(R.id.btnUpdate).setOnClickListener {

        }

        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun showDialogOncePerLaunch() {
        val prefs = requireContext()
            .getSharedPreferences(AppConstants.PREF_NAME, MODE_PRIVATE)

        val alreadyShown =
            prefs.getBoolean(AppConstants.KEY_DIALOG_SHOWN, false)

        if (!alreadyShown) {
            checkForUpdate(requireContext())
            prefs.edit { putBoolean(AppConstants.KEY_DIALOG_SHOWN, true) }
        }
    }
}