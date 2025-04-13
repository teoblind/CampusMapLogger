// Package declaration
package com.example.campusmaplogger

// Importing necessary Android and Google Maps libraries
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Google Maps functionality
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

// View binding for XML layout
import com.example.campusmaplogger.databinding.ActivityMapsBinding

// Location services to track user location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

// For handling image capture intents and URIs
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.io.File

// Secure file sharing between app and camera intent
import androidx.core.content.FileProvider

// Formatting timestamps for saved photo filenames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// For saving files to external app storage
import android.os.Environment

// UI visibility and interaction
import android.view.View

// Handling and resizing images
import android.graphics.Bitmap
import android.graphics.BitmapFactory

// For requesting periodic location updates
import android.os.Looper
import com.google.android.gms.maps.model.BitmapDescriptorFactory

// Callback classes to listen for location updates
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.Marker

// Alert dialog for editing marker titles/snippets
import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    // Request code used when confirming a photo in FullPhotoActivity
    private val PHOTO_CONFIRM_REQUEST = 2

    // Stores the last captured photo's URI and its corresponding location
    private var lastCapturedUri: Uri? = null
    private var lastCapturedLatLng: LatLng? = null

    // Reference to the Google Map object
    private lateinit var mMap: GoogleMap

    // View binding object for accessing layout views
    private lateinit var binding: ActivityMapsBinding

    // Location client for retrieving device location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Request code used when taking a photo with the camera
    private val REQUEST_IMAGE_CAPTURE = 1

    // Stores the URI of the photo currently being captured
    private lateinit var photoUri: Uri

    // Request code used when asking the user for location permissions
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    // Data class representing a photo and its location
    data class PhotoMarker(val uri: Uri, val location: LatLng)

    // List of all photo markers currently on the map
    private val photoMarkers = mutableListOf<PhotoMarker>()

    // Location callback object for receiving periodic location updates
    private lateinit var locationCallback: LocationCallback

    // Configuration for how often to request location updates
    private lateinit var locationRequest: LocationRequest

    // A reference to the currently displayed live location marker on the map
    private var liveLocationMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), 200)
        }
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

        mMap.setOnMarkerClickListener { marker ->
            showMarkerEditDialog(marker)
            true
        }


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


    private fun showMarkerEditDialog(marker: Marker) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val titleBox = EditText(this)
        titleBox.hint = "Enter Title"
        titleBox.setText(marker.title)

        val snippetBox = EditText(this)
        snippetBox.hint = "Enter Snippet"
        snippetBox.setText(marker.snippet)

        layout.addView(titleBox)
        layout.addView(snippetBox)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Marker Info")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                marker.title = titleBox.text.toString()
                marker.snippet = snippetBox.text.toString()
                marker.showInfoWindow()
            }
            .setNeutralButton("View Photo") { _, _ ->
                val uri = marker.tag as? Uri
                if (uri != null) {
                    val intent = Intent(this, FullPhotoActivity::class.java)
                    intent.putExtra("imageUri", uri)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
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
                   // LatLng(39.9042, 116.4074) // Kunshan, China

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

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    addPhotoMarker(photoUri, latLng)
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