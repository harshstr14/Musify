package com.example.musify

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.musify.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var googleSignInManager: GoogleSignInManager ?= null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

        googleSignInManager = GoogleSignInManager.getInstance(this)
        val pref = getSharedPreferences("Pref_Name",MODE_PRIVATE)
        val isLoggedIn = pref.getBoolean("isLoggedIn",false)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isLoggedIn) {
                val intent = Intent(this, MainActivity2::class.java)
                startActivity(intent)
                finish()
            } else if (googleSignInManager!!.isUserAlreadySignedIn) {
                val intent = Intent(this, MainActivity2::class.java)
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this, SignIn::class.java)
                startActivity(intent)
                finish()
            }
        },3000)
    }
    fun enableEdgeToEdgeWithInsets(rootView: View) {
        val activity = rootView.context as ComponentActivity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            rootView.setPadding(
                rootView.paddingLeft,
                systemBars.top,
                rootView.paddingRight,
                systemBars.bottom
            )

            insets
        }
    }
}