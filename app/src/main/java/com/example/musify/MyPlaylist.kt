package com.example.musify

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.text.Html
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.musify.databinding.FragmentMyPlaylistBinding
import com.example.musify.service.MusicPlayerService
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class MyPlaylist : Fragment() {
    private lateinit var binding: FragmentMyPlaylistBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var musicPlayerService: MusicPlayerService ?= null
    private var bound = false
    private lateinit var miniPlayer: View
    private lateinit var songName: TextView
    private lateinit var artistName: TextView
    private lateinit var playPauseButton: AppCompatImageView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var nextButton: AppCompatImageView
    private lateinit var prevButton: AppCompatImageView
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var bottomSheetDialog0: BottomSheetDialog
    private lateinit var playlistAdapter: MyPlaylistAdapter
    private var progressDialog: AlertDialog? = null
    private lateinit var apiUrl: String
    private var progressTextView: TextView? = null
    private var dotTimer: CountDownTimer? = null
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
                    val scale = resources.displayMetrics.density
                    val paddingInPx = (paddingInDp * scale).toInt()
                    binding.myPlayListRecyclerView.setPadding(0,0,0,paddingInPx)
                } else {
                    if (miniPlayer.visibility != View.VISIBLE) {
                        // Prepare for animation
                        miniPlayer.translationY = miniPlayer.height.toFloat()
                        miniPlayer.alpha = 0f
                        miniPlayer.visibility = View.VISIBLE

                        val paddingInDp = 90
                        val scale = resources.displayMetrics.density
                        val paddingInPx = (paddingInDp * scale).toInt()
                        binding.myPlayListRecyclerView.setPadding(0,0,0,paddingInPx)

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
        binding = FragmentMyPlaylistBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiUrl = requireContext().getString(R.string.Spotify_Url)

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

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference().child("Users")

        val userID = auth.currentUser?.uid
        if (userID != null) {
            val favRef = database.child(userID).child("Favourites").child("Songs")
            favRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        var totalSongs = 0
                        for (songSnap in snapshot.children) {
                            val songID  = songSnap.child("id").getValue(String::class.java)
                            if (songID != null) totalSongs++
                        }

                        "$totalSongs Songs".also { binding.itemNameTextView.text = it }
                    }
                }

                override fun onCancelled(error: DatabaseError) { }
            })
        }

        bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.playlist_bottomsheet,null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        view.scaleY = 0.8f
        view.animate()
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(400)
            .start()

        bottomSheetDialog0 = BottomSheetDialog(requireContext())
        val view0 = layoutInflater.inflate(R.layout.import_bottomsheet,null)
        bottomSheetDialog0.setContentView(view0)
        bottomSheetDialog0.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        bottomSheetDialog0.setOnShowListener {
            val bottomSheet = bottomSheetDialog0.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        view0.scaleY = 0.8f
        view0.animate()
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(400)
            .start()

        binding.plusIcon.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(),view, Gravity.END,0,R.style.CustomPopupThemeOverlay)
            popup.menuInflater.inflate(R.menu.plus_menu, popup.menu)

            val typeface = ResourcesCompat.getFont(
                requireContext(),
                R.font.merriweathersans_regular
            )!!

            val textSizeSp = 15
            val textColor = ContextCompat.getColor(requireContext(), R.color.white)

            for (i in 0 until popup.menu.size) {
                val item = popup.menu[i]
                val title = SpannableString(item.title)
                title.setSpan(CustomTypefaceSpan(typeface), 0, title.length, 0)
                title.setSpan(AbsoluteSizeSpan(textSizeSp, true), 0, title.length, 0)
                title.setSpan(ForegroundColorSpan(textColor), 0, title.length, 0)

                item.title = title
            }

            popup.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    R.id.action_add -> {
                        importPlaylist()
                        bottomSheetDialog0.show()
                        true
                    }
                    R.id.action_create -> {
                        createPlaylist()
                        bottomSheetDialog.show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        loadPlaylists()

        binding.materialCardView3.setOnClickListener {
            val intent = Intent(requireContext(), MyPlaylistActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("name","Favourites")
            startActivity(intent)
        }
    }
    private fun importPlaylist() {
        val editText0 = bottomSheetDialog0.findViewById<EditText>(R.id.playlistEditText0)
        val importButton = bottomSheetDialog0.findViewById<MaterialButton>(R.id.importPlaylistBtn)

        importButton?.setOnClickListener {
            val url = editText0?.text.toString().trim()
            if (url.isEmpty()) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText0?.windowToken, 0)
                Toast.makeText(requireContext(),"Enter PlayList URL",Toast.LENGTH_SHORT).show()
            } else {
                val trimUrl = url.substringAfter("playlist/").substringBefore("?")
                importPlaylistByUrl(trimUrl)
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText0?.windowToken, 0)
                showProgressDialog()
            }
        }
    }
    private fun importPlaylistByUrl(url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$apiUrl/$url")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }

                    response.body.string()
                }

                if (responseBody.isEmpty()) {
                    showToast("Empty response from server")
                    hideLoading()
                    return@launch
                }

                withContext(Dispatchers.Default) {
                    parsePlaylistData(responseBody)
                }

            } catch (e: SocketTimeoutException) {
                hideLoading()
                showToast("Request timed out. Please try again.")

            } catch (e: IOException) {
                hideLoading()
                showToast("Playlist not available")

            } catch (e: Exception) {
                hideLoading()
                showToast("Something went wrong")
                Log.e("SAAVN", "Error importing playlist", e)
            }
        }
    }
    private fun parsePlaylistData(jsonString: String) {
        val songList = ArrayList<SongData>()

        val json = JSONObject(jsonString)
        val success = json.optBoolean("success",false)
        if (!success) return

        val playListObject = json.getJSONObject("data")
        val name = playListObject.optString("name").trim()
        val image = playListObject.optString("image")
        val songsArray = playListObject.optJSONArray("songs")
        if (songsArray != null) {
            for (song in 0 until songsArray.length()) {
                val songObject = songsArray.optJSONObject(song)
                val songId = songObject?.optString("id")
                val songName = songObject?.optString("name")
                val songData = SongData(
                    id = songId ?: "",
                    name = songName ?: ""
                )
                songList.add(songData)
            }
        }
        Log.d("playlistData",songList.toString())
        val userID = auth.currentUser?.uid
        if (userID != null) {
            val playListRef = database.child(userID).child("Favourites").child("MyPlaylist")
            playListRef.child(name).get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val data = mapOf(
                        "playList Name" to name,
                        "total Songs" to songList.size,
                        "imageUrl" to image,
                        "Songs" to songList
                    )
                    playListRef.child(name).setValue(data).addOnSuccessListener {
                        Toast.makeText(requireContext(),"PlayList Import Successfully",Toast.LENGTH_SHORT).show()
                        hideLoading()
                        loadPlaylists()
                    }.addOnFailureListener {
                        Toast.makeText(requireContext(),"Failed to Import PlayList",Toast.LENGTH_SHORT).show()
                        hideLoading()
                    }
                } else {
                    Toast.makeText(requireContext(),"PlayList Already Exist",Toast.LENGTH_SHORT).show()
                    hideLoading()
                }
            }
        }
    }
    private fun createPlaylist() {
        val editText = bottomSheetDialog.findViewById<EditText>(R.id.playlistEditText)
        val createButton = bottomSheetDialog.findViewById<MaterialButton>(R.id.createPlaylistBtn)

        val userID = auth.currentUser?.uid

        createButton?.setOnClickListener {
            val name = editText?.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(),"Enter PlayList Name",
                    Toast.LENGTH_SHORT).show()
            } else {
                if (userID != null) {
                    val playListRef = database.child(userID).child("Favourites").child("MyPlaylist")
                    playListRef.child(name).get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            val data = mapOf(
                                "playList Name" to name,
                                "total Songs" to 0
                            )
                            playListRef.child(name).setValue(data).addOnSuccessListener {
                                Toast.makeText(requireContext(),"PlayList Created Successfully",
                                    Toast.LENGTH_SHORT).show()
                                loadPlaylists()
                            }.addOnFailureListener {
                                Toast.makeText(requireContext(),"Failed to Create PlayList",
                                    Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(requireContext(),"PlayList Already Exist",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText?.windowToken, 0)
        }
    }
    private fun loadPlaylists() {
        val userID = auth.currentUser?.uid ?: return
        val playListRef = database.child(userID).child("Favourites").child("MyPlaylist")
        val playlistList = mutableListOf<PlaylistData>()

        binding.myPlayListRecyclerView.fadeOut()
        binding.progressBar.fadeIn()

        playListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                playlistList.clear()

                if (snapshot.exists()) {
                    for (playListSnapShot in snapshot.children) {
                        val name = playListSnapShot.child("playList Name").getValue(String::class.java) ?: ""
                        val totalSongs = playListSnapShot.child("total Songs").getValue(Int::class.java) ?: 0
                        val image = playListSnapShot.child("imageUrl").getValue(String::class.java) ?: ""
                        playlistList.add(PlaylistData(name,image,totalSongs))
                    }

                    playlistAdapter = MyPlaylistAdapter(playlistList,
                        onRenameClick = { playlistData ->
                            showRenameDialog(playlistData)
                        },
                        onRemoveClick = { playlistData ->
                            removePlaylist(playlistData)
                        }
                    )
                    binding.myPlayListRecyclerView.layoutManager =
                        LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
                    binding.myPlayListRecyclerView.adapter = playlistAdapter
                    playlistAdapter.notifyDataSetChanged()

                    playlistAdapter.setOnItemClickListener(object : MyPlaylistAdapter.OnItemClickListener {
                        override fun onItemClick(position: Int) {
                            val intent = Intent(requireContext(), MyPlaylistActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            intent.putExtra("name",playlistList[position].name)
                            startActivity(intent)
                        }
                    })

                    binding.progressBar.fadeOut()
                    binding.myPlayListRecyclerView.fadeIn()
                } else {
                    binding.progressBar.fadeOut()
                    binding.myPlayListRecyclerView.fadeOut()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.fadeOut()
                Toast.makeText(requireContext(), "Failed to load Playlists", Toast.LENGTH_SHORT).show()
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
    private fun showRenameDialog(item: PlaylistData) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.playlist_rename_bottomsheet,null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        view.scaleY = 0.8f
        view.animate()
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(400)
            .start()

        val editText = bottomSheetDialog.findViewById<EditText>(R.id.playlistEditText)
        val createButton = bottomSheetDialog.findViewById<MaterialButton>(R.id.createPlaylistBtn)

        editText?.setText(item.name)

        bottomSheetDialog.show()

        createButton?.setOnClickListener {
            val userID = auth.currentUser?.uid
            val name = editText?.text.toString().trim()
            val oldName = item.name

            if (name.isEmpty()) {
                Toast.makeText(requireContext(),"Enter PlayList Name",
                    Toast.LENGTH_SHORT).show()
            } else {
                if (userID != null) {
                    val playListRef = database.child(userID).child("Favourites").child("MyPlaylist")
                    val oldPlaylistRef = playListRef.child(oldName)
                    val newPlaylistRef = playListRef.child(name)

                    if (oldName == name) {
                        Toast.makeText(requireContext(), "No changes made", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    newPlaylistRef.get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            Toast.makeText(requireContext(), "Playlist already exists", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        oldPlaylistRef.get().addOnSuccessListener { oldSnapshot ->
                            if (!oldSnapshot.exists()) {
                                Toast.makeText(requireContext(), "Playlist does not exist", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val imageUrl = oldSnapshot.child("imageUrl").getValue(String::class.java) ?: ""
                            val totalSongs = oldSnapshot.child("total Songs").getValue(Int::class.java) ?: 0
                            val songsData = oldSnapshot.child("Songs").value

                            val data = mutableMapOf<String, Any>(
                                "imageUrl" to imageUrl,
                                "playList Name" to name,
                                "total Songs" to totalSongs
                            )

                            songsData?.let { data["Songs"] = it }

                            // Write new playlist first
                            newPlaylistRef.setValue(data).addOnSuccessListener {
                                // Then remove the old one
                                oldPlaylistRef.removeValue().addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Playlist renamed successfully", Toast.LENGTH_SHORT).show()
                                    loadPlaylists()
                                }.addOnFailureListener {
                                    Toast.makeText(requireContext(), "Failed to delete old playlist", Toast.LENGTH_SHORT).show()
                                }
                            }.addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to rename playlist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText?.windowToken, 0)
        }
    }
    private fun removePlaylist(item: PlaylistData) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.custom_dialog,null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.btnRemove).setOnClickListener {
            val userID = auth.currentUser?.uid
            val playListRef = database.child(userID.toString()).child("Favourites").child("MyPlaylist")
            playListRef.child(item.name).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    playListRef.child(item.name).removeValue()
                    Toast.makeText(requireContext(), "Playlist Removed Successfully", Toast.LENGTH_SHORT).show()
                    loadPlaylists()
                } else {
                    Toast.makeText(requireContext(), "Failed to Remove Playlist", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        dialogView.findViewById<TextView>(R.id.btnCancle).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onResume() {
        loadPlaylists()
        super.onResume()
    }
    private fun showProgressDialog() {
        bottomSheetDialog0.dismiss()
        val dialogView = layoutInflater.inflate(R.layout.import_progress, null)
        progressTextView = dialogView.findViewById(R.id.progressText)

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setCancelable(false)

        progressDialog = builder.create()
        progressDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        progressDialog?.show()
        startDotAnimation()
    }
    private fun startDotAnimation() {
        var dots = 0
        dotTimer?.cancel()
        dotTimer = object : CountDownTimer(Long.MAX_VALUE, 500) {
            override fun onTick(millisUntilFinished: Long) {
                dots = (dots + 1) % 4
                progressTextView?.text = "Importing" + ".".repeat(dots)
            }

            override fun onFinish() {}
        }.start()
    }
    private fun hideLoading() {
        dotTimer?.cancel()
        dotTimer = null
        progressDialog?.dismiss()
    }
    private fun showToast(message: String) {
        if (isAdded) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}