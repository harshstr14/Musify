package com.example.musify

import android.app.Application
import com.cloudinary.android.MediaManager
import kotlin.collections.set

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()

        val config = HashMap<String, String>()
        config["cloud_name"] = "dcdg3s1pf"
        config["api_key"] = "146325261992342"
        config["api_secret"] = "wGeCDJkAnjj5g1dOIq_hKCBi4uY"
        MediaManager.init(this,config)
    }
}