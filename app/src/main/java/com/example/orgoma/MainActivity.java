package com.example.orgoma;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private List<LatLng> grovePoints = new ArrayList<>();
    private Polygon polygon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Initialize location provider
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Check permissions and request location updates
        requestLocationUpdates();

        Button logoutButton = findViewById(R.id.btnLogout);
        logoutButton.setOnClickListener(v -> logout());
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut(); // Sign out from Firebase
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(MainActivity.this, LoginActivity.class)); // Redirect to LoginActivity
        finish(); // Finish MainActivity to prevent back navigation
    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        checkLocationPermission();

        // Load existing groves from Firestore
        loadGroves();

        // Add new grove on map click
        mMap.setOnMapClickListener(latLng -> {
            openAddGroveDialog(latLng);
        });

        mMap.setOnMarkerClickListener(marker -> {
            // Get the grove ID stored as a tag
            String groveId = (String) marker.getTag();
            if (groveId != null) {
                openEditGroveDialog(groveId, marker); // Open edit dialog
            }
            return true; // Return true to indicate the click event is handled
        });

    }

    // Method to check location permissions
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied! Cannot show location.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openAddGroveDialog(LatLng initialPoint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_grove, null);
        builder.setView(dialogView);

        EditText editOwnerName = dialogView.findViewById(R.id.editOwnerName);
        EditText editVariety = dialogView.findViewById(R.id.editVariety);
        CheckBox checkBiological = dialogView.findViewById(R.id.checkBiological);
        TextView pointList = dialogView.findViewById(R.id.pointList);
        Button btnAddPoint = dialogView.findViewById(R.id.btnAddPoint);
        Button btnSaveGrove;
        btnSaveGrove = dialogView.findViewById(R.id.btnSaveGrove);

        // Add initial point
        grovePoints.clear();
        grovePoints.add(initialPoint);
        updatePointList(pointList);

        btnAddPoint.setOnClickListener(v -> {
            // Allow adding more points by clicking the map
            mMap.setOnMapClickListener(point -> {
                grovePoints.add(point);
                updatePointList(pointList);

            });
        });

        AlertDialog dialog = builder.create();

        btnSaveGrove.setOnClickListener(v -> {
            String owner = editOwnerName.getText().toString();
            String variety = editVariety.getText().toString();
            boolean isBiological = checkBiological.isChecked();

            if (owner.isEmpty() || variety.isEmpty() || grovePoints.isEmpty()) {
                Toast.makeText(this, "Fill in all fields!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save to Firestore
            Map<String, Object> grove = new HashMap<>();
            grove.put("owner", owner);
            grove.put("variety", variety);
            grove.put("isBiological", isBiological);
            grove.put("points", grovePoints);
            grove.put("status", "not_sprayed");
            List<Map<String, Double>> points = new ArrayList<>();
            for (LatLng point : grovePoints) {
                Map<String, Double> coordinates = new HashMap<>();
                coordinates.put("lat", point.latitude);
                coordinates.put("lng", point.longitude);
                points.add(coordinates);
            }
            grove.put("points", points); // Save the entire polygon

            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Saving grove...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            db.collection("groves").add(grove)
                    .addOnSuccessListener(documentReference -> {
                        progressDialog.dismiss(); // Hide progress dialog
                        Toast.makeText(MainActivity.this, "Grove Added!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Refresh map
                        loadGroves();
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss(); // Hide progress dialog
                        Toast.makeText(this, "Failed to save grove.", Toast.LENGTH_SHORT).show();
                    });

        });

        dialog.show();
    }

    // Update the point list
    private void updatePointList(TextView pointList) {
        StringBuilder pointsText = new StringBuilder("Points:\n");
        for (LatLng point : grovePoints) {
            pointsText.append(point.latitude).append(", ").append(point.longitude).append("\n");
        }
        pointList.setText(pointsText.toString());
    }



    // Load existing groves from Firestore
    private void loadGroves() {
        db.collection("groves").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                mMap.clear();
                for (com.google.firebase.firestore.QueryDocumentSnapshot document : task.getResult()) {
                    String groveId = document.getId();
                    String owner = document.getString("owner");
                    boolean isBiological = document.getBoolean("isBiological");
                    String status = document.getString("status");
                    List<Map<String, Double>> points = (List<Map<String, Double>>) document.get("points");

                    if (points == null || points.isEmpty()) continue;

                    // Extract the first point to use as the marker position
                    Map<String, Double> firstPoint = points.get(0);
                    LatLng groveLocation = new LatLng(firstPoint.get("lat"), firstPoint.get("lng"));

                    // Add marker with color based on status
                    float markerColor = BitmapDescriptorFactory.HUE_RED; // Default: Conventional
                    if (isBiological) markerColor = BitmapDescriptorFactory.HUE_GREEN; // Biological
                    if ("sprayed".equals(status)) markerColor = BitmapDescriptorFactory.HUE_BLUE; // Sprayed

                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(groveLocation)
                            .title(owner + " (" + (isBiological ? "Biological" : "Conventional") + ")")
                            .snippet("Status: " + status)
                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

                    Marker marker = mMap.addMarker(markerOptions);
                    marker.setTag(groveId);
                }
            } else {
                Toast.makeText(MainActivity.this, "Failed to load groves!", Toast.LENGTH_SHORT).show();
            }
        });
    }





    // Check proximity to groves
    private void checkProximity(Location userLocation) {
        db.collection("groves").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (com.google.firebase.firestore.QueryDocumentSnapshot document : task.getResult()) {
                    boolean isBiological = document.getBoolean("isBiological");
                    List<Map<String, Double>> points = (List<Map<String, Double>>) document.get("points");

                    for (Map<String, Double> point : points) {
                        Location groveLocation = new Location("");
                        groveLocation.setLatitude(point.get("lat"));
                        groveLocation.setLongitude(point.get("lng"));

                        float distance = userLocation.distanceTo(groveLocation);

                        if (isBiological && distance < 20) { // 20m for biological
                            sendNotification("Approaching Biological Grove", "You are within 20m of a biological grove. Do not spray!");
                            return;
                        } else if (!isBiological && distance < 10) { // 10m for conventional
                            updateGroveStatus(document.getId()); // Mark as sprayed
                            sendNotification("Approaching Conventional Grove", "You are within 10m of a conventional grove.");
                            return;
                        }
                    }
                }
            }
        });
    }

    private void updateGroveStatus(String groveId) {
        db.collection("groves").document(groveId)
                .update("status", "sprayed")
                .addOnSuccessListener(aVoid -> Log.d("WorkerMode", "Grove marked as sprayed."))
                .addOnFailureListener(e -> Log.e("WorkerMode", "Failed to mark grove as sprayed.", e));
    }





    private void openEditGroveDialog(String groveId, Marker marker) {
        // Fetch grove data from Firestore
        db.collection("groves").document(groveId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                // Get existing data
                String existingOwner = document.getString("owner");
                String existingVariety = document.getString("variety");
                boolean existingIsBiological = document.getBoolean("isBiological");
                List<Map<String, Double>> existingPoints = (List<Map<String, Double>>) document.get("points");

                grovePoints.clear(); // Clear old points
                if (existingPoints != null) {
                    for (Map<String, Double> point : existingPoints) {
                        grovePoints.add(new LatLng(point.get("lat"), point.get("lng")));
                    }
                }

                // Inflate dialog layout
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_grove, null);
                builder.setView(dialogView);

                EditText editOwnerName = dialogView.findViewById(R.id.editOwnerName);
                EditText editVariety = dialogView.findViewById(R.id.editVariety);
                CheckBox checkBiological = dialogView.findViewById(R.id.checkBiological);
                TextView pointList = dialogView.findViewById(R.id.pointList);
                Button btnAddPoint = dialogView.findViewById(R.id.btnAddPoint);
                Button btnSaveGrove = dialogView.findViewById(R.id.btnSaveGrove);

                // Pre-fill data
                editOwnerName.setText(existingOwner);
                editVariety.setText(existingVariety);
                checkBiological.setChecked(existingIsBiological);
                updatePointList(pointList);

                // Add new points
                btnAddPoint.setOnClickListener(v -> {
                    mMap.setOnMapClickListener(latLng -> {
                        grovePoints.add(latLng);
                        updatePointList(pointList);

                    });
                });

                AlertDialog dialog = builder.create();

                // Save updated grove
                btnSaveGrove.setOnClickListener(v -> {
                    String updatedOwner = editOwnerName.getText().toString();
                    String updatedVariety = editVariety.getText().toString();
                    boolean updatedIsBiological = checkBiological.isChecked();

                    if (updatedOwner.isEmpty() || updatedVariety.isEmpty() || grovePoints.isEmpty()) {
                        Toast.makeText(this, "Fill in all fields!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Save to Firestore
                    Map<String, Object> updatedGrove = new HashMap<>();
                    updatedGrove.put("owner", updatedOwner);
                    updatedGrove.put("variety", updatedVariety);
                    updatedGrove.put("isBiological", updatedIsBiological);
                    updatedGrove.put("status", document.getString("status")); // Preserve existing status

                    List<Map<String, Double>> updatedPoints = new ArrayList<>();
                    for (LatLng point : grovePoints) {
                        Map<String, Double> coordinates = new HashMap<>();
                        coordinates.put("lat", point.latitude);
                        coordinates.put("lng", point.longitude);
                        updatedPoints.add(coordinates);
                    }
                    updatedGrove.put("points", updatedPoints);

                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Saving grove...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    db.collection("groves").document(groveId).set(updatedGrove)
                            .addOnSuccessListener(unused -> {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, "Grove Updated!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                loadGroves(); // Refresh the map
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Failed to update grove.", Toast.LENGTH_SHORT).show();
                            });
                });

                dialog.show();
            } else {
                Toast.makeText(this, "Grove not found!", Toast.LENGTH_SHORT).show();
            }
        });
    }


    // Send notification
    private void sendNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "grove_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Grove Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }

    // Request location updates
    private void requestLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(5000)
                .setFastestInterval(2000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    checkProximity(location); // Check proximity
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String uid = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Fetch user role from Firestore
        db.collection("users").document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        String role = task.getResult().getString("role");
                        if ("worker".equals(role)) {
                            configureWorkerMode();
                        } else if ("admin".equals(role)) {
                            configureAdminMode();
                        } else {
                            Toast.makeText(this, "Unknown role. Please contact support.", Toast.LENGTH_SHORT).show();
                            FirebaseAuth.getInstance().signOut();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "Failed to fetch user role.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void configureWorkerMode() {
        // Disable map click to prevent adding groves
        mMap.setOnMapClickListener(null);

        // Customize marker click listener to only show labels
        mMap.setOnMarkerClickListener(marker -> {
            String groveTitle = marker.getTitle();
            String groveSnippet = marker.getSnippet();

            // Show grove details in a toast or as an info window
            Toast.makeText(this, groveTitle + "\n" + groveSnippet, Toast.LENGTH_SHORT).show();
            marker.showInfoWindow(); // Optionally display the info window on the map
            return true; // Indicate the event is handled
        });

        // Enable proximity tracking for workers
        requestLocationUpdates();
        Toast.makeText(this, "You are logged in as a worker.", Toast.LENGTH_SHORT).show();
    }



    private void configureAdminMode() {
        mMap.setOnMapClickListener(latLng -> openAddGroveDialog(latLng)); // Enable adding groves
        mMap.setOnMarkerClickListener(marker -> {
            String groveId = (String) marker.getTag();
            if (groveId != null) {
                openEditGroveDialog(groveId, marker); // Enable editing groves
            }
            return true;
        });
        Toast.makeText(this, "You are logged in as an admin.", Toast.LENGTH_SHORT).show();
    }





}
