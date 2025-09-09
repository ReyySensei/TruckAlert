package com.example.truckalert;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ProfileActivity extends AppCompatActivity {

    TextView profileName, profileEmail;
    Button backButton;
    ListView videosListView;

    ArrayList<String> videoList;
    // later: ArrayAdapter<String> adapter;

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Views
        profileName = findViewById(R.id.profileName);
        profileEmail = findViewById(R.id.profileEmail);
        backButton = findViewById(R.id.backButton);
        videosListView = findViewById(R.id.videosListView);

        // Firebase setup
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        // Load user info from DB
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();

            databaseReference.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);

                        profileName.setText("Name: " + (name != null ? name : "N/A"));
                        profileEmail.setText("Email: " + (email != null ? email : "N/A"));
                    } else {
                        Toast.makeText(ProfileActivity.this, "No profile data found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Toast.makeText(ProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Back Button
        backButton.setOnClickListener(v -> finish());

        // Example: Initialize empty video list (later you can load actual recorded videos)
        videoList = new ArrayList<>();
        videoList.add("Accident_2025-01-01.mp4"); // dummy example
        videoList.add("Accident_2025-01-02.mp4"); // dummy example
        // adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoList);
        // videosListView.setAdapter(adapter);
    }
}
