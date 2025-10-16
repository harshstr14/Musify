package com.example.musify

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class GoogleSignInManager private constructor(private val activity: Activity) {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference()

    companion object {
        const val GOOGLE_SIGN_IN = 100
        private var instance: GoogleSignInManager ?= null

        fun getInstance(context: android.content.Context): GoogleSignInManager {
            val activity = context as? Activity
                ?: throw IllegalArgumentException("Context must be an Activity")
            if (instance == null || instance?.activity != activity) {
                instance = GoogleSignInManager(activity)
            }
            return instance!!
        }
    }
    fun setUpGoogleSignInOption() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("675693172763-7478p6fjg3g3dkb84clhfnoc7ouvv5q0.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(activity,gso)
    }
    val isUserAlreadySignedIn: Boolean
        get() = auth.currentUser != null

    fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent,GOOGLE_SIGN_IN)
    }
    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            Toast.makeText(activity, "Signed out", Toast.LENGTH_SHORT).show()
        }
    }
    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken != null) {
                firebaseAuthWithGoogle(idToken)
            } else {
                Toast.makeText(activity, "ID Token not found", Toast.LENGTH_SHORT).show()
                Log.e("GoogleSignInManager", "ID token is null")
            }
        } catch (e: ApiException) {
            Toast.makeText(activity, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            Log.e("GoogleSignInManager", "SignIn failed", e)
        }
    }
    fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(activity) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val userID = user?.uid
                val mail = user?.email
                val name = user?.displayName
                val photoUrl = user?.photoUrl.toString()
                val url = photoUrl.replace("s96-c","s400-c")

                if (userID != null) {
                    val userRef = database.child("Users").child(userID)
                    userRef.get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            val userData = mapOf(
                                "name" to name,
                                "mail" to mail,
                                "photoUrl" to url
                            )

                            userRef.setValue(userData).addOnSuccessListener {
                                Log.d("FireBase", "User profile created successfully.")
                            }.addOnFailureListener {
                                Log.e("Firebase", "Failed to create user profile: ${it.message}")
                            }
                        } else {
                            Log.d("FireBase", "User already exists. Skipping creation.")
                        }

                        Toast.makeText(activity, "Welcome ${user.displayName}", Toast.LENGTH_SHORT).show()
                        Log.d("GoogleSignInManager", "signInWithCredential : success")
                        val intent = Intent(activity, MainActivity2::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        activity.startActivity(intent)
                    }
                } else {
                    Log.e("FireBase", "UID is null after successful registration")
                    Toast.makeText(activity, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "Authentication Failed", Toast.LENGTH_SHORT).show()
                Log.w("GoogleSignInManager", "signInWithCredential:failure", task.exception)
            }
        }
    }
}