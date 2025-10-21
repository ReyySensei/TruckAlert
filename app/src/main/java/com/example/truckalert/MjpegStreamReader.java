package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.TextureView;
import android.graphics.Canvas;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MjpegStreamReader extends Thread {

    private final String streamUrl;
    private final TextureView textureView;
    private volatile boolean running = true;

    public MjpegStreamReader(String url, TextureView tv) {
        streamUrl = url;
        textureView = tv;
    }

    @Override
    public void run() {
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(5000);
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.connect();

            inputStream = connection.getInputStream();
            MJPEGInputStream mjpegInputStream = new MJPEGInputStream(inputStream);

            while (running) {
                final Bitmap frame = mjpegInputStream.readMJPEGFrame();
                if (frame != null && textureView.isAvailable()) {
                    Canvas canvas = textureView.lockCanvas();
                    if (canvas != null) {
                        canvas.drawBitmap(frame, 0, 0, null);
                        textureView.unlockCanvasAndPost(canvas);
                    }
                }
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
