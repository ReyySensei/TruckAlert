package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.io.DataInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RecordingPlayerActivity extends AppCompatActivity {

    private TextureView videoView;
    private String fileUrl;
    private volatile boolean playing = true;

    private static final String TAG = "RecordingPlayer";

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


    /** ============================================================
     *  CUSTOM PLAYER FOR ESP32 MJPEG FORMAT:
     *  [4-byte frameSize][JPEG data]
     *  ============================================================
     */
    private void playMJPEG() {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            InputStream is = conn.getInputStream();
            DataInputStream dis = new DataInputStream(is);

            while (playing) {
                // Read frame size (4 bytes)
                int frameSize;
                try {
                    frameSize = dis.readInt();   // Big endian
                } catch (Exception e) {
                    Log.e(TAG, "End of file / readInt failed");
                    break;
                }

                if (frameSize <= 0 || frameSize > 200000) {
                    Log.e(TAG, "Invalid frame size: " + frameSize);
                    break;
                }

                // Read JPEG frame
                byte[] jpeg = new byte[frameSize];
                dis.readFully(jpeg);

                // Decode JPEG
                Bitmap frame = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                if (frame != null) drawFrame(frame);
            }

        } catch (Exception e) {
            Log.e(TAG, "Playback error", e);
        }
    }


    /** Draw a frame on the TextureView */
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
