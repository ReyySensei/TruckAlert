package com.example.truckalert;

import android.os.Bundle;
import android.view.TextureView;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class FullScreenCameraActivity extends AppCompatActivity {

    private TextureView fullCam;
    private OverlayView fullOverlay;
    private ImageButton exitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_camera);

        fullCam = findViewById(R.id.fullScreenCam);
        fullOverlay = findViewById(R.id.fullScreenOverlay);
        exitButton = findViewById(R.id.exitFull);

        // get which camera to open
        String cameraId = getIntent().getStringExtra("cameraId");
        if (cameraId == null) cameraId = "LEFT";

        // open the camera
        CameraManagerSingleton.getInstance().openCamera(cameraId, fullCam, fullOverlay);

        // exit button
        exitButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraManagerSingleton.getInstance().closeCamera();
    }
}
