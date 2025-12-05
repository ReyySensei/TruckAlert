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

/**
 * CameraSensorsActivity — updated to work with the new, rewritten MJPEGDecoder (cameraId-based).
 * Key improvements:
 * - Uses MJPEGDecoder(url, textureView, cameraId, activity) for all 4 cameras
 * - Starts streams in onResume() and stops in onPause() to manage resources properly
 * - Per-camera inference runs on dedicated HandlerThreads
 * - Detection is still throttled (every 5th frame inside decoder) — activity receives scaled bitmaps
 * - Robust cleanup in onDestroy()
 * NOTE:
 * - Update the LEFT/RIGHT URL placeholders below to your actual ESP32 endpoints.
 * - Make sure R.raw.beep exists or replace it with your sound resource.
 * - MJPEGDecoder must call activity.onMJPEGFrame(bitmap, cameraId) (this is how the decoder and activity communicate).
 */
public class CameraSensorsActivity extends AppCompatActivity {

    private static final String TAG = "CameraSensorsActivity";
    private RequestQueue requestQueue;
    // TextureViews (camera previews)
    public TextureView leftCam, rightCam, frontCam, backCam;

    // Overlays
    public OverlayView leftOverlay, rightOverlay, frontOverlay, backOverlay;

    // Sensor TextViews
    public TextView leftSensor, rightSensor, backSensor;

    // Object detector helper
    private ObjectDetectorHelper objectDetectorHelper;

    // Per-camera inference threads + handlers
    private HandlerThread frontInferenceThread, backInferenceThread, leftInferenceThread, rightInferenceThread;
    private Handler frontHandler, backHandler, leftHandler, rightHandler;

    // MJPEG streams (optimized decoder)
    private MJPEGDecoder leftStream, rightStream, frontStream, backStream;

    // Sensor polling handler
    private final Handler sensorHandler = new Handler();

    // Independent MediaPlayers for each sensor
    private MediaPlayer leftAlertPlayer;
    private MediaPlayer rightAlertPlayer;
    private MediaPlayer backAlertPlayer;

    // Lock for detection
    private final Object detectionLock = new Object();

    // Sensor URLs (replace with your actual endpoints)
    private final String LEFT_SENSOR_URL = "http://192.168.4.103/left";
    private final String BACK_SENSOR_URL = "http://192.168.4.104/back";
    private final String RIGHT_SENSOR_URL = "http://192.168.4.105/right";

    // =======================
    // CAMERA HOSTS (API – port 80)
    // =======================
    private final String LEFT_CAM_HOST  = "http://192.168.4.106";
    private final String RIGHT_CAM_HOST = "http://192.168.4.107";
    private final String FRONT_CAM_HOST = "http://192.168.4.101";
    private final String BACK_CAM_HOST  = "http://192.168.4.102";

    // =======================
    // CAMERA STREAMS (MJPEG – port 81)
    // =======================
    private final String LEFT_CAM_URL  = LEFT_CAM_HOST  + ":81/stream";
    private final String RIGHT_CAM_URL = RIGHT_CAM_HOST + ":81/stream";
    private final String FRONT_CAM_URL = FRONT_CAM_HOST + ":81/stream";
    private final String BACK_CAM_URL  = BACK_CAM_HOST  + ":81/stream";


    // Recording flags per camera
    private boolean isRecordingBack = false;
    private long backLastSeen = 0;

    private boolean isRecordingFront = false;
    private long frontLastSeen = 0;

    private boolean isRecordingLeft = false;
    private long leftLastSeen = 0;

    private boolean isRecordingRight = false;
    private long rightLastSeen = 0;

    // Detection timeout (ms) before stopping recording
    private static final long DETECTION_TIMEOUT = 2500;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_sensors);

        // Initialize a single Volley RequestQueue for this activity
        requestQueue = Volley.newRequestQueue(getApplicationContext());

        // Back button
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Find camera TextureViews (IDs must match your XML)
        leftCam = findViewById(R.id.leftCam);
        rightCam = findViewById(R.id.rightCam);
        frontCam = findViewById(R.id.frontCam);
        backCam = findViewById(R.id.backCam);

        // --- Full-screen tap listeners ---
        leftCam.setOnClickListener(v -> openFullScreen("left"));
        rightCam.setOnClickListener(v -> openFullScreen("right"));
        frontCam.setOnClickListener(v -> openFullScreen("front"));
        backCam.setOnClickListener(v -> openFullScreen("back"));

        // Find overlays
        leftOverlay = findViewById(R.id.leftOverlay);
        rightOverlay = findViewById(R.id.rightOverlay);
        frontOverlay = findViewById(R.id.frontOverlay);
        backOverlay = findViewById(R.id.backOverlay);

        // Sensors
        leftSensor = findViewById(R.id.leftSensor);
        rightSensor = findViewById(R.id.rightSensor);
        backSensor = findViewById(R.id.backSensor);

        // MediaPlayers for beeps (R.raw.beep must exist)
        leftAlertPlayer  = MediaPlayer.create(this, R.raw.beep);
        rightAlertPlayer = MediaPlayer.create(this, R.raw.beep);
        backAlertPlayer  = MediaPlayer.create(this, R.raw.beep);

        // Initialize object detector
        try {
            objectDetectorHelper = new ObjectDetectorHelper(this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize ObjectDetectorHelper", e);
            finish();
            return;
        }

        // Create inference threads
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

    // Start streams when activity becomes visible
    @Override
    protected void onResume() {
        super.onResume();
        startMJPEGStreams();
        startSensorPolling();
    }

    // Stop streams when activity is no longer in foreground
    @Override
    protected void onPause() {
        super.onPause();
        stopMJPEGStreams();
        stopSensorPolling();

        // 🔇 Make sure titit.mp3 from ObjectDetectorHelper stops when leaving screen
        if (objectDetectorHelper != null) {
            objectDetectorHelper.stopAlertSound();
        }
    }

    private void openFullScreen(String cameraId) {
        Intent intent = new Intent(this, FullScreenCameraActivity.class);
        intent.putExtra("cameraId", cameraId);
        startActivity(intent);
    }

    private void startRecording(String camHost) {
        String url = camHost + "/start_record";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> Log.i(TAG, "Recording started: " + camHost),
                error -> Log.e(TAG, "Failed to start recording: " + camHost));
        requestQueue.add(request);
    }

    private void stopRecording(String camHost) {
        String url = camHost + "/stop_record";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> Log.i(TAG, "Recording stopped: " + camHost),
                error -> Log.e(TAG, "Failed to stop recording: " + camHost));
        requestQueue.add(request);
    }

    /**
     * Start MJPEG streams for all 4 cameras.
     * Uses the new MJPEGDecoder(url, textureView, cameraId, activity) constructor.
     */
    private synchronized void startMJPEGStreams() {
        // Prevent double-start
        if ((leftStream != null && leftStream.isAlive()) ||
                (rightStream != null && rightStream.isAlive()) ||
                (frontStream != null && frontStream.isAlive()) ||
                (backStream != null && backStream.isAlive())) {
            Log.i(TAG, "Streams already running");
            return;
        }

        try {
            leftStream  = new MJPEGDecoder(LEFT_CAM_URL,  leftCam,  "left",  this);
            rightStream = new MJPEGDecoder(RIGHT_CAM_URL, rightCam, "right", this);
            frontStream = new MJPEGDecoder(FRONT_CAM_URL, frontCam, "front", this);
            backStream  = new MJPEGDecoder(BACK_CAM_URL,  backCam,  "back",  this);

            leftStream.start();
            rightStream.start();
            frontStream.start();
            backStream.start();

            Log.i(TAG, "All MJPEG streams started.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MJPEG streams", e);
        }
    }

    private synchronized void stopMJPEGStreams() {
        try {
            if (leftStream != null)  { leftStream.stopStream();  leftStream = null; }
        } catch (Exception ignored) {}
        try {
            if (rightStream != null) { rightStream.stopStream(); rightStream = null; }
        } catch (Exception ignored) {}
        try {
            if (frontStream != null) { frontStream.stopStream(); frontStream = null; }
        } catch (Exception ignored) {}
        try {
            if (backStream != null)  { backStream.stopStream();  backStream = null; }
        } catch (Exception ignored) {}
        Log.i(TAG, "All MJPEG streams stopped.");
    }

    /**
     * Called by MJPEGDecoder when a (scaled) frame is available.
     * cameraId will be "left", "right", "front" or "back".
     */
    public void onMJPEGFrame(Bitmap bitmap, String cameraId) {
        if (bitmap == null || cameraId == null) return;

        switch (cameraId) {
            case "front":
                runDetectionOnHandler(bitmap, frontHandler, frontOverlay, frontCam, "front");
                break;
            case "back":
                runDetectionOnHandler(bitmap, backHandler, backOverlay, backCam, "back");
                break;
            case "left":
                runDetectionOnHandler(bitmap, leftHandler, leftOverlay, leftCam, "left");
                break;
            case "right":
                runDetectionOnHandler(bitmap, rightHandler, rightOverlay, rightCam, "right");
                break;
            default:
                // unknown camera id
                break;
        }
    }

    /**
     * Routes detection onto a handler so detection runs off the UI thread.
     * Expectation: the bitmap passed is already scaled (decoder scales every Nth frame).
     */
    private void runDetectionOnHandler(Bitmap bitmap, Handler handler,
                                       OverlayView overlay, TextureView camView, String cameraId) {

        if (bitmap == null || handler == null || objectDetectorHelper == null) return;

        handler.post(() -> {
            synchronized (detectionLock) {

                try {
                    long start = System.currentTimeMillis();
                    List<OverlayView.Recognition> results = objectDetectorHelper.detectObjects(bitmap);
                    long time = System.currentTimeMillis() - start;
                    Log.d(TAG, cameraId + " detection took: " + time + "ms");

                    // ---- YOLO RECORDING TRIGGER LOGIC ----
                    boolean detected = (results != null && !results.isEmpty());
                    long now = System.currentTimeMillis();

                    switch (cameraId) {

                        case "back":
                            if (detected) {
                                backLastSeen = now;
                                if (!isRecordingBack) {
                                    isRecordingBack = true;
                                    startRecording(BACK_CAM_HOST);
                                    Log.i(TAG, "BACK: Recording started");
                                }
                            } else if (isRecordingBack && now - backLastSeen > DETECTION_TIMEOUT) {
                                isRecordingBack = false;
                                stopRecording(BACK_CAM_HOST);
                                Log.i(TAG, "BACK: Recording stopped");
                            }
                            break;

                        case "front":
                            if (detected) {
                                frontLastSeen = now;
                                if (!isRecordingFront) {
                                    isRecordingFront = true;
                                    startRecording(FRONT_CAM_HOST);
                                    Log.i(TAG, "FRONT: Recording started");
                                }
                            } else if (isRecordingFront && now - frontLastSeen > DETECTION_TIMEOUT) {
                                isRecordingFront = false;
                                stopRecording(FRONT_CAM_HOST);
                                Log.i(TAG, "FRONT: Recording stopped");
                            }
                            break;

                        case "left":
                            if (detected) {
                                leftLastSeen = now;
                                if (!isRecordingLeft) {
                                    isRecordingLeft = true;
                                    startRecording(LEFT_CAM_HOST);
                                    Log.i(TAG, "LEFT: Recording started");
                                }
                            } else if (isRecordingLeft && now - leftLastSeen > DETECTION_TIMEOUT) {
                                isRecordingLeft = false;
                                stopRecording(LEFT_CAM_HOST);
                                Log.i(TAG, "LEFT: Recording stopped");
                            }
                            break;

                        case "right":
                            if (detected) {
                                rightLastSeen = now;
                                if (!isRecordingRight) {
                                    isRecordingRight = true;
                                    startRecording(RIGHT_CAM_HOST);
                                    Log.i(TAG, "RIGHT: Recording started");
                                }
                            } else if (isRecordingRight && now - rightLastSeen > DETECTION_TIMEOUT) {
                                isRecordingRight = false;
                                stopRecording(RIGHT_CAM_HOST);
                                Log.i(TAG, "RIGHT: Recording stopped");
                            }
                            break;
                    }
                    // ---- END RECORDING TRIGGER LOGIC ----


                    // ---- DRAW OVERLAY ON UI THREAD ----
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

                } catch (Exception e) {
                    Log.e(TAG, "Detection error on " + cameraId, e);
                }
            }
        });
    }


    /*** ---- SENSOR POLLING ----***/
    private void startSensorPolling() {
        // If already running, skip
        sensorHandler.removeCallbacksAndMessages(null);

        Runnable sensorTask = new Runnable() {
            @Override
            public void run() {
                fetchDualSensorData(LEFT_SENSOR_URL, leftSensor, 30, leftAlertPlayer);
                fetchSensorData(BACK_SENSOR_URL, backSensor, 30, backAlertPlayer);
                fetchDualSensorData(RIGHT_SENSOR_URL, rightSensor, 30, rightAlertPlayer);
                sensorHandler.postDelayed(this, 600);
            }
        };
        sensorHandler.post(sensorTask);
    }

    private void stopSensorPolling() {
        sensorHandler.removeCallbacksAndMessages(null);
    }

    private void fetchDualSensorData(String url, TextView targetView, int safeDistance, MediaPlayer alertPlayer) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        String[] parts = response.trim().split(",");
                        int dist1 = Integer.parseInt(parts[0].trim());
                        int dist2 = (parts.length > 1) ? Integer.parseInt(parts[1].trim()) : -1;
                        int nearest = (dist2     > 0 && dist2 < dist1) ? dist2 : dist1;

                        targetView.setText(getLabelFromTextView(targetView));

                        if (nearest <= safeDistance) {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                            playIndependentAlert(alertPlayer);
                        } else {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                            stopIndependentAlert(alertPlayer);
                        }
                    } catch (Exception e) {
                        targetView.setText(getLabelFromTextView(targetView));
                        targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                    }
                },
                error -> {
                    targetView.setText(getLabelFromTextView(targetView));
                    targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                });

        request.setRetryPolicy(new DefaultRetryPolicy(3000, 2, 1.5f));
        requestQueue.add(request);
    }

    private void fetchSensorData(String url, TextView targetView, int safeDistance, MediaPlayer alertPlayer) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        int distance = Integer.parseInt(response.trim());
                        targetView.setText(getLabelFromTextView(targetView));

                        if (distance <= safeDistance) {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                            playIndependentAlert(alertPlayer);
                        } else {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                            stopIndependentAlert(alertPlayer);
                        }
                    } catch (NumberFormatException e) {
                        targetView.setText(getLabelFromTextView(targetView));
                        targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                    }
                },
                error -> {
                    targetView.setText(getLabelFromTextView(targetView));
                    targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                });

        request.setRetryPolicy(new DefaultRetryPolicy(3000, 2, 1.5f));
        requestQueue.add(request);
    }

    private void playIndependentAlert(MediaPlayer player) {
        if (player != null) {
            player.setLooping(true);
            if (!player.isPlaying()) player.start();
        }
    }

    private void stopIndependentAlert(MediaPlayer player) {
        if (player != null && player.isPlaying()) {
            player.pause();
            player.seekTo(0);
        }
    }

    private String getLabelFromTextView(TextView tv) {
        String idName = getResources().getResourceEntryName(tv.getId());
        switch (idName) {
            case "leftSensor":
                return "Left";
            case "backSensor":
                return "Back";
            case "rightSensor":
                return "Right";
            default:
                return "Sensor";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopMJPEGStreams();
        stopSensorPolling();

        // Release MediaPlayers
        if (leftAlertPlayer != null)  { leftAlertPlayer.release(); leftAlertPlayer = null; }
        if (rightAlertPlayer != null) { rightAlertPlayer.release(); rightAlertPlayer = null; }
        if (backAlertPlayer != null)  { backAlertPlayer.release(); backAlertPlayer = null; }

        // 🔇 Release object detector + its titit.mp3 player
        if (objectDetectorHelper != null) {
            objectDetectorHelper.release();
            objectDetectorHelper = null;
        }

        // Quit inference threads
        if (frontInferenceThread != null) { frontInferenceThread.quitSafely(); frontInferenceThread = null; }
        if (backInferenceThread != null)  { backInferenceThread.quitSafely();  backInferenceThread = null;  }
        if (leftInferenceThread != null)  { leftInferenceThread.quitSafely();  leftInferenceThread = null;  }
        if (rightInferenceThread != null) { rightInferenceThread.quitSafely(); rightInferenceThread = null; }

        sensorHandler.removeCallbacksAndMessages(null);

        // ✅ Optional: stop / clear Volley queue
        if (requestQueue != null) {
            requestQueue.stop();
            requestQueue = null;
        }
    }
}
