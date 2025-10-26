package com.example.musify

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.musify.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SignUp : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var googleSignInManager: GoogleSignInManager ?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

        setStatusBarIconsTheme(this)

        googleSignInManager = GoogleSignInManager.getInstance(this)
        googleSignInManager?.setUpGoogleSignInOption()

        binding.googleSignInBtn.setOnClickListener {
            googleSignInManager?.signIn()
        }

        binding.backArrowBtn.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        binding.signUpBtn.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val mail = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            val pref = getSharedPreferences("Pref_Name",MODE_PRIVATE)

            if (mail.isEmpty()) {
                binding.emailEditText.error = "Email Required"
                binding.emailEditText.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.passwordEditText.error = "Password Required"
                binding.passwordEditText.requestFocus()
                return@setOnClickListener
            }

            if (name.isNotEmpty() && mail.isNotEmpty() && password.isNotEmpty()) {
                auth = FirebaseAuth.getInstance()
                database = FirebaseDatabase.getInstance().getReference()

                auth.createUserWithEmailAndPassword(mail,password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userID = auth.currentUser?.uid
                        val userData = mapOf(
                            "name" to name,
                            "mail" to mail,
                        )
                        if (userID != null) {
                            database.child("Users").child(userID).setValue(userData).addOnSuccessListener {
                                pref.edit { putBoolean("isLoggedIn", true) }
                                val intent = Intent(this, MainActivity2::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                startActivity(intent)
                                finish()
                            }.addOnFailureListener { e ->
                                Log.e("FirebaseDB", "Failed to save user data: ${e.message}", e)
                                Toast.makeText(this, "Could not save user info. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("Auth", "UID is null after successful registration")
                            Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        try {
                            throw task.exception!!
                        } catch (e: FirebaseAuthUserCollisionException) {
                            // Email already in use
                            Toast.makeText(this, "This email is already registered.", Toast.LENGTH_SHORT).show()
                            Log.e("Auth", "Email : ${e.message}")
                        } catch (e: FirebaseAuthWeakPasswordException) {
                            // Weak password
                            Toast.makeText(this, "Password is too weak. Use at least 6 characters.", Toast.LENGTH_SHORT).show()
                            Log.e("Auth", "Password : ${e.message}")
                        } catch (e: FirebaseAuthInvalidCredentialsException) {
                            // Invalid email format
                            Toast.makeText(this, "Invalid email format.", Toast.LENGTH_SHORT).show()
                            Log.e("Auth", "Email Format : ${e.message}")
                        } catch (e: Exception) {
                            // Other errors
                            Toast.makeText(this, "Sign-up failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("Auth", "Other : ${e.message}")
                        }
                    }
                }.addOnFailureListener { e ->
                    Log.e("Auth", "FirebaseAuth error: ${e.message}", e)
                    Toast.makeText(this, "Sign-up failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this,"Please Fill the Details", Toast.LENGTH_SHORT).show()
            }
        }

        binding.signuptxt.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleSignInManager.GOOGLE_SIGN_IN) {
            googleSignInManager?.handleSignInResult(data)
        }
    }
    private fun enableEdgeToEdgeWithInsets(rootView: View) {
        val activity = rootView.context as ComponentActivity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            rootView.setPadding(
                rootView.paddingLeft,
                rootView.paddingTop,
                rootView.paddingRight,
                systemBars.bottom
            )

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