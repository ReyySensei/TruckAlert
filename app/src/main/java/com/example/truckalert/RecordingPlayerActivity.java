package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.widget.Button;
import android.util.Log;
import android.view.TextureView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RecordingPlayerActivity extends AppCompatActivity {

    private TextureView videoView;
    private String fileUrl;
    private volatile boolean playing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_player);

        videoView = findViewById(R.id.videoView);

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        fileUrl = getIntent().getStringExtra("url");

        new Thread(this::playMJPEG).start();
    }

    private void playMJPEG() {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            InputStream is = conn.getInputStream();
            MJPEGInputStream mjpeg = new MJPEGInputStream(is);

            while (playing) {
                Bitmap frame = mjpeg.readMJPEGFrame();
                if (frame != null) drawFrame(frame);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawFrame(Bitmap bmp) {
        if (!videoView.isAvailable()) return;

        Canvas c = videoView.lockCanvas();
        if (c != null) {
            Rect dest = new Rect(0, 0, c.getWidth(), c.getHeight());
            c.drawBitmap(bmp, null, dest, null);
            videoView.unlockCanvasAndPost(c);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playing = false;
    }
}

