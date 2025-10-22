package com.example.musify

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.example.musify.databinding.ActivityMain2Binding

class MainActivity2 : AppCompatActivity() {
    private lateinit var binding: ActivityMain2Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMain2Binding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        enableEdgeToEdgeWithInsets(binding.root, binding.bottomNavBar)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = ContextCompat.getColor(
            this,
            if (isDark) R.color.status_bar_dark else R.color.status_bar_light
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decor = window.decorView
            decor.systemUiVisibility = if (isDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, Home())
                .commit()
            onItemClick(binding.navBarHome)
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateBottomNavSelection()
        }

        binding.navBarHome.setOnClickListener {
            val home = binding.navBarHome
            onItemClick(home)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)
            if (currentFragment !is Home) {
                replaceWithFragment(Home())
            }
        }

        binding.navBarSearch.setOnClickListener {
            val search = binding.navBarSearch
            onItemClick(search)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)
            if (currentFragment !is Search) {
                replaceWithFragment(Search())
            }
        }

        binding.navBarFavourite.setOnClickListener {
            val favourite = binding.navBarFavourite
            onItemClick(favourite)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)
            if (currentFragment !is Favourite) {
                replaceWithFragment(Favourite())
            }
        }

        binding.navBarPlaylist.setOnClickListener {
            val playlist = binding.navBarPlaylist
            onItemClick(playlist)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)
            if (currentFragment !is Playlist) {
                replaceWithFragment(Playlist())
            }
        }

        binding.navBarMyPlayList.setOnClickListener {
            val myPlaylist = binding.navBarMyPlayList
            onItemClick(myPlaylist)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)
            if (currentFragment !is MyPlaylist) {
                replaceWithFragment(MyPlaylist())
            }
        }
    }
    fun updateBottomNavSelection() {
        clearSelection()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)
        when(currentFragment) {
            is Home -> onItemClick(binding.navBarHome)
            is Search -> onItemClick(binding.navBarSearch)
            is Favourite -> onItemClick(binding.navBarFavourite)
            is Playlist -> onItemClick(binding.navBarPlaylist)
            is MyPlaylist -> onItemClick(binding.navBarMyPlayList)
        }
    }
    private fun replaceWithFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.setCustomAnimations(
            R.anim.enter_from_right,
            R.anim.exit_to_left,
            R.anim.enter_from_left,
            R.anim.exit_to_right
        )
        fragmentTransaction.replace(R.id.frameLayout, fragment)
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
    }
    private fun onItemClick(imageViewCompat: AppCompatImageView) {
        clearSelection()
        imageViewCompat.isSelected = true
        val animation = AnimationUtils.loadAnimation(this,R.anim.nav_item_click)
        imageViewCompat.startAnimation(animation)
    }
    private fun clearSelection() {
        binding.navBarHome.isSelected = false
        binding.navBarSearch.isSelected = false
        binding.navBarFavourite.isSelected = false
        binding.navBarPlaylist.isSelected = false
        binding.navBarMyPlayList.isSelected = false
    }
    fun enableEdgeToEdgeWithInsets(rootView: View, bottomNav: View) {
        val activity = rootView.context as ComponentActivity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.frameLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }

            bottomNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = systemBars.bottom
            }

            insets
        }
    }
}