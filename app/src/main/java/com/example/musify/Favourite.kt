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
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.musify.databinding.FragmentFavouriteBinding
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

class Favourite : Fragment() {
    private lateinit var binding: FragmentFavouriteBinding
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
        val intent = Intent(requireContext(), MusicPlayerService::class.java)
        requireContext().bindService(intent,connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            requireContext().unbindService(connection)
            bound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentFavouriteBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        database = FirebaseDatabase.getInstance().getReference().child("Users").child(userID!!).child("Favourites")

        val favRef = FirebaseDatabase.getInstance().getReference().child("Users").child(userID).child("Favourites")
            .child("Songs")

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
                if (musicPlayerService != null) {
                    musicPlayerService?.setPlaylist(songList,position)
                    val intent = Intent(requireContext(), MusicPlayerService::class.java)
                    requireContext().startService(intent)
                }
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

        loadFavouriteData()
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
                        if (responseBody .isNotEmpty()) {
                            val songItem = parseSongJson(responseBody)
                            if (songItem != null) {
                                tempList.add(songItem)
                            }
                        }
                    } else {
                        Log.e("SAAVN", "Error: ${response.code}")
                    }
                }

                requireActivity().runOnUiThread {
                    if (isAdded && view != null) {
                        binding.likedSongRecyclerView.post {
                            songList.clear()
                            songList.addAll(tempList)
                            songAdapter.notifyDataSetChanged()

                            val userID = auth.currentUser?.uid
                            val favSongRef = FirebaseDatabase.getInstance().getReference().child("Users").child(userID!!).child("Favourites")
                                .child("Songs")

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
                            onDataLoaded("songs")
                            updateCategoryVisibility()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
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
        val downloadUrl = if (downloadArray != null && downloadArray.length() > 0) {
            downloadArray.getJSONObject(2).optString("url")
        } else ""

        val artistsObj = songObject.optJSONObject("artists")
        val primaryArtists = artistsObj?.optJSONArray("primary")
        val artistName = if (primaryArtists != null && primaryArtists.length() > 0) {
            primaryArtists.getJSONObject(0).optString("name")
        } else ""

        return SongItem(id, name, artistName, image, duration, downloadUrl)
    }
    fun fetchArtistsByIDs(artistsID: List<String>) {
        Thread {
            val client = OkHttpClient()
            val tempList = mutableListOf<Artists>()

            try {
                for (artistID in artistsID) {
                    val request = Request.Builder()
                        .url("https://jiosaavn-api-stableone.vercel.app/api/artists/${artistID}")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        if (responseBody .isNotEmpty()) {
                            val artistItem = parseArtistJson(responseBody)
                            if (artistItem != null) {
                                tempList.add(artistItem)
                            }
                        }
                    } else {
                        Log.e("SAAVN", "Error: ${response.code}")
                    }
                }

                requireActivity().runOnUiThread {
                    if (isAdded && view != null) {
                        artistsList.clear()
                        artistsList.addAll(tempList)
                        artistsAdapter.notifyDataSetChanged()
                        onDataLoaded("artists")
                        updateCategoryVisibility()
                    }
                }

            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
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
    fun fetchAlbumsByIDs(albumsID: List<String>) {
        Thread {
            val client = OkHttpClient()
            val tempList = mutableListOf<DataItem>()

            try {
                for (albumID in albumsID) {
                    val request = Request.Builder()
                        .url("https://jiosaavn-api-stableone.vercel.app/api/albums?id=${albumID}")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        if (responseBody .isNotEmpty()) {
                            val albumItem = parseAlbumJson(responseBody)
                            if (albumItem != null) {
                                tempList.add(albumItem)
                            }
                        }
                    } else {
                        Log.e("SAAVN", "Error: ${response.code}")
                    }
                }

                requireActivity().runOnUiThread {
                    if (isAdded && view != null) {
                        albumsList.clear()
                        albumsList.addAll(tempList)
                        albumAdapter.notifyDataSetChanged()
                        onDataLoaded("albums")
                        updateCategoryVisibility()
                    }
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
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
    private fun updateMiniPlayer(songItem: SongItem?) {
        songName.text = Html.fromHtml(songItem?.name ?: "", Html.FROM_HTML_MODE_LEGACY)
        artistName.text = songItem?.artist
        Picasso.get().load(songItem?.image[1]?.url).into(songImage)
        setDynamicBackground(songItem?.image[1]?.url ?: "" ,songImage,background)

    }
    fun fetchPlaylistsByIDs(playlistsID: List<String>) {
        Thread {
            val client = OkHttpClient()
            val tempList = mutableListOf<DataItem>()

            try {
                for (playlistID in playlistsID) {
                    val request = Request.Builder()
                        .url("https://jiosaavn-api-stableone.vercel.app/api/playlists?id=${playlistID}")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        if (responseBody .isNotEmpty()) {
                            val playlistItem = parsePlaylistJson(responseBody)
                            if (playlistItem != null) {
                                tempList.add(playlistItem)
                            }
                        }
                    } else {
                        Log.e("SAAVN", "Error: ${response.code}")
                    }
                }

                requireActivity().runOnUiThread {
                    if (isAdded && view != null) {
                        playlistsList.clear()
                        playlistsList.addAll(tempList)
                        playListAdapter.notifyDataSetChanged()
                        onDataLoaded("playlists")
                        updateCategoryVisibility()
                    }
                }
            } catch (e: Exception) {
                Log.e("SAAVN", "Exception: ${e.message}")
            }
        }.start()
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
                    transition: Transition<in Bitmap>?
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
    private fun onDataLoaded(category: String) {
        when(category) {
            "songs" -> songsLoaded = true
            "artists" -> artistsLoaded = true
            "albums" -> albumsLoaded = true
            "playlists" -> playlistsLoaded = true
        }

        activity?.runOnUiThread {
            updateCategoryVisibility()
        }
    }
    private fun updateCategoryVisibility() {
        if (!isAdded) return

        when (category) {
            "songs" -> {
                binding.likedSongRecyclerView.post {
                    if (songsLoaded && songList.isNotEmpty()) binding.likedSongRecyclerView.fadeIn()
                    else if (!songsLoaded) binding.likedSongRecyclerView.fadeIn()
                    binding.artistsRecyclerView.fadeOut()
                    binding.albumsRecyclerView.fadeOut()
                    binding.playListRecyclerView.fadeOut()
                    if (songList.isEmpty()) {
                        binding.noFavText.fadeIn()
                    } else {
                        binding.noFavText.fadeOut()
                    }
                    if (!songsLoaded) {
                        binding.progressBar.fadeIn()
                    } else {
                        binding.progressBar.fadeOut()
                    }
                }
            }

            "artists" -> {
                if (artistsLoaded && albumsList.isNotEmpty()) binding.artistsRecyclerView.fadeIn()
                else if (!artistsLoaded) binding.artistsRecyclerView.fadeIn()
                binding.likedSongRecyclerView.fadeOut()
                binding.albumsRecyclerView.fadeOut()
                binding.playListRecyclerView.fadeOut()
                if (artistsList.isEmpty()) {
                    binding.noFavText.fadeIn()
                } else {
                    binding.noFavText.fadeOut()
                }
                if (!artistsLoaded) {
                    binding.progressBar.fadeIn()
                } else {
                    binding.progressBar.fadeOut()
                }
            }

            "albums" -> {
                if (albumsLoaded && albumsList.isNotEmpty()) binding.albumsRecyclerView.fadeIn()
                else if (!albumsLoaded) binding.albumsRecyclerView.fadeIn()
                binding.artistsRecyclerView.fadeOut()
                binding.likedSongRecyclerView.fadeOut()
                binding.playListRecyclerView.fadeOut()
                if (albumsList.isEmpty()) {
                    binding.noFavText.fadeIn()
                } else {
                    binding.noFavText.fadeOut()
                }
                if (!albumsLoaded) {
                    binding.progressBar.fadeIn()
                } else {
                    binding.progressBar.fadeOut()
                }
            }

            "playlists" -> {
                if (playlistsLoaded && playlistsList.isNotEmpty()) binding.playListRecyclerView.fadeIn()
                else if (!playlistsLoaded) binding.playListRecyclerView.fadeIn()
                binding.artistsRecyclerView.fadeOut()
                binding.albumsRecyclerView.fadeOut()
                binding.likedSongRecyclerView.fadeOut()
                if (playlistsList.isEmpty()) {
                    binding.noFavText.fadeIn()
                } else {
                    binding.noFavText.fadeOut()
                }
                if (!playlistsLoaded) {
                    binding.progressBar.fadeIn()
                } else {
                    binding.progressBar.fadeOut()
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
    private fun loadFavouriteData() {
        val userID = auth.currentUser?.uid
        if (userID != null) {
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    binding.progressBar.fadeIn()
                    binding.noFavText.fadeOut()
                    //updateCategoryVisibility()

                    if (snapshot.exists()) {
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
                        }

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
                        }

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
                        }

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
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FAV", "Error loading favourites", error.toException())
                }
            })
        }
    }
}