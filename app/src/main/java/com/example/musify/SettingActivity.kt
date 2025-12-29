package com.example.musify

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import com.example.musify.databinding.ActivitySettingBinding
import com.example.musify.service.MusicPlayerService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import java.io.File

class SettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var userId: String ?= null
    private lateinit var dialog: AlertDialog
    private var googleSignInManager: GoogleSignInManager ?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivitySettingBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        googleSignInManager = GoogleSignInManager.getInstance(this)
        googleSignInManager?.setUpGoogleSignInOption()

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

        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference().child("Users")

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName

        "App Version $versionName".also { binding.textView11.text = it }

        binding.textView9.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/harshstr14".toUri())
            startActivity(intent)
        }
        binding.textView10.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/harshstr14".toUri())
            startActivity(intent)
        }
        binding.githubIcon.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/harshstr14".toUri())
            startActivity(intent)
        }

        binding.textView5.setOnClickListener {
            showDialog(this,"Reset Musify App","Are you sure you want to clear data")
        }
        binding.textView6.setOnClickListener {
            showDialog(this,"Reset Musify App","Are you sure you want to clear data")
        }
        binding.resetIcon.setOnClickListener {
            showDialog(this,"Reset Musify App","Are you sure you want to clear data")
        }

        binding.textView7.setOnClickListener {
            showDialog(this,"Delete Account","Are you sure you want to delete account")
        }
        binding.textView8.setOnClickListener {
            showDialog(this,"Delete Account","Are you sure you want to delete account")
        }
        binding.deleteIcon.setOnClickListener {
            showDialog(this,"Delete Account","Are you sure you want to delete account")
        }

        binding.updateIcon.setOnClickListener {
            checkForUpdate(this)
        }
        binding.textView1.setOnClickListener {
            checkForUpdate(this)
        }
        binding.textView2.setOnClickListener {
            checkForUpdate(this)
        }

        val spinnerAdapter = ArrayAdapter.createFromResource(
            this,R.array.quality_array,R.layout.quality_selected_item
        )
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.qualityAdapter.adapter = spinnerAdapter

        userId?.let {
            database.child(it).child("quality").get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val savedQuality = snapshot.getValue(String::class.java)
                    if (!savedQuality.isNullOrEmpty()) {
                        val spinnerPosition = spinnerAdapter.getPosition(savedQuality)
                        if (spinnerPosition >= 0) {
                            binding.qualityAdapter.setSelection(spinnerPosition)
                        }
                    }
                } else {
                    val spinnerPosition = spinnerAdapter.getPosition("320kbps")
                    if (spinnerPosition >= 0) {
                        binding.qualityAdapter.setSelection(spinnerPosition)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("Firebase", "Failed to load quality", e)
            }
        }

        var selectedQuality: String? = null
        var isUserAction = false

        binding.qualityAdapter.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isUserAction = true
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                }
            }
            false
        }

        binding.qualityAdapter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedQuality = parent.getItemAtPosition(position) as String

                if (isUserAction && userId != null) {
                    database.child(userId!!).child("quality")
                        .setValue(selectedQuality).addOnSuccessListener {
                            Log.d("Firebase", "Quality saved: $selectedQuality")
                        }.addOnFailureListener {
                            Log.e("Firebase", "Failed to save Quality", it)
                        }
                }

                isUserAction = false
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedQuality = null
            }
        }
    }
    private fun showDialog(context: Context,title: String,message: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.reset_dialogview,null)
        dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.textView18).text = title
        dialogView.findViewById<TextView>(R.id.textView19).text = message

        dialogView.findViewById<TextView>(R.id.btnRemove).setOnClickListener {
            if (title == "Reset Musify App") {
                clearAppCache(this)
            } else {
                deleteAccount(this)
            }
        }

        dialogView.findViewById<TextView>(R.id.btnCancle).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun clearAppCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            if (deleteDir(cacheDir)) {
                userId?.let {
                    val favReference = database.child(it).child("Favourites")
                    favReference.removeValue()
                }
                try {
                    val serviceIntent = Intent(context, MusicPlayerService::class.java)
                    context.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                clearSpecificPrefs(this)
                Toast.makeText(this,"Reset Successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this,"Failed to reset,try again", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDir(File(dir, child))
                    if (!success) return false
                }
            }
        }
        return dir?.delete() ?: false
    }
    private fun clearSpecificPrefs(context: Context) {
        val pref = context.getSharedPreferences("MusifyPref", MODE_PRIVATE)
        pref.edit { remove("recently_played") }
    }
    private fun deleteAccount(context: Context) {
        userId?.let {
            val userReference = database.child(it)
            userReference.removeValue().addOnSuccessListener {
                val pref = context.getSharedPreferences("Pref_Name",MODE_PRIVATE)
                pref.edit { putBoolean("isLoggedIn", false) }

                googleSignInManager!!.signOut()

                FirebaseAuth.getInstance().currentUser?.delete()

                try {
                    val serviceIntent = Intent(context, MusicPlayerService::class.java)
                    context.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val intent = Intent(context, SignIn::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)

                if (context is Activity) {
                    context.finish()
                }

                clearSpecificPrefs(this)
                Toast.makeText(this,"Account Deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }.addOnFailureListener {
                Toast.makeText(this,"Failed to delete account,Try Again", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
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
                    val paddingInDp = 10
                    val scale = resources.displayMetrics.density
                    val paddingInPx = (paddingInDp * scale).toInt()
                    binding.textView11.setPadding(0,0,0,paddingInPx)
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
    private fun checkForUpdate(context: Context) {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersion = remoteConfig.getString("latest_version")
                val message = remoteConfig.getString("update_message")
                val downloadUrl = remoteConfig.getString("download_url")

                val currentVersion = getCurrentVersion(context)

                if (isNewVersionAvailable(currentVersion, latestVersion)) {
                    showDialog(context,message,latestVersion, currentVersion, downloadUrl)
                } else {
                    Toast.makeText(this,"Update not available", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this,"Update not available", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this,"Something went wrong,try again", Toast.LENGTH_SHORT).show()
        }
    }
    private fun isNewVersionAvailable(current: String, latest: String): Boolean {
        val currentParts = current.split(".")
        val latestParts = latest.split(".")

        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
    private fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    private fun showDialog(context: Context,title: String, latestVersion: String, currentVersion: String, downloadUrl: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.update_dialog,null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.textView).text = title
        "Version : $currentVersion - $latestVersion".also { dialogView.findViewById<TextView>(R.id.textView20).text = it }

        dialogView.findViewById<TextView>(R.id.btnUpdate).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
            startActivity(intent)
        }

        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}