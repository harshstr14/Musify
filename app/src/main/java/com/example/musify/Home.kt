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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
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
import com.example.musify.databinding.FragmentHomeBinding
import com.example.musify.service.MusicPlayerService
import com.example.musify.songData.Artists
import com.example.musify.songData.Image
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalTime

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
    private lateinit var songImage: AppCompatImageView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var background: AppCompatImageView
    private lateinit var shimmerFrameLayout: ShimmerFrameLayout
    private var totalRequests = 5
    private var completedRequests = 0

    object RecentlyPlayedManager {
        fun addToRecentlyPlayed(context: Context,song: SongItem,maxSize: Int = 20) {
            val pref = context.getSharedPreferences("MusifyPref", Context.MODE_PRIVATE)
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
            val pref = context.getSharedPreferences("MusifyPref", Context.MODE_PRIVATE)
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
        binding = FragmentHomeBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shimmerFrameLayout = binding.shimmerFrameLayout

        shimmerFrameLayout.startShimmer()
        shimmerFrameLayout.visibility= View.VISIBLE
        binding.constraintLayout.visibility = View.GONE

        auth = FirebaseAuth.getInstance()
        val userID = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference().child("Users")

        binding.textView1.text = getGreetingMessage()

        if (userID != null) {
            database.child(userID).get().addOnSuccessListener {
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
        fetchPlaylistsByID("110858205","songs",todayTrendingSongList,todayTrendingSongAdapter)
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
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/playlists?id=$playListId&limit=40")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Log.d("SAAVN_RAW", responseBody)
                    parseNewSongsJson(responseBody,root,targetList,adapter)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun fetchPlaylistsByID(playListId: String,root: String,targetList: MutableList<SongItem>, adapter: NewSongAdapter) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/playlists?id=$playListId&limit=40")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Log.d("SAAVN_RAW", responseBody)
                    parseNewSongsJson(responseBody,root,targetList,adapter)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun fetchAlbumByQuery(query: String, root: String, targetList: MutableList<DataItem>, adapter: AlbumAdapter) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/albums?query=${query}&limit=30")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Log.d("SAAVN_RAW", responseBody)
                    parseAlbumListJson(responseBody,root,targetList,adapter)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun fetchArtistsByQuery(query: String, root: String, targetList: MutableList<Artists>, adapter: ArtistsAdapter) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/artists?query=${query}&limit=20")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    parseArtistsJson(responseBody,root,targetList,adapter)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun fetchPlayListByQuery(query: String, root: String, targetList: MutableList<DataItem>, adapter: PlayListAdapter) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/playlists?query=${query}&limit=20")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Log.d("SAAVN_RAW", responseBody)
                    parsePlaylistJson(responseBody,root,targetList,adapter)
                } else {
                    Log.e("SAAVN", "Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
    }
    fun parseNewSongsJson(jsonString: String,root: String,targetList: MutableList<SongItem>, adapter: SongAdapter) {
        val parsedSongs = mutableListOf<SongItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

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
            val downloadUrl = if (downloadArray != null && downloadArray.length() > 0) {
                downloadArray.getJSONObject(2).optString("url")
            } else ""

            val artistsObj = song.optJSONObject("artists")
            val primaryArtists = artistsObj?.optJSONArray("primary")
            val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                primaryArtists.getJSONObject(0).optString("name")
            } else ""

            parsedSongs.add(SongItem(id, name, artistName.toString(), image,duration,downloadUrl))
        }

        activity?.runOnUiThread {
            targetList.addAll(parsedSongs)
            Log.d("Song", "Parsed ${parsedSongs.size} songs: $parsedSongs")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    fun parseNewSongsJson(jsonString: String,root: String,targetList: MutableList<SongItem>, adapter: NewSongAdapter) {
        val parsedSongs = mutableListOf<SongItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

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
            val downloadUrl = if (downloadArray != null && downloadArray.length() > 0) {
                    downloadArray.getJSONObject(2).optString("url")
            } else ""

            val artistsObj = song.optJSONObject("artists")
            val primaryArtists = artistsObj?.optJSONArray("primary")
            val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
                primaryArtists.getJSONObject(0).optString("name")
            } else ""

            parsedSongs.add(SongItem(id, name, artistName, image,duration,downloadUrl))
        }

        activity?.runOnUiThread {
            targetList.addAll(parsedSongs)
            Log.d("Song", "Parsed ${parsedSongs.size} songs: $parsedSongs")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    fun parseArtistsJson(jsonString: String, root: String, targetList: MutableList<Artists>, adapter: ArtistsAdapter) {
        val parsedArtist = mutableListOf<Artists>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

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
                imageArray.getJSONObject(1).optString("url")

            } else ""

            parsedArtist.add(Artists(id, name, role, imageUrl,type))
        }
        activity?.runOnUiThread {
            targetList.addAll(parsedArtist)
            Log.d("Artists", "Parsed ${parsedArtist.size} artists : $parsedArtist")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    fun parsePlaylistJson(jsonString: String, root: String, targetList: MutableList<DataItem>, adapter: PlayListAdapter) {
        val parsedPlaylist = mutableListOf<DataItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

        val data = json.getJSONObject("data")
        val songsArray = data.getJSONArray(root)
        Log.d("Song", "Songs array length: ${songsArray.length()}")

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

            parsedPlaylist.add(DataItem(id, name, artistName, imageUrl))
        }

        activity?.runOnUiThread {
            targetList.addAll(parsedPlaylist)
            Log.d("Playlists", "Parsed ${parsedPlaylist.size} Playlists : $parsedPlaylist")
            adapter.notifyDataSetChanged()
            onDataLoaded()
        }
    }
    fun parseAlbumListJson(jsonString: String, root: String, targetList: MutableList<DataItem>, adapter: AlbumAdapter) {
        val parsedAlbum = mutableListOf<DataItem>()
        val json = JSONObject(jsonString)
        val success = json.optBoolean("success", false)
        if (!success) return

        val data = json.getJSONObject("data")
        val songsArray = data.getJSONArray(root)
        Log.d("Song", "Songs array length: ${songsArray.length()}")

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

            parsedAlbum.add(DataItem(id, name, artistName, imageUrl))
        }

        activity?.runOnUiThread {
            targetList.addAll(parsedAlbum)
            Log.d("Album", "Parsed ${parsedAlbum.size} albums : $parsedAlbum")
            adapter.notifyDataSetChanged()
            onDataLoaded()
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
    private fun onDataLoaded() {
        completedRequests++
        if (completedRequests >= totalRequests) {
            activity?.runOnUiThread {
                shimmerFrameLayout.stopShimmer()
                shimmerFrameLayout.visibility = View.GONE
                binding.constraintLayout.visibility = View.VISIBLE
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
}