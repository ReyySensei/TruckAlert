package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
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

        try {
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoInput(true);
            connection.connect();
            inputStream = connection.getInputStream();

            MJPEGInputStream mjpegStream = new MJPEGInputStream(inputStream);

            while (running) {
                Bitmap frame = mjpegStream.readMJPEGFrame();
                if (frame == null) continue;

                // Draw frame on the TextureView
                if (textureView.isAvailable()) {
                    Canvas canvas = textureView.lockCanvas();
                    if (canvas != null) {
                        Rect dest = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                        canvas.drawBitmap(frame, null, dest, null);
                        textureView.unlockCanvasAndPost(canvas);
                    }
                }

                // Run detection on this camera independently
                activity.runDetection(frame, isFrontCam);

                // Small delay to prevent overloading the CPU
                Thread.sleep(50);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (connection != null) connection.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public void stopStream() {
        running = false;
        interrupt();
    }
}
