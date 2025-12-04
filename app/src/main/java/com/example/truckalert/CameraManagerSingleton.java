package com.example.truckalert;

import android.view.TextureView;

public class CameraManagerSingleton {

    private static CameraManagerSingleton instance;

    private MJPEGDecoder activeStream = null;

    private CameraManagerSingleton() {}

    public static synchronized CameraManagerSingleton getInstance() {
        if (instance == null) {
            instance = new CameraManagerSingleton();
        }
        return instance;
    }

    /**
     * Opens a single MJPEG stream for the full-screen activity
     */
    public synchronized void openCamera(String cameraId, TextureView textureView, OverlayView overlayView) {

        closeCamera(); // close any running stream

        String url;
        switch (cameraId.toLowerCase()) {
            case "left":
                url = "http://192.168.4.106:81/stream";
                break;
            case "right":
                url = "http://192.168.4.107:81/stream";
                break;
            case "front":
                url = "http://192.168.4.101:81/stream";
                break;
            case "back":
                url = "http://192.168.4.102:81/stream";
                break;
            default:
                return;
        }

        try {
            activeStream = new MJPEGDecoder(url, textureView, cameraId, null);
            activeStream.setOverlayView(overlayView);
            activeStream.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the current full-screen stream
     */
    public synchronized void closeCamera() {
        if (activeStream != null) {
            try {
                activeStream.stopStream();
            } catch (Exception ignored) {}
            activeStream = null;
        }
    }
}
