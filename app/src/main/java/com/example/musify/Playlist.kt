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
import com.example.musify.databinding.FragmentPlaylistBinding
import com.example.musify.service.MusicPlayerService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class Playlist : Fragment() {
    private lateinit var binding: FragmentPlaylistBinding
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
        binding = FragmentPlaylistBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference().child("Users")

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
        binding.forYouRecyclerView.fadeOut()

        val browseCategoryList = listOf(
            "For You","Hindi","English","Punjabi","Rajasthani","Haryanvi","Telugu","Marathi","Gujarati"
        )

        val browseCategoryAdapter = SearchCategoryAdapter(browseCategoryList){ position ->
            val browseCategory = browseCategoryList[position]

            when (browseCategory) {
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
    fun fetchPlaylistsDataByQuery(query: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://jiosaavn-api-stableone.vercel.app/api/search/playlists?query=${query}&limit=40")
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
        val playlistList = mutableListOf<DataItem>()
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

            playlistList.add(DataItem(id, name, "", imageUrl))
        }

        activity?.runOnUiThread {
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
    fun setDynamicBackground(imageUrl: String, imageView: AppCompatImageView, backgroundView: AppCompatImageView) {
        Glide.with(imageView.context)
            .asBitmap()
            .load(imageUrl)
            .override(500, 500)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
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
    fun Int.adjustAlpha(factor: Float): Int {
        val alpha = (Color.alpha(this) * factor).toInt()
        val red = Color.red(this)
        val green = Color.green(this)
        val blue = Color.blue(this)
        return Color.argb(alpha, red, green, blue)
    }
}