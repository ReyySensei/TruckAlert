package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.TextureView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

public class MJPEGDecoder extends Thread {

    private final String streamUrl;
    private final TextureView textureView;
    private final String cameraId;                    // left, right, front, back
    private final CameraSensorsActivity activity;

    private volatile boolean running = true;

    private static final int RECONNECT_DELAY = 1500;  // 1.5 sec reconnect
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 3000;

    public MJPEGDecoder(String url, TextureView tv, String camId, CameraSensorsActivity act) {
        this.streamUrl = url;
        this.textureView = tv;
        this.cameraId = camId;
        this.activity = act;
    }

    @Override
    public void run() {
        while (running) {
            HttpURLConnection connection = null;
            InputStream inputStream = null;

            try {
                URL url = new URL(streamUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setUseCaches(false);
                connection.setDoInput(true);
                connection.setRequestProperty("Connection", "Keep-Alive");

                connection.connect();
                inputStream = connection.getInputStream();

                MJPEGInputStream mjpegStream = new MJPEGInputStream(inputStream);

                Log.i("MJPEG", "Stream connected: " + cameraId);

                int frameCount = 0;

                // ---- STREAM LOOP ----
                while (running) {

                    Bitmap frame = mjpegStream.readMJPEGFrame();
                    if (frame == null) continue;

                    drawFrame(frame);

                    frameCount++;
                    if (frameCount % 5 == 0) {
                        try {
                            Bitmap small = Bitmap.createScaledBitmap(frame, 320, 240, false);
                            activity.onMJPEGFrame(small, cameraId);
                        } catch (Exception ignore) {}
                    }
                }

            } catch (SocketTimeoutException tex) {
                Log.w("MJPEG", "Timeout (" + cameraId + "), reconnecting...");
            } catch (IOException io) {
                Log.w("MJPEG", "Stream IO error (" + cameraId + "): " + io.getMessage());
            } catch (Exception e) {
                Log.e("MJPEG", "Unexpected error (" + cameraId + "): " + e.getMessage());
            } finally {
                try { if (inputStream != null) inputStream.close(); } catch (Exception ignore) {}
                try { if (connection != null) connection.disconnect(); } catch (Exception ignore) {}

                if (running) {
                    Log.i("MJPEG", "Reconnecting stream: " + cameraId);
                    try { Thread.sleep(RECONNECT_DELAY); } catch (Exception ignore) {}
                }
            }
        }

        Log.i("MJPEG", "Stream stopped: " + cameraId);
    }

    private void drawFrame(Bitmap frame) {
        if (textureView == null || !textureView.isAvailable()) return;

        try {
            Canvas canvas = textureView.lockCanvas();
            if (canvas != null) {
                Rect dest = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                canvas.drawBitmap(frame, null, dest, null);
                textureView.unlockCanvasAndPost(canvas);
            }
        } catch (Exception ignore) {}
    }

    public void stopStream() {
        running = false;
        interrupt();
    }
}
