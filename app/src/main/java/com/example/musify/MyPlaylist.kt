package com.example.musify

import android.app.AlertDialog
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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
import com.squareup.picasso.Picasso

class MyPlaylist : Fragment() {
    private lateinit var binding: FragmentMyPlaylistBinding
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
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var playlistAdapter: MyPlaylistAdapter

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
        binding = FragmentMyPlaylistBinding.inflate(inflater,container,false)
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

        val editText = bottomSheetDialog.findViewById<EditText>(R.id.playlistEditText)
        val createButton = bottomSheetDialog.findViewById<MaterialButton>(R.id.createPlaylistBtn)

        binding.plusIcon.setOnClickListener {
            bottomSheetDialog.show()
        }

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
        }

        loadPlaylists()

        binding.materialCardView3.setOnClickListener {
            val intent = Intent(requireContext(), MyPlaylistActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("name","Favourites")
            startActivity(intent)
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
                        playlistList.add(PlaylistData(name, totalSongs))
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
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {

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
                            setGradientCenter(0.5f, 0.3f) // position glow (center/top)
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

                            val totalSongs = oldSnapshot.child("total Songs").getValue(Int::class.java) ?: 0
                            val songsData = oldSnapshot.child("Songs").value

                            val data = mutableMapOf<String, Any>(
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
}