package com.example.musify

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
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
    private fun Int.dpToPx(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()
    private fun updateBottomNavSelection() {
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
    private fun handleBottomNavPosition() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Typical values:
            // Gesture: 16–24dp
            // 3-button: 48–80dp

            val threshold = 40.dpToPx(binding.root)

            binding.bottomNavBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = if (navBarHeight > threshold) {
                    navBarHeight   // 3-button → move up
                } else {
                    0              // Gesture → stay at bottom
                }
            }
            if (navBarHeight < threshold) {
                binding.bottomNavBar.updateLayoutParams {
                    height = 65.dpToPx(binding.bottomNavBar)
                }
                binding.bottomNavBar.setPadding(0,0,0,12.dpToPx(binding.bottomNavBar))
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