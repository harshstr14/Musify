package com.example.musify

import android.content.Context
import android.media.MediaPlayer

class MediaPlayerManager private constructor() {
    companion object {
        private var mediaPlayer: MediaPlayer? = null

        fun getPlayer(context: Context): MediaPlayer {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            }
            return mediaPlayer!!
        }
    }
}
