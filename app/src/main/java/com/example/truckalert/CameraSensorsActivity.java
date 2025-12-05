package com.example.truckalert;

import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import android.content.Intent;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.util.List;

public class CameraSensorsActivity extends AppCompatActivity {

    private static final String TAG = "CameraSensorsActivity";
    private RequestQueue requestQueue;

    public TextureView leftCam, rightCam, frontCam, backCam;

    public OverlayView leftOverlay, rightOverlay, frontOverlay, backOverlay;

    public TextView leftSensor, rightSensor, backSensor;

    private ObjectDetectorHelper objectDetectorHelper;

    private HandlerThread frontInferenceThread, backInferenceThread, leftInferenceThread, rightInferenceThread;
    private Handler frontHandler, backHandler, leftHandler, rightHandler;

    private MJPEGDecoder leftStream, rightStream, frontStream, backStream;

    private final Handler sensorHandler = new Handler();

    private MediaPlayer leftAlertPlayer;
    private MediaPlayer rightAlertPlayer;
    private MediaPlayer backAlertPlayer;

    private final Object detectionLock = new Object();

    private final String LEFT_SENSOR_URL = "http://192.168.4.103/left";
    private final String BACK_SENSOR_URL = "http://192.168.4.104/back";
    private final String RIGHT_SENSOR_URL = "http://192.168.4.105/right";

    // CAMERA HOSTS (API 80)
    private final String LEFT_CAM_HOST  = "http://192.168.4.106";
    private final String RIGHT_CAM_HOST = "http://192.168.4.107";
    private final String FRONT_CAM_HOST = "http://192.168.4.101";
    private final String BACK_CAM_HOST  = "http://192.168.4.102";

    // STREAM URLs
    private final String LEFT_CAM_URL  = LEFT_CAM_HOST  + ":81/stream";
    private final String RIGHT_CAM_URL = RIGHT_CAM_HOST + ":81/stream";
    private final String FRONT_CAM_URL = FRONT_CAM_HOST + ":81/stream";
    private final String BACK_CAM_URL  = BACK_CAM_HOST  + ":81/stream";

    // Recording flags
    private boolean isRecordingBack = false;
    private boolean isRecordingFront = false;
    private boolean isRecordingLeft = false;
    private boolean isRecordingRight = false;

    private long backLastSeen = 0;
    private long frontLastSeen = 0;
    private long leftLastSeen = 0;
    private long rightLastSeen = 0;

    private static final long DETECTION_TIMEOUT = 2500;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_sensors);

        requestQueue = Volley.newRequestQueue(getApplicationContext());

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        leftCam = findViewById(R.id.leftCam);
        rightCam = findViewById(R.id.rightCam);
        frontCam = findViewById(R.id.frontCam);
        backCam = findViewById(R.id.backCam);

        leftCam.setOnClickListener(v -> openFullScreen("left"));
        rightCam.setOnClickListener(v -> openFullScreen("right"));
        frontCam.setOnClickListener(v -> openFullScreen("front"));
        backCam.setOnClickListener(v -> openFullScreen("back"));

        leftOverlay = findViewById(R.id.leftOverlay);
        rightOverlay = findViewById(R.id.rightOverlay);
        frontOverlay = findViewById(R.id.frontOverlay);
        backOverlay = findViewById(R.id.backOverlay);

        leftSensor = findViewById(R.id.leftSensor);
        rightSensor = findViewById(R.id.rightSensor);
        backSensor  = findViewById(R.id.backSensor);

        leftAlertPlayer  = MediaPlayer.create(this, R.raw.beep);
        rightAlertPlayer = MediaPlayer.create(this, R.raw.beep);
        backAlertPlayer  = MediaPlayer.create(this, R.raw.beep);

        try {
            objectDetectorHelper = new ObjectDetectorHelper(this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize ObjectDetectorHelper", e);
            finish();
            return;
        }

        frontInferenceThread = new HandlerThread("FrontInferenceThread");
        frontInferenceThread.start();
        frontHandler = new Handler(frontInferenceThread.getLooper());

        backInferenceThread = new HandlerThread("BackInferenceThread");
        backInferenceThread.start();
        backHandler = new Handler(backInferenceThread.getLooper());

        leftInferenceThread = new HandlerThread("LeftInferenceThread");
        leftInferenceThread.start();
        leftHandler = new Handler(leftInferenceThread.getLooper());

        rightInferenceThread = new HandlerThread("RightInferenceThread");
        rightInferenceThread.start();
        rightHandler = new Handler(rightInferenceThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMJPEGStreams();
        startSensorPolling();

        // 🚀 Auto start recording on ALL cams
        startRecording(FRONT_CAM_HOST);
        startRecording(BACK_CAM_HOST);
        startRecording(LEFT_CAM_HOST);
        startRecording(RIGHT_CAM_HOST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMJPEGStreams();
        stopSensorPolling();

        // 🛑 Stop recordings when leaving screen
        stopRecording(FRONT_CAM_HOST);
        stopRecording(BACK_CAM_HOST);
        stopRecording(LEFT_CAM_HOST);
        stopRecording(RIGHT_CAM_HOST);

        if (objectDetectorHelper != null) {
            objectDetectorHelper.stopAlertSound();
        }
    }

    private void openFullScreen(String cameraId) {
        Intent intent = new Intent(this, FullScreenCameraActivity.class);
        intent.putExtra("cameraId", cameraId);
        startActivity(intent);
    }

    // -----------------------------
    //  RECORDING CONTROL FIXED
    // -----------------------------
    private void startRecording(String camHost) {
        StringRequest req = new StringRequest(Request.Method.GET,
                camHost + "/start_record",
                r -> Log.i(TAG, "START RECORD OK: " + camHost),
                e -> Log.e(TAG, "START RECORD FAIL: " + camHost));
        requestQueue.add(req);
    }

    private void stopRecording(String camHost) {
        StringRequest req = new StringRequest(Request.Method.GET,
                camHost + "/stop_record",
                r -> Log.i(TAG, "STOP RECORD OK: " + camHost),
                e -> Log.e(TAG, "STOP RECORD FAIL: " + camHost));
        requestQueue.add(req);
    }

    // CAMERA STREAMS
    private synchronized void startMJPEGStreams() {
        if ((leftStream != null && leftStream.isAlive()) ||
                (rightStream != null && rightStream.isAlive()) ||
                (frontStream != null && frontStream.isAlive()) ||
                (backStream != null && backStream.isAlive())) {
            return;
        }

        leftStream  = new MJPEGDecoder(LEFT_CAM_URL,  leftCam,  "left",  this);
        rightStream = new MJPEGDecoder(RIGHT_CAM_URL, rightCam, "right", this);
        frontStream = new MJPEGDecoder(FRONT_CAM_URL, frontCam, "front", this);
        backStream  = new MJPEGDecoder(BACK_CAM_URL,  backCam,  "back",  this);

        leftStream.start();
        rightStream.start();
        frontStream.start();
        backStream.start();
    }

    private synchronized void stopMJPEGStreams() {
        try { if (leftStream != null)  leftStream.stopStream(); } catch (Exception ignored) {}
        try { if (rightStream != null) rightStream.stopStream(); } catch (Exception ignored) {}
        try { if (frontStream != null) frontStream.stopStream(); } catch (Exception ignored) {}
        try { if (backStream != null)  backStream.stopStream(); } catch (Exception ignored) {}

        leftStream = rightStream = frontStream = backStream = null;
    }

    // FRAME PROCESSING
    public void onMJPEGFrame(Bitmap bitmap, String cameraId) {
        if (bitmap == null) return;

        switch (cameraId) {
            case "front": runDetectionOnHandler(bitmap, frontHandler, frontOverlay, frontCam, "front"); break;
            case "back":  runDetectionOnHandler(bitmap, backHandler,  backOverlay,  backCam,  "back");  break;
            case "left":  runDetectionOnHandler(bitmap, leftHandler,  leftOverlay,  leftCam,  "left");  break;
            case "right": runDetectionOnHandler(bitmap, rightHandler, rightOverlay, rightCam, "right"); break;
        }
    }

    private void runDetectionOnHandler(Bitmap bitmap, Handler handler,
                                       OverlayView overlay, TextureView camView, String cameraId) {

        handler.post(() -> {
            synchronized (detectionLock) {

                List<OverlayView.Recognition> results = objectDetectorHelper.detectObjects(bitmap);
                boolean detected = results != null && !results.isEmpty();
                long now = System.currentTimeMillis();

                switch (cameraId) {
                    case "front":  frontLastSeen = detected ? now : frontLastSeen; break;
                    case "back":   backLastSeen  = detected ? now : backLastSeen;  break;
                    case "left":   leftLastSeen  = detected ? now : leftLastSeen;  break;
                    case "right":  rightLastSeen = detected ? now : rightLastSeen; break;
                }

                runOnUiThread(() -> {
                    if (overlay != null && camView != null) {
                        overlay.setDetections(
                                results,
                                bitmap.getWidth(), bitmap.getHeight(),
                                camView.getWidth(), camView.getHeight(),
                                cameraId.equals("front") || cameraId.equals("left")
                        );
                    }
                });
            }
        });
    }


    // SENSOR SYSTEM… (unchanged)

    private void startSensorPolling() {
        sensorHandler.removeCallbacksAndMessages(null);

        Runnable task = new Runnable() {
            @Override
            public void run() {
                fetchDualSensorData(LEFT_SENSOR_URL, leftSensor, 30, leftAlertPlayer);
                fetchSensorData(BACK_SENSOR_URL, backSensor, 30, backAlertPlayer);
                fetchDualSensorData(RIGHT_SENSOR_URL, rightSensor, 30, rightAlertPlayer);
                sensorHandler.postDelayed(this, 600);
            }
        };
        sensorHandler.post(task);
    }

    private void stopSensorPolling() {
        sensorHandler.removeCallbacksAndMessages(null);
    }

    private void fetchDualSensorData(String url, TextView targetView, int safe, MediaPlayer alert) {
        StringRequest req = new StringRequest(Request.Method.GET, url,
                r -> {
                    try {
                        String[] parts = r.trim().split(",");
                        int a = Integer.parseInt(parts[0].trim());
                        int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : -1;

                        int nearest = (b > 0 && b < a) ? b : a;
                        targetView.setText(getLabelFromTextView(targetView));

                        if (nearest <= safe) {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                            playIndependentAlert(alert);
                        } else {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                            stopIndependentAlert(alert);
                        }
                    } catch (Exception ex) { }
                },
                e -> {});

        requestQueue.add(req);
    }

    private void fetchSensorData(String url, TextView targetView, int safe, MediaPlayer alert) {
        StringRequest req = new StringRequest(Request.Method.GET, url,
                r -> {
                    try {
                        int d = Integer.parseInt(r.trim());
                        targetView.setText(getLabelFromTextView(targetView));

                        if (d <= safe) {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                            playIndependentAlert(alert);
                        } else {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                            stopIndependentAlert(alert);
                        }
                    } catch (Exception ignored) {}
                },
                e -> {});
        requestQueue.add(req);
    }

    private void playIndependentAlert(MediaPlayer p) {
        if (p != null) {
            p.setLooping(true);
            if (!p.isPlaying()) p.start();
        }
    }

    private void stopIndependentAlert(MediaPlayer p) {
        if (p != null && p.isPlaying()) {
            p.pause();
            p.seekTo(0);
        }
    }

    private String getLabelFromTextView(TextView v) {
        String id = getResources().getResourceEntryName(v.getId());
        switch (id) {
            case "leftSensor": return "Left";
            case "rightSensor": return "Right";
            case "backSensor": return "Back";
        }
        return "Sensor";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopMJPEGStreams();
        stopSensorPolling();

        if (leftAlertPlayer != null)  leftAlertPlayer.release();
        if (rightAlertPlayer != null) rightAlertPlayer.release();
        if (backAlertPlayer != null)  backAlertPlayer.release();

        if (objectDetectorHelper != null) objectDetectorHelper.release();

        if (frontInferenceThread != null) frontInferenceThread.quitSafely();
        if (backInferenceThread != null)  backInferenceThread.quitSafely();
        if (leftInferenceThread != null)  leftInferenceThread.quitSafely();
        if (rightInferenceThread != null) rightInferenceThread.quitSafely();

        if (requestQueue != null) requestQueue.stop();
    }
}
