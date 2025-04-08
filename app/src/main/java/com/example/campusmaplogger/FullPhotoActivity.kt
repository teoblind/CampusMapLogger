package com.example.campusmaplogger

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


import android.net.Uri

import android.widget.ImageView

class FullPhotoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_photo)

        val imageView = findViewById<ImageView>(R.id.fullImageView)
        val imageUri = intent.getParcelableExtra<Uri>("imageUri")

        imageUri?.let {
            imageView.setImageURI(it)
        }
    }
}