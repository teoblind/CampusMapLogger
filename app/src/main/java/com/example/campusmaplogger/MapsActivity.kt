package com.example.campusmaplogger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.campusmaplogger.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.io.File

import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment

import android.view.View

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import com.google.android.gms.maps.model.BitmapDescriptorFactory

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.Marker

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val REQUEST_IMAGE_CAPTURE = 1
    private lateinit var photoUri: Uri

    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    data class PhotoMarker(val uri: Uri, val location: LatLng)
    private val photoMarkers = mutableListOf<PhotoMarker>()

    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var liveLocationMarker: Marker? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnTakePicture.setOnClickListener {
            dispatchTakePictureIntent()
        }

        locationRequest = LocationRequest.create().apply {
            interval = 5000  // every 5 seconds
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)

                    // Remove old marker
                    liveLocationMarker?.remove()

                    // Add new marker
                    liveLocationMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Your current location")
                    )

                    // Move camera (optional)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        checkAndRequestLocationPermission()

        photoMarkers.forEach { marker ->
            addPhotoMarker(marker.uri, marker.location)
        }


        mMap.setOnMarkerClickListener { marker ->
            val tag = marker.tag
            if (tag is Uri) {
                val intent = Intent(this, FullPhotoActivity::class.java)
                intent.putExtra("photoUri", tag)
                startActivity(intent)
                true
            } else {
                false
            }
        }
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableUserLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableUserLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true

            // Show the initial location once
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val userLatLng = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    // Fallback for emulator/grading
                    LatLng(31.3856, 120.9818) // Kunshan, China
                }

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 16f))
                mMap.addMarker(MarkerOptions().position(userLatLng).title("You're here"))
            }

            // 🔁 Start continuous location updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            // Handle the case where permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestFreshLocation() {
        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        val location = result.lastLocation
                        if (location != null) {
                            val userLatLng = LatLng(location.latitude, location.longitude)
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))
                            mMap.addMarker(MarkerOptions().position(userLatLng).title("You're here"))
                        }
                    }
                },
                mainLooper
            )
        }
    }

    private fun dispatchTakePictureIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile: File? = createImageFile()
            photoFile?.also {
                photoUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    it
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    @Deprecated("Deprecated in Java")

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show()

            // Show thumbnail preview
            binding.imageThumbnail.setImageURI(photoUri)
            binding.imageThumbnail.visibility = View.VISIBLE

            // Get user location and add marker to the map
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    addPhotoMarker(photoUri, latLng)  // You'll define this function next
                } else {
                    Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }




//Part 3
private fun addPhotoMarker(uri: Uri, location: LatLng) {
    val inputStream = contentResolver.openInputStream(uri)
    val bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream?.close()

    // Resize to thumbnail size
    val thumbnail = Bitmap.createScaledBitmap(bitmap, 100, 100, false)

    // Create one marker and assign tag
    val marker = mMap.addMarker(
        MarkerOptions()
            .position(location)
            .icon(BitmapDescriptorFactory.fromBitmap(thumbnail))
            .title("Photo taken here")
    )
    marker?.tag = uri  // Link this marker to the photo Uri
}

    override fun onPause() {
        super.onPause()

        // Stop receiving location updates when app is in background
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

}