package com.example.musify

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.musify.databinding.ActivitySignInBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class SignIn : AppCompatActivity() {
    private lateinit var binding: ActivitySignInBinding
    private lateinit var auth: FirebaseAuth
    private var googleSignInManager: GoogleSignInManager ?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivitySignInBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

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

        googleSignInManager = GoogleSignInManager.getInstance(this)
        googleSignInManager?.setUpGoogleSignInOption()

        binding.googleSignInBtn.setOnClickListener {
            googleSignInManager?.signIn()
        }

        binding.backArrowBtn.setOnClickListener {
            finishAffinity()
        }

        binding.recoveryTxt.setOnClickListener {
            val intent = Intent(this, RecoveryPassword::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        binding.signInBtn.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (email.isEmpty()) {
                binding.emailEditText.error = "Email Required"
                binding.emailEditText.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.passwordEditText.error = "Password Required"
                binding.passwordEditText.requestFocus()
                return@setOnClickListener
            }

            if (email.isNotEmpty() && password.isNotEmpty()) {
                readData(email,password)
            } else {
                Toast.makeText(this,"Enter Email and Password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.signuptxt.setOnClickListener {
            val intent = Intent(this, SignUp::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }
    private fun readData(email: String,password: String) {
        val pref = getSharedPreferences("Pref_Name",MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()

        Log.d("DEBUG", "Email: $email, Password: $password")

        auth.signInWithEmailAndPassword(email,password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                pref.edit { putBoolean("isLoggedIn",true) }
                val intent = Intent(this, MainActivity2::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            } else {
                try {
                    throw task.exception!!
                } catch (e: FirebaseAuthInvalidUserException) {
                    // Email not registered
                    Toast.makeText(this, "This email is not registered.", Toast.LENGTH_SHORT).show()
                    Log.e("Auth", "Email : ${e.message}")
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    // Wrong password
                    Toast.makeText(this, "Incorrect password.", Toast.LENGTH_SHORT).show()
                    Log.e("Auth", "Password : ${e.message}")
                } catch (e: Exception) {
                    // Other errors
                    Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Auth", "Other : ${e.message}")
                }
            }
        }.addOnFailureListener { e ->
            Log.e("Auth", "FirebaseAuth error: ${e.message}", e)
            Toast.makeText(this, "Log-In failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleSignInManager.GOOGLE_SIGN_IN) {
            googleSignInManager?.handleSignInResult(data)
        }
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