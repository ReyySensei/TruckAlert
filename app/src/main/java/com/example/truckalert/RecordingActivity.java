package com.example.truckalert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.RequestQueue;

import java.util.ArrayList;

public class RecordingActivity extends AppCompatActivity {

    private static final String TAG = "RecordingActivity";

    Button backButton;
    ListView videosListView;
    ArrayList<String> videoList;
    ArrayAdapter<String> adapter;

    RequestQueue requestQueue;

    // Camera IPs
    private final String FRONT_CAM = "http://192.168.4.101";
    private final String BACK_CAM  = "http://192.168.4.102";
    private final String LEFT_CAM  = "http://192.168.4.106";
    private final String RIGHT_CAM = "http://192.168.4.107";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        backButton = findViewById(R.id.backButton);
        videosListView = findViewById(R.id.videosListView);

        backButton.setOnClickListener(v -> finish());

        requestQueue = Volley.newRequestQueue(getApplicationContext());

        videoList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoList);
        videosListView.setAdapter(adapter);

        // ⭐ NEW ⭐ — Add click listener for playback
        videosListView.setOnItemClickListener((parent, view, position, id) -> {
            String item = videoList.get(position);

            // Ignore section headers and separators
            if (item.startsWith("====") || item.startsWith("--")) return;

            // Format: "BACK – record_1701234567.mjpeg"
            String[] parts = item.split("–");
            if (parts.length < 2) return;

            String camName  = parts[0].trim();   // BACK
            String fileName = parts[1].trim();   // record_xxx.mjpeg
            String camIp    = getCameraIP(camName);

            if (camIp == null) {
                Log.e(TAG, "Unknown camera: " + camName);
                return;
            }

            Intent intent = new Intent(RecordingActivity.this, RecordingPlayerActivity.class);

            // ✅ FIXED — correct playback URL
            // ESP32 returns names like "/record_xxx.mjpeg"
            // Correct final URL: http://IP/record_xxx.mjpeg
            if (fileName.startsWith("/")) {
                intent.putExtra("url", camIp + fileName);
            } else {
                intent.putExtra("url", camIp + "/" + fileName);
            }

            startActivity(intent);
        });

        loadAllRecordings();
    }

    /** Get camera IP */
    private String getCameraIP(String camName) {
        switch (camName) {
            case "BACK":  return BACK_CAM;
            case "FRONT": return FRONT_CAM;
            case "LEFT":  return LEFT_CAM;
            case "RIGHT": return RIGHT_CAM;
        }
        return null;
    }

    /** Fetch all 4 cameras */
    private void loadAllRecordings() {
        videoList.clear();
        adapter.notifyDataSetChanged();

        fetchCameraList("BACK",  BACK_CAM  + "/list");
        fetchCameraList("FRONT", FRONT_CAM + "/list");
        fetchCameraList("LEFT",  LEFT_CAM  + "/list");
        fetchCameraList("RIGHT", RIGHT_CAM + "/list");
    }

    /** Fetch recordings for ONE camera and add separator line */
    private void fetchCameraList(String camName, String url) {
        Log.i(TAG, "Requesting: " + url);

        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> {
                    String[] files = response.trim().split("\n");

                    boolean headerAdded = false;

                    for (String f : files) {
                        f = f.trim();
                        if (f.length() > 0) {

                            if (!headerAdded) {
                                videoList.add("===== " + camName + " CAMERA =====");
                                headerAdded = true;
                            }

                            videoList.add(camName + " – " + f);
                        }
                    }

                    // Add separator ONLY if this camera has files
                    if (headerAdded) {
                        videoList.add("---------------------------------------");
                    }

                    adapter.notifyDataSetChanged();
                },
                error -> Log.e(TAG, "Failed to get list from " + camName + ": " + error)
        );

        requestQueue.add(req);
    }
}
