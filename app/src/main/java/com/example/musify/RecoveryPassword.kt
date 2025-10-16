package com.example.musify

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.musify.databinding.ActivityRecoveryPasswordBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class RecoveryPassword : AppCompatActivity() {
    private lateinit var binding: ActivityRecoveryPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityRecoveryPasswordBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        enableEdgeToEdgeWithInsets(binding.root)

        binding.backArrowBtn.setOnClickListener {
            finish()
        }

        auth = FirebaseAuth.getInstance()
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val continueButton = findViewById<MaterialButton>(R.id.continueBtn)

        continueButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                emailEditText.error = "Email Required"
                emailEditText.requestFocus()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Recovery Password","Password reset email sent")
                    Toast.makeText(this,"Password reset email sent", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("Recovery Password","Failed to sent: ${task.exception}")
                    Toast.makeText(this,"${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
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