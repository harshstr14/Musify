package com.example.musify.songData

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Image(
    val quality: String,
    val url: String
) : Parcelable
