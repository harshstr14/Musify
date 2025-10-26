package com.example.musify

import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.musify.databinding.FragmentProfileBinding
import com.example.musify.service.MusicPlayerService
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class Profile : Fragment() {
    private lateinit var binding: FragmentProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var googleSignInManager: GoogleSignInManager ?= null
    private lateinit var profileImageVIew: CircleImageView
    private lateinit var cameraIcon: ImageButton
    private var selectedImageUri: Uri ?= null
    private var progressDialog: AlertDialog? = null
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                profileImageVIew.setImageURI(it)
                uploadToCloudinary(it)
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentProfileBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.progressBar.fadeIn()
        binding.scrollView.fadeOut()

        profileImageVIew = binding.profileImage
        cameraIcon = binding.cameraImageButton

        cameraIcon.setOnClickListener {
            Log.d("Profile", "Camera icon clicked")
            pickImageLauncher.launch("image/*")
        }

        googleSignInManager = GoogleSignInManager.getInstance(requireContext())
        googleSignInManager?.setUpGoogleSignInOption()

        auth = FirebaseAuth.getInstance()
        val userID = auth.currentUser?.uid

        database = FirebaseDatabase.getInstance().getReference().child("Users")
        if (userID != null) {
            database.child(userID).get().addOnSuccessListener {
                val name = it.child("name").value.toString()
                val email = it.child("mail").value.toString()
                val imageUrl = it.child("photoUrl").value.toString()

                binding.userNameText.text = name
                binding.userEmailText.text = email
                Picasso.get().load(imageUrl).into(binding.profileImage)

                binding.nameEditText.setText(name)
                binding.emailEditText.setText(email)

                binding.progressBar.fadeOut()
                binding.scrollView.fadeIn()
            }
        }

        var isEditing = false
        binding.editProfileButton.setOnClickListener {
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val nameEditText = binding.nameEditText

            if (!isEditing) {
                // Enable editing
                nameEditText.isEnabled = true
                nameEditText.isFocusableInTouchMode = true
                nameEditText.requestFocus()

                // Change button text
                binding.editProfileButton.text = "Save"
                isEditing = true
            } else {
                // Save name to Firebase
                val newName = nameEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    database.child(userId).child("name").setValue(newName)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Name updated", Toast.LENGTH_SHORT).show()

                            // Disable editing
                            nameEditText.isEnabled = false
                            nameEditText.isFocusable = false

                            // Change button text back
                            binding.editProfileButton.text = "Edit Profile"
                            isEditing = false
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val spinnerAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_array,
            R.layout.spinner_selected_item
        )
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerGender.adapter = spinnerAdapter

        if (userID != null) {
            database.child(userID).child("gender").get()
                .addOnSuccessListener { snapshot ->
                    val savedGender = snapshot.getValue(String::class.java)
                    if (!savedGender.isNullOrEmpty()) {
                        val spinnerPosition = spinnerAdapter.getPosition(savedGender)
                        if (spinnerPosition >= 0) {
                            binding.spinnerGender.setSelection(spinnerPosition)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to load gender", e)
                }
        }

        var selectedGender: String? = null

        binding.spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedGender = parent.getItemAtPosition(position) as String
                Log.d("Spinner", "Selected gender: $selectedGender")

                if (userID != null && selectedGender != "Gender") {
                    database.child(userID).child("gender").setValue(selectedGender).addOnSuccessListener {
                            Log.d("Firebase", "Gender saved: $selectedGender")
                    }.addOnFailureListener { e ->
                            Log.e("Firebase", "Failed to save gender", e)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedGender = null
            }
        }

        binding.recoveryTextView.setOnClickListener {
            val intent = Intent(requireContext(), RecoveryPassword::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        binding.logoutTextView.setOnClickListener {
            val pref = requireContext().getSharedPreferences("Pref_Name",MODE_PRIVATE)
            pref.edit { putBoolean("isLoggedIn", false)
                apply() }

            googleSignInManager!!.signOut()

            try {
                val serviceIntent = Intent(requireContext(), MusicPlayerService::class.java)
                requireContext().stopService(serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val intent = Intent(requireContext(), SignIn::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            activity?.finish()
        }
    }
    private fun View.fadeIn(duration: Long = 300) {
        this.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(duration).start()
        }
    }
    private fun View.fadeOut(duration: Long = 300, onEnd: (() -> Unit)? = null) {
        this.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }
    private fun uploadToCloudinary(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        showProgressDialog("Uploading image...")

        MediaManager.get().upload(imageUri).option("folder","profile_pics")
            .option("public_id", userId)
            .option("overwrite", true)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) { }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) { }

                override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                    val imageUrl = resultData?.get("secure_url").toString()
                    saveImageUrlToDatabase(imageUrl)
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    hideLoading()
                    Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show()
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) { }
            })
            .dispatch()
    }
    private fun saveImageUrlToDatabase(url: String) {
        val userID = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference().child("Users").child(userID!!)
        database.child("photoUrl").setValue(url).addOnSuccessListener {
            hideLoading()
            Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
            loadProfileImage()
        }.addOnFailureListener { e ->
            hideLoading()
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun loadProfileImage() {
        val userID = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference().child("Users").child(userID!!)
        database.child("photoUrl").get().addOnSuccessListener { snapshot ->
            val imageUrl = snapshot.value?.toString()
            if (!imageUrl.isNullOrEmpty()) {
                Picasso.get().load(imageUrl).into(profileImageVIew)
            }
        }
    }
    private fun showProgressDialog(message: String = "Loading...") {
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val textView = dialogView.findViewById<TextView>(R.id.progressText)
        textView.text = message

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setCancelable(false)

        progressDialog = builder.create()
        progressDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        progressDialog?.show()
    }
    private fun hideLoading() {
        progressDialog?.dismiss()
    }
}