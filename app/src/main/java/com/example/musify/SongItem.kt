package com.example.musify

import android.os.Parcelable
import com.example.musify.songData.Download
import com.example.musify.songData.Image
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongItem(
    val id: String,
    val name: String,
    val artist: String,
    val image: MutableList<Image>,
    val duration: Int,
    val downloadUrl: MutableList<Download>,
    var isFav: Boolean = false
) : Parcelable
