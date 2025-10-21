package com.example.truckalert;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 1000; // 1 second

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            // Directly go to HomeActivity (no Firebase login check)
            startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            finish();
        }, SPLASH_DURATION);
    }
}
