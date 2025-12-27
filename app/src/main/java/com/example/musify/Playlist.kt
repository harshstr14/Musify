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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.databinding.FragmentPlaylistBinding
import com.example.musify.service.MusicPlayerService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Playlist : Fragment() {
    private lateinit var binding: FragmentPlaylistBinding
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
                    val paddingBottomInDp = 12
                    val paddingStartInDp = 25
                    val scale = resources.displayMetrics.density
                    val paddingStartInPx = (paddingStartInDp * scale).toInt()
                    val paddingBottomInPx = (paddingBottomInDp * scale).toInt()
                    binding.forYouRecyclerView.setPadding(paddingStartInPx,0,0,paddingBottomInPx)
                } else {
                    if (miniPlayer.visibility != View.VISIBLE) {
                        // Prepare for animation
                        miniPlayer.translationY = miniPlayer.height.toFloat()
                        miniPlayer.alpha = 0f
                        miniPlayer.visibility = View.VISIBLE

                        val paddingInDp = 90
                        val paddingStartInDp = 25
                        val scale = resources.displayMetrics.density
                        val paddingStartInPx = (paddingStartInDp * scale).toInt()
                        val paddingInPx = (paddingInDp * scale).toInt()
                        binding.forYouRecyclerView.setPadding(paddingStartInPx,0,0,paddingInPx)

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPlaylistBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiUrl = BuildConfig.API_BASE_URL

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference().child("Users")

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

        binding.progressBar.fadeIn()
        binding.forYouRecyclerView.fadeOut()

        val browseCategoryList = listOf(
            "For You","Hindi","English","Punjabi","Rajasthani","Haryanvi","Telugu","Marathi","Gujarati"
        )

        val browseCategoryAdapter = SearchCategoryAdapter(browseCategoryList){ position ->
            when (val browseCategory = browseCategoryList[position]) {
                "For You" -> {
                    fetchPlaylistsDataByQuery("top")
                }
                "Hindi" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "English" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "Punjabi" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "Rajasthani" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "Haryanvi" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "Telugu" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "Marathi" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
                "Gujarati" -> {
                    fetchPlaylistsDataByQuery(browseCategory)
                }
            }
        }

        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)
        binding.categoryRecyclerView.adapter = browseCategoryAdapter

        fetchPlaylistsDataByQuery("top")
    }
    private fun fetchPlaylistsDataByQuery(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/search/playlists?query=${query}&limit=40")
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
        val playlistList = mutableListOf<DataItem>()

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
                    imageArray.getJSONObject(2).optString("url")

                } else ""

                playlistList.add(DataItem(id, name, "", imageUrl))
            }
        }

        withContext(Dispatchers.Main) {
            val playListAdapter = PlayListAdapter(playlistList)
            binding.forYouRecyclerView.layoutManager = GridLayoutManager(requireContext(),3,
                GridLayoutManager.VERTICAL,false)
            binding.forYouRecyclerView.adapter = playListAdapter

            binding.progressBar.fadeOut()
            binding.forYouRecyclerView.fadeIn()

            playListAdapter.setOnItemClickListener(object : PlayListAdapter.OnItemClickListener {
                override fun omItemClick(position: Int) {
                    val intent = Intent(requireContext(), PlaylistActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    intent.putExtra("id",playlistList[position].id)
                    startActivity(intent)
                }
            })
        }
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