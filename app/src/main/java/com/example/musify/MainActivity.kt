package com.example.musify

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import com.example.musify.AppConstants.KEY_DIALOG_SHOWN
import com.example.musify.AppConstants.PREF_NAME
import com.example.musify.databinding.ActivityMainBinding
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {
    private var googleSignInManager: GoogleSignInManager ?= null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_DIALOG_SHOWN, false) }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightNavigationBars = false

        handleBottomNavPosition()

        setStatusBarIconsTheme(this)

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
    private fun Int.dpToPx(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()
    private fun handleBottomNavPosition() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Typical values:
            // Gesture: 16–24dp
            // 3-button: 48–80dp

            val threshold = 40.dpToPx(binding.root)

            binding.main.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (navBarHeight > threshold) {
                    navBarHeight   // 3-button → move up
                } else {
                    0              // Gesture → stay at bottom
                }
            }

            insets
        }
    }
    private fun setStatusBarIconsTheme(activity: Activity) {
        val window = activity.window
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)

        // Detect current theme
        val isDarkTheme =
            (activity.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Set icon color automatically
        if (isDarkTheme) {
            // Light icons for dark theme
            insetsController.isAppearanceLightStatusBars = false
        } else {
            // Dark icons for light theme
            insetsController.isAppearanceLightStatusBars = false
        }
    }
}