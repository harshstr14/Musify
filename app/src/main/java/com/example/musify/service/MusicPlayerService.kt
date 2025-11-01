package com.example.musify.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.Html
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.exoplayer.ExoPlayer
import com.example.musify.Home
import com.example.musify.PlaySong
import com.example.musify.R
import com.example.musify.SongItem
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerService : LifecycleService() {
    companion object {
        const val CHANNEL_ID = "music_player_channel"
        const val NOTIFICATION_ID = 1

        // Action strings for notification intents
        const val ACTION_PLAY_NEW = "com.example.app.action.PLAY_NEW"
        const val ACTION_PLAY = "com.example.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.app.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.app.ACTION_NEXT"
        const val ACTION_PREV = "com.example.app.ACTION_PREV"
        const val ACTION_REPEAT = "com.example.app.ACTION_REPEAT"
        const val ACTION_SHUFFLE = "com.example.app.ACTION_SHUFFLE"
        const val ACTION_STOP = "com.example.app.ACTION_STOP"
    }
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    // LiveData to share with UI
    val currentSongLive = MutableLiveData<SongItem?>(null)
    val isPlayingLive = MutableLiveData(false)
    val progressLive = MutableLiveData(0)
    val durationLive = MutableLiveData(0)
    private val handler = Handler(Looper.getMainLooper())
    private val progressRefreshMs = 500L
    val isShuffle = MutableLiveData(false)
    val repeatMode = MutableLiveData(false)
    private var currentAlbumArt: Bitmap? = null
    private var currentArtSongId: String? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                updatePlaybackInfo()
                handler.postDelayed(this, progressRefreshMs)
            }
        }
    }
    private var playlist: MutableList<SongItem> = mutableListOf()
    private var currentIndex = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val placeholderNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Musify is starting…")
            .setContentText("Preparing your music")
            .setSmallIcon(R.drawable.headset_image)
            .build()
        startForeground(NOTIFICATION_ID, placeholderNotification)

        initPlayer()
        initMediaSession()
    }
    private fun initPlayer() {
        player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)

        player.addListener(object : Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && currentIndex in playlist.indices) {
                    val song = playlist[currentIndex]
                    val duration = player.duration.coerceAtLeast(song.duration.toLong()) // Use actual player duration if available

                    durationLive.postValue(duration.toInt())

                    val bitmap = BitmapFactory.decodeResource(resources, R.drawable.playlist)
                    updateMetadata(song, bitmap)
                    CoroutineScope(Dispatchers.Main).launch {
                        updateNotification()
                    }
                }
                else if (state == Player.STATE_ENDED) {
                    next()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingLive.postValue(isPlaying)
                if (isPlaying) handler.post(progressRunnable) else handler.removeCallbacks(progressRunnable)
                updatePlaybackState()
                CoroutineScope(Dispatchers.Main).launch {
                    updateNotification()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicPlayerService", "Player error: ${error.message}")
            }
        })
    }
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayerService")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { resume() }
            override fun onPause() { pause() }
            override fun onSkipToNext() { next() }
            override fun onSkipToPrevious() { previous() }
            override fun onStop() { stopServiceAndNotification() }
            override fun onSeekTo(pos: Long) { seekTo(pos) }
        })
        mediaSession.isActive = true
        updatePlaybackState()
    }
    private fun updatePlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
        val state = if (::player.isInitialized && player.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        stateBuilder.setState(state, player.currentPosition, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_NEW -> {
                    val playlist: ArrayList<SongItem>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra("playlist", SongItem::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra("playlist")
                    }

                    val index = intent.getIntExtra("index", 0)
                    if (!playlist.isNullOrEmpty() && index in playlist.indices) {
                        setPlaylist(playlist, index)
                        // Only start foreground if a song is available
                        currentSongLive.value?.let { song ->
                            startForegroundWithNotification(song)
                        }
                    }
                }
                ACTION_PLAY -> resume()
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> next()
                ACTION_PREV -> previous()
                ACTION_SHUFFLE -> shuffleToggle()
                ACTION_REPEAT -> repeatToggle()
                ACTION_STOP -> stopServiceAndNotification()
            }
        }

        // If the player is not initialized yet, start with a placeholder notification
        if (!::player.isInitialized) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Musify is running…")
                .setContentText("Preparing your music")
                .setSmallIcon(R.drawable.headset_image)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }
    fun setPlaylist(songs: ArrayList<SongItem>?, startAtIndex: Int = 0) {
        playlist.clear(); playlist.addAll(songs!!)
        if (startAtIndex in playlist.indices) playIndex(startAtIndex)
    }
    fun play(song: SongItem) {
        val idx = playlist.indexOfFirst { it.id == song.id }
        if (idx >= 0) {
            playIndex(idx)
        } else {
            playlist.add(song)
            playIndex(playlist.lastIndex)
        }
    }
    private fun playIndex(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val song = playlist[index]
        currentSongLive.postValue(song)
        prepareAndPlay(song)
        Home.RecentlyPlayedManager.addToRecentlyPlayed(this,playlist[index])
    }
    private fun prepareAndPlay(song: SongItem) {
        currentAlbumArt = null
        currentArtSongId = null

        player.stop()
        player.clearMediaItems()
        val mediaItem = MediaItem.fromUri(song.downloadUrl[4].url.toUri())
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        startForegroundWithNotification(song)
        updatePlaybackState()
    }
    fun getCurrentPosition(): Int {
        return player.currentPosition.toInt()
    }
    fun getDuration(): Int {
        return player.duration.toInt()
    }
    fun pause() {
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
            isPlayingLive.postValue(false)
        }
    }
    fun resume() {
        if (::player.isInitialized) player.play()
        isPlayingLive.postValue(true)
    }
    fun seekTo(positionMs: Long) {
        if (::player.isInitialized) player.seekTo(positionMs)
    }

    fun next() {
        if (playlist.isEmpty()) return

        when {
            repeatMode.value == true -> {
                // Repeat current song
                playIndex(currentIndex)
            }
            isShuffle.value == true -> {
                // Pick a random song
                val randomIndex = (playlist.indices).random()
                playIndex(randomIndex)
            }
            else -> {
                // Normal next
                val nextIndex = currentIndex + 1
                if (nextIndex in playlist.indices) {
                    playIndex(nextIndex)
                } else {
                    // reached end → stop or repeat whole playlist
                    playIndex(0)
                }
            }
        }
    }
    fun previous() {
        if (playlist.isEmpty()) return

        when {
            repeatMode.value == true -> {
                // Repeat current song
                playIndex(currentIndex)
            }
            isShuffle.value == true -> {
                // Pick a random song
                val randomIndex = (playlist.indices).random()
                playIndex(randomIndex)
            }
            else -> {
                // Normal previous
                val prevIndex = currentIndex - 1
                if (prevIndex >= 0) {
                    playIndex(prevIndex)
                } else {
                    // At beginning → restart current song
                    player.seekTo(0)
                }
            }
        }
    }
    fun shuffleToggle() {
        isShuffle.value = !isShuffle.value!!
        updateNotification()
    }
    fun repeatToggle() {
        repeatMode.value = !repeatMode.value!!
        updateNotification()
    }
    private fun stopServiceAndNotification() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (t: Throwable) { /* ignore */ }
    }

    override fun onDestroy() {
        Log.w("MusicPlayerService", "Service destroyed by system")
        handler.removeCallbacks(progressRunnable)
        if (::player.isInitialized) player.release()
        if (::mediaSession.isInitialized) mediaSession.release()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) {}
        super.onDestroy()
    }
    private fun startForegroundWithNotification(song: SongItem) {
        val placeholder = BitmapFactory.decodeResource(resources, R.drawable.playlist)
        val notif = buildNotification(song, placeholder)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startForeground(NOTIFICATION_ID, notif)
        }

        if (song.id == currentArtSongId && currentAlbumArt != null) {
            val notif = buildNotification(song, currentAlbumArt!!)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notif)
            }
            return
        }

        // 2️⃣ Load real album art in background
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = try {
                if (song.image.size > 2 && song.image[2].url.isNotBlank()) {
                    Picasso.get()
                        .load(song.image[2].url)
                        .resize(512, 512)
                        .centerCrop()
                        .get()
                } else {
                    placeholder // fallback if no image
                }
            } catch (e: Exception) {
                Log.e("AlbumArt", "Error loading album art", e)
                placeholder
            }

            // 3️⃣ Update metadata and notification on main thread
            withContext(Dispatchers.Main) {
                currentAlbumArt = bitmap
                currentArtSongId = song.id

                updateMetadata(song, bitmap)
                val updatedNotif = buildNotification(song, bitmap)

                val notificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, updatedNotif)
            }
        }
    }
    fun updateNotification() {
        val song = currentSongLive.value ?: return

        if (song.id == currentArtSongId && currentAlbumArt != null) {
            val notif = buildNotification(song, currentAlbumArt!!)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notif)
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = try {
                if (song.image.size > 2 && song.image[2].url.isNotBlank()) {
                    Picasso.get()
                        .load(song.image[2].url)
                        .resize(512, 512)
                        .centerCrop()
                        .get()
                } else {
                    BitmapFactory.decodeResource(resources, R.drawable.playlist)
                }
            } catch (e: Exception) {
                Log.e("AlbumArt", "Error loading image", e)
                BitmapFactory.decodeResource(resources, R.drawable.playlist)
            }

            withContext(Dispatchers.Main) {
                currentAlbumArt = bitmap
                currentArtSongId = song.id

                updateMetadata(song,bitmap)

                // ✅ Update the notification WITHOUT calling startForeground again
                val notif = buildNotification(song, bitmap)
                if (ActivityCompat.checkSelfPermission(
                        this@MusicPlayerService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(this@MusicPlayerService).notify(NOTIFICATION_ID, notif)
                } else {
                    Log.w("MusicPlayerService", "Notification permission denied, cannot show notification")
                }
            }
        }
    }
    private fun buildNotification(song: SongItem, bitmap: Bitmap): Notification {
        val playIntent = Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_PLAY)
        val pauseIntent = Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_PAUSE)
        val nextIntent = Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_NEXT)
        val prevIntent = Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_PREV)
        val shuffleIntent = Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_SHUFFLE)
        val repeatIntent = Intent(this, NotificationActionReceiver::class.java).setAction(ACTION_REPEAT)

        val playPending    = PendingIntent.getBroadcast(this, 100, playIntent, PendingIntent.FLAG_IMMUTABLE)
        val pausePending   = PendingIntent.getBroadcast(this, 101, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val nextPending    = PendingIntent.getBroadcast(this, 102, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        val prevPending    = PendingIntent.getBroadcast(this, 103, prevIntent, PendingIntent.FLAG_IMMUTABLE)
        val shufflePending = PendingIntent.getBroadcast(this, 104, shuffleIntent, PendingIntent.FLAG_IMMUTABLE)
        val repeatPending  = PendingIntent.getBroadcast(this, 105, repeatIntent, PendingIntent.FLAG_IMMUTABLE)

        val openIntent = Intent(this, PlaySong::class.java)
        val openPending = PendingIntent.getActivity(this, 200, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val isPlaying = isPlayingLive.value ?: false
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(R.drawable.notificationpausebutton, "Pause", pausePending)
        } else {
            NotificationCompat.Action(R.drawable.notificationplaybutton, "Play", playPending)
        }

        val isShuffle = isShuffle.value ?: false
        val shuffleAction = if (isShuffle) {
            NotificationCompat.Action(R.drawable.notificationshufflebutton, "shuffle", shufflePending)
        } else {
            NotificationCompat.Action(R.drawable.disableshuffle, "disableShuffle", shufflePending)
        }

        val isRepeat = repeatMode.value ?: false
        val repeatAction = if (isRepeat) {
            NotificationCompat.Action(R.drawable.notificationrepeatbutton, "repeat", repeatPending)
        } else {
            NotificationCompat.Action(R.drawable.disablerepeat, "disableRepeat", repeatPending)
        }

        val songName = Html.fromHtml(song.name,Html.FROM_HTML_MODE_LEGACY)
        updateMetadata(song, bitmap)

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                getCurrentPosition().toLong(),
                1f)
            .build()

        mediaSession.setPlaybackState(playbackState)

        val notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(songName)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.headset_image)
            .setLargeIcon(bitmap)
            .setContentIntent(openPending)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(shuffleAction)
            .addAction(NotificationCompat.Action(R.drawable.notificationprevbutton, "Prev", prevPending))
            .addAction(playPauseAction)
            .addAction(NotificationCompat.Action(R.drawable.notificationnextbutton, "Next", nextPending))
            .addAction(repeatAction)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2, 3, 4))

        return notifBuilder.build()
    }
    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Music player", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback controls"
            // Show full content on lockscreen
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }
    private fun updateMetadata(song: SongItem, bitmap: Bitmap) {
        val duration = if (::player.isInitialized && player.duration > 0) player.duration else song.duration.toLong()
        val songName = Html.fromHtml(song.name,Html.FROM_HTML_MODE_LEGACY)

        mediaSession.setMetadata(
            android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, songName.toString())
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build()
        )
    }
    private fun updatePlaybackInfo() {
        val song = currentSongLive.value ?: return
        if (!::player.isInitialized) return

        val duration = player.duration.coerceAtLeast(song.duration.toLong())
        val position = player.currentPosition

        // Update LiveData
        durationLive.postValue(duration.toInt())
        progressLive.postValue(position.toInt())

        // Update playback state
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                position,
                1f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }
}