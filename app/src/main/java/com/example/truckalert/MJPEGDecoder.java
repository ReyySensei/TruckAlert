package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.TextureView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MJPEGDecoder extends Thread {

    private final String streamUrl;
    private final TextureView textureView;
    private final boolean isFrontCam;
    private final CameraSensorsActivity activity;

    private volatile boolean running = true;

    public MJPEGDecoder(String url, TextureView tv, boolean isFront, CameraSensorsActivity act) {
        this.streamUrl = url;
        this.textureView = tv;
        this.isFrontCam = isFront;
        this.activity = act;
    }

    @Override
    public void run() {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        int frameCount = 0; // ✅ Declare frame counter

        try {
            // ⚡ Faster connection setup
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.connect();

            inputStream = connection.getInputStream();
            MJPEGInputStream mjpegStream = new MJPEGInputStream(inputStream);

            Log.i("MJPEGDecoder", "✅ Stream started: " + streamUrl);

            while (running) {
                Bitmap frame = mjpegStream.readMJPEGFrame();
                if (frame == null) continue;

                // ✅ Draw latest frame to TextureView
                if (textureView.isAvailable()) {
                    Canvas canvas = textureView.lockCanvas();
                    if (canvas != null) {
                        Rect dest = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                        canvas.drawBitmap(frame, null, dest, null);
                        textureView.unlockCanvasAndPost(canvas);
                    }
                }

                // ✅ Run detection every 5th frame to prevent lag
                frameCount++;
                if (frameCount % 5 == 0) {
                    try {
                        Bitmap smallFrame = Bitmap.createScaledBitmap(frame, 320, 240, false);
                        activity.runDetection(smallFrame, isFrontCam);
                    } catch (Exception ex) {
                        Log.e("MJPEGDecoder", "Detection error: " + ex.getMessage());
                    }
                }

                // ⚡ No Thread.sleep() — keeps stream as fast as possible
            }

        } catch (Exception e) {
            Log.e("MJPEGDecoder", "Stream error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (connection != null) connection.disconnect();
            } catch (Exception ignored) {}
            Log.i("MJPEGDecoder", "❌ Stream stopped.");
        }
    }

    public void stopStream() {
        running = false;
        interrupt();
        Log.i("MJPEGDecoder", "⛔ Stop signal sent to MJPEG thread.");
    }
}
