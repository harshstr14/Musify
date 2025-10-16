package com.example.musify.songData

data class Song(
    val id: String,
    val name: String,
    val type: String,
    val year: String,
    val releaseDate: String,
    val duration: Int,
    val playCount: Int,
    val language: String,
    val album: Album,
    val artists: MutableList<Artists>,
    val image: MutableList<Image>,
    val downloadUrl: MutableList<Download>
    )