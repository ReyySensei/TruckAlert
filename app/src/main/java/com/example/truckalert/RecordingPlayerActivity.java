package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RecordingPlayerActivity extends AppCompatActivity {

    private static final String TAG = "RecordingPlayer";

    private TextureView videoView;
    private TextView statusText;
    private String fileUrl;

    private volatile boolean playing = false;
    private Thread playbackThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_player);

        videoView  = findViewById(R.id.videoView);
        statusText = findViewById(R.id.statusText);

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        fileUrl = getIntent().getStringExtra("url");

        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "No file URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Received URL: " + fileUrl);
        setStatus("Connecting...");

        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture s, int w, int h) {
                startPlayback();
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture s) {
                stopPlayback();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture s) {}
        });
    }

    private void startPlayback() {
        playing = true;
        playbackThread = new Thread(this::tryPlayback);
        playbackThread.start();
    }

    private void stopPlayback() {
        playing = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
    }

    /**
     * Try port 80 first, if 404 then try port 81.
     * Also tries /playback/ prefix if bare filename fails.
     */
    private void tryPlayback() {
        // Build candidate URLs to try in order
        String[] candidates = buildCandidateUrls(fileUrl);

        for (String url : candidates) {
            if (!playing) return;

            Log.d(TAG, "Trying URL: " + url);
            setStatus("Trying: " + url);

            int code = getResponseCode(url);
            Log.d(TAG, "Response code for " + url + " → " + code);

            if (code == 200) {
                Log.d(TAG, "✅ Found working URL: " + url);
                playMJPEG(url);
                return;
            }
        }

        // None worked
        Log.e(TAG, "❌ All URLs returned 404 or error");
        setStatus("Error: File not found on camera");
        runOnUiThread(() ->
                Toast.makeText(this, "Recording not found on camera", Toast.LENGTH_LONG).show()
        );
    }

    /**
     * Given a URL like http://192.168.4.101/record_xxx.mjpeg,
     * build variants with port 80, port 81, and /playback/ prefix.
     */
    private String[] buildCandidateUrls(String originalUrl) {
        try {
            URL parsed = new URL(originalUrl);
            String host = parsed.getHost();         // 192.168.4.101
            String path = parsed.getPath();          // /record_xxx.mjpeg

            String base80 = "http://" + host;       // port 80 (default)
            String base81 = "http://" + host + ":81"; // port 81

            return new String[]{
                    base80 + path,                       // http://ip/record_xxx.mjpeg
                    base81 + path,                       // http://ip:81/record_xxx.mjpeg
                    base80 + "/playback" + path,         // http://ip/playback/record_xxx.mjpeg
                    base81 + "/playback" + path,         // http://ip:81/playback/record_xxx.mjpeg
                    base80 + "/sdcard"   + path,         // http://ip/sdcard/record_xxx.mjpeg
                    base81 + "/sdcard"   + path,         // http://ip:81/sdcard/record_xxx.mjpeg
            };
        } catch (Exception e) {
            Log.e(TAG, "URL parse error: " + e.getMessage());
            return new String[]{ originalUrl };
        }
    }

    /** Quick HEAD/GET just to check response code */
    private int getResponseCode(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            return conn.getResponseCode();
        } catch (Exception e) {
            Log.e(TAG, "getResponseCode error: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Stream and render MJPEG frames from the given URL */
    private void playMJPEG(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.connect();

            is = conn.getInputStream();
            MJPEGInputStream mjpegStream = new MJPEGInputStream(is);

            setStatus("Playing...");
            int frameCount = 0;

            while (playing && !Thread.currentThread().isInterrupted()) {
                Bitmap frame = mjpegStream.readMJPEGFrame();

                if (frame == null) {
                    Log.i(TAG, "End of stream after " + frameCount + " frames");
                    setStatus("Playback finished (" + frameCount + " frames)");
                    break;
                }

                drawFrame(frame);
                frameCount++;

                // ~15 FPS
                Thread.sleep(66);
            }

        } catch (InterruptedException e) {
            Log.d(TAG, "Playback interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Playback error: " + e.getMessage(), e);
            setStatus("Playback error: " + e.getMessage());
        } finally {
            try { if (is   != null) is.close();        } catch (Exception ignored) {}
            try { if (conn != null) conn.disconnect();  } catch (Exception ignored) {}
        }
    }

    private void drawFrame(Bitmap bmp) {
        if (videoView == null || !videoView.isAvailable()) return;
        try {
            Canvas c = videoView.lockCanvas();
            if (c != null) {
                Rect dest = new Rect(0, 0, c.getWidth(), c.getHeight());
                c.drawBitmap(bmp, null, dest, null);
                videoView.unlockCanvasAndPost(c);
            }
        } catch (Exception e) {
            Log.e(TAG, "Draw error", e);
        }
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> {
            if (statusText != null) statusText.setText(msg);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
    }
}