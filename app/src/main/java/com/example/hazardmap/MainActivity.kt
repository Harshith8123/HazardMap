package com.example.hazardmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var searchView: SearchView
    private var lastSearchedLatLng: LatLng? = null
    private var currentPolyline: Polyline? = null
    private var is3DMode = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            enableUserLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
    }

    private fun setupUI() {
        searchView = findViewById(R.id.search_view)
        val navLayout = findViewById<LinearLayout>(R.id.navigation_layout)
        val btnDirections = findViewById<Button>(R.id.btn_get_directions)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchLocation(query)
                    navLayout.visibility = View.VISIBLE
                }
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })

        btnDirections.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && lastSearchedLatLng != null) {
                        val origin = LatLng(location.latitude, location.longitude)
                        getDirections(origin, lastSearchedLatLng!!)
                    } else {
                        Toast.makeText(this, "Location or destination not available", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<FloatingActionButton>(R.id.btn_zoom_in).setOnClickListener {
            if (::googleMap.isInitialized) googleMap.animateCamera(CameraUpdateFactory.zoomIn())
        }

        findViewById<FloatingActionButton>(R.id.btn_zoom_out).setOnClickListener {
            if (::googleMap.isInitialized) googleMap.animateCamera(CameraUpdateFactory.zoomOut())
        }

        findViewById<FloatingActionButton>(R.id.btn_home).setOnClickListener {
            if (::googleMap.isInitialized) enableUserLocation()
        }

        findViewById<FloatingActionButton>(R.id.btn_map_type).setOnClickListener {
            if (::googleMap.isInitialized) {
                googleMap.mapType = if (googleMap.mapType == GoogleMap.MAP_TYPE_NORMAL) {
                    GoogleMap.MAP_TYPE_SATELLITE
                } else {
                    GoogleMap.MAP_TYPE_NORMAL
                }
            }
        }

        findViewById<FloatingActionButton>(R.id.btn_map_style).setOnClickListener {
            showMapStyleDialog()
        }

        findViewById<FloatingActionButton>(R.id.btn_3d_mode).setOnClickListener {
            if (::googleMap.isInitialized) {
                is3DMode = !is3DMode
                val currentPos = googleMap.cameraPosition
                val tilt = if (is3DMode) 60f else 0f
                val bearing = if (is3DMode) currentPos.bearing else 0f
                
                val newPos = CameraPosition.Builder()
                    .target(currentPos.target)
                    .zoom(currentPos.zoom)
                    .tilt(tilt)
                    .bearing(bearing)
                    .build()
                
                googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(newPos))
                Toast.makeText(this, if (is3DMode) "3D Mode On" else "2D Mode On", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isMapToolbarEnabled = false // We use our own UI
        
        googleMap.setOnMapClickListener { latLng ->
            showAddMarkerDialog(latLng)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun searchLocation(locationName: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        Thread {
            try {
                val addressList: List<Address>? = geocoder.getFromLocationName(locationName, 1)
                runOnUiThread {
                    if (!addressList.isNullOrEmpty()) {
                        val address = addressList[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        lastSearchedLatLng = latLng
                        googleMap.clear() // Clear previous search markers
                        currentPolyline = null
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(locationName)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        )
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    } else {
                        Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showAddMarkerDialog(latLng: LatLng) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_marker, null)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.et_description)
        val typeGroup = dialogView.findViewById<RadioGroup>(R.id.rg_hazard_type)

        AlertDialog.Builder(this)
            .setTitle("Add Hazard")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val description = descriptionInput.text.toString()
                val color = when (typeGroup.checkedRadioButtonId) {
                    R.id.rb_fire -> BitmapDescriptorFactory.HUE_RED
                    R.id.rb_flood -> BitmapDescriptorFactory.HUE_BLUE
                    R.id.rb_pothole -> BitmapDescriptorFactory.HUE_YELLOW
                    else -> BitmapDescriptorFactory.HUE_MAGENTA
                }
                addHazardMarker(latLng, description, color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addHazardMarker(latLng: LatLng, title: String, color: Float) {
        googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(color))
        )
    }

    private fun showMapStyleDialog() {
        val styles = arrayOf("Default", "Apple Maps Style", "Dark Mode", "Vibrant", "Colorblind Friendly")
        AlertDialog.Builder(this)
            .setTitle("Choose Map Style")
            .setItems(styles) { _, which ->
                val styleResId = when (which) {
                    1 -> R.raw.map_style_silver
                    2 -> R.raw.map_style_dark
                    3 -> R.raw.map_style_vibrant
                    4 -> R.raw.map_style_colorblind
                    else -> null
                }
                if (::googleMap.isInitialized) {
                    googleMap.setMapStyle(if (styleResId != null) MapStyleOptions.loadRawResourceStyle(this, styleResId) else null)
                }
            }
            .show()
    }

    private fun getDirections(origin: LatLng, dest: LatLng) {
        val apiKey = "AIzaSyCeOGlyW0CkNeBHI5DXPkvozvyVy9HM3M0" // Better to get from resources
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&key=$apiKey"

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                val data = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(data)
                
                if (jsonResponse.getString("status") == "OK") {
                    val routes = jsonResponse.getJSONArray("routes")
                    val overviewPolyline = routes.getJSONObject(0)
                        .getJSONObject("overview_polyline").getString("points")
                    
                    val path = decodePolyline(overviewPolyline)
                    
                    runOnUiThread {
                        currentPolyline?.remove()
                        currentPolyline = googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(path)
                                .width(12f)
                                .color("#4A90E2".toColorInt())
                                .geodesic(true)
                        )
                        
                        val builder = LatLngBounds.Builder()
                        path.forEach { builder.include(it) }
                        val bounds = builder.build()
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    }
                } else {
                    val status = jsonResponse.getString("status")
                    val errorMessage = if (jsonResponse.has("error_message")) {
                        jsonResponse.getString("error_message")
                    } else status
                    
                    runOnUiThread {
                        Toast.makeText(this, "Directions Error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to get directions", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                }
            }
        }
    }
}
