package com.example.truckalert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    Button cameraButton, recordingButton, logoutButton;
    TextView welcomeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        cameraButton = findViewById(R.id.cameraButton);
        recordingButton = findViewById(R.id.recordingButton);

        cameraButton.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, CameraSensorsActivity.class)));

        recordingButton.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, RecordingActivity.class)));

    }
}
