package com.example.musify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
            this.action = action
        }
        Log.d("NotificationReceiver", "Action received: $action")

        ContextCompat.startForegroundService(context, serviceIntent)
    }
}