package com.example.truckalert;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class RecordingActivity extends AppCompatActivity {

    Button backButton;
    ListView videosListView;

    ArrayList<String> videoList;
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        backButton = findViewById(R.id.backButton);
        videosListView = findViewById(R.id.videosListView);

        backButton.setOnClickListener(v -> finish());

        // Sample static list of recordings
        videoList = new ArrayList<>();
        videoList.add("Accident_2025-01-01.mp4");
        videoList.add("Accident_2025-01-02.mp4");
        videoList.add("Intruder_2025-01-03.mp4");

        // Display the recordings in a simple list view
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoList);
        videosListView.setAdapter(adapter);
    }
}
