package com.example.truckalert;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.DefaultRetryPolicy;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import android.media.MediaPlayer;


import android.graphics.RectF;


import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class CameraSensorsActivity extends AppCompatActivity {

    public TextureView frontCam, backCam;
    public OverlayView overlayView;
    public OverlayView frontOverlay, backOverlay;
    public TextView leftSensor, backSensor, rightSensor;

    private ObjectDetectorHelper objectDetectorHelper;

    private HandlerThread frontInferenceThread, backInferenceThread;
    private Handler frontHandler, backHandler;

    private MJPEGDecoder frontStream, backStream;

    private final Object detectionLock = new Object();
    private MediaPlayer mediaPlayer;
    private boolean isAlertPlaying = false;

    private final String[] relevantLabels = { "person", "motorcycle", "bicycle", "cat", "dog"};

    // Sensor URLs
    private final String LEFT_SENSOR_URL = "http://192.168.4.103/left";
    private final String BACK_SENSOR_URL = "http://192.168.4.104/back";
    private final String RIGHT_SENSOR_URL = "http://192.168.4.105/right";


    private Handler sensorHandler = new Handler();

    private boolean isRelevant(String label) {
        for (String rl : relevantLabels)
            if (label.equalsIgnoreCase(rl)) return true;
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_sensors);

        mediaPlayer = MediaPlayer.create(this, R.raw.beep);

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        frontCam = findViewById(R.id.frontCam);
        backCam = findViewById(R.id.backCam);
        overlayView = findViewById(R.id.overlayView);
        frontOverlay = findViewById(R.id.frontOverlay);
        backOverlay = findViewById(R.id.backOverlay);

        leftSensor = findViewById(R.id.leftSensor);
        backSensor = findViewById(R.id.backSensor);
        rightSensor = findViewById(R.id.rightSensor);


        try {
            objectDetectorHelper = new ObjectDetectorHelper(this);
        } catch (IOException e) {
            e.printStackTrace();
            // Optionally show a message or finish the activity
            finish();
        }


        // Threads for inference
        frontInferenceThread = new HandlerThread("FrontInferenceThread");
        frontInferenceThread.start();
        frontHandler = new Handler(frontInferenceThread.getLooper());

        backInferenceThread = new HandlerThread("BackInferenceThread");
        backInferenceThread.start();
        backHandler = new Handler(backInferenceThread.getLooper());

        // Start camera streams (unchanged)
        startMJPEGStreams();

        // Start sensors
        startSensorPolling();
    }

    // Camera stream code unchanged
    private void startMJPEGStreams() {
        String frontURL = "http://192.168.4.101:81/stream";
        String backURL = "http://192.168.4.102:81/stream";

        frontStream = new MJPEGDecoder(frontURL, frontCam, true, this);
        backStream = new MJPEGDecoder(backURL, backCam, false, this);

        frontStream.start();
        backStream.start();
    }

    /*** ---- OBJECT DETECTION ---- ***/
    public void runDetection(Bitmap bitmap, boolean isFrontCam) {
        if (bitmap == null) return;

        Handler handler = isFrontCam ? frontHandler : backHandler;

        handler.post(() -> {
            synchronized (detectionLock) {
                // Detect objects using the new helper
                List<OverlayView.Recognition> results = objectDetectorHelper.detectObjects(bitmap);

                // No need to filter by label, because helper already limits to 5 objects
                List<OverlayView.Recognition> filtered = results != null ? results : new ArrayList<>();

                // Update overlay
                runOnUiThread(() -> {
                    OverlayView overlay = isFrontCam ? frontOverlay : backOverlay;
                    TextureView camView = isFrontCam ? frontCam : backCam;
                    if (overlay != null && camView != null) {
                        overlay.setDetections(
                                filtered,
                                bitmap.getWidth(), bitmap.getHeight(),
                                camView.getWidth(), camView.getHeight(),
                                isFrontCam
                        );
                    }
                });
            }
        });
    }


    /*** ---- SENSOR POLLING ---- ***/
    private void startSensorPolling() {
        Runnable sensorTask = new Runnable() {
            @Override
            public void run() {
                // --- Update each sensor module ---
                fetchDualSensorData(LEFT_SENSOR_URL, leftSensor, 30);   // 2 sensors on left
                fetchSensorData(BACK_SENSOR_URL, backSensor, 30);       // 1 sensor on back
                fetchDualSensorData(RIGHT_SENSOR_URL, rightSensor, 30); // 2 sensors on right

                // Repeat every 2 seconds
                sensorHandler.postDelayed(this, 600);
            }
        };

        sensorHandler.post(sensorTask);
    }

    /**
     * Handles two ultrasonic readings from one ESP32 (e.g., left or right modules).
     * Expected response format: "25,40" (both distances in cm)
     */
    private void fetchDualSensorData(String url, TextView targetView, int safeDistance) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        String[] parts = response.trim().split(",");
                        int dist1 = Integer.parseInt(parts[0].trim());
                        int dist2 = (parts.length > 1) ? Integer.parseInt(parts[1].trim()) : -1;

                        // Use nearest distance as the alert distance
                        int nearest = (dist2 > 0 && dist2 < dist1) ? dist2 : dist1;

                        targetView.setText(getLabelFromTextView(targetView));

                        if (nearest <= safeDistance) {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                            playAlertSound();
                        } else {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                            stopAlertSound();
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
        Volley.newRequestQueue(this).add(request);
    }

    /**
     * Handles single ultrasonic reading (e.g., for back module)
     */
    private void fetchSensorData(String url, TextView targetView, int safeDistance) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        int distance = Integer.parseInt(response.trim());

                        targetView.setText(getLabelFromTextView(targetView));

                        if (distance <= safeDistance) {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                            playAlertSound();
                        } else {
                            targetView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                            stopAlertSound();
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
        Volley.newRequestQueue(this).add(request);
    }

    // Helper to get label like "LeftSensor" from TextView
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

    private void playAlertSound() {
        if (mediaPlayer != null && !isAlertPlaying) {
            mediaPlayer.start();
            isAlertPlaying = true;

            mediaPlayer.setOnCompletionListener(mp -> isAlertPlaying = false);
        }
    }

    private void stopAlertSound() {
        if (mediaPlayer != null && isAlertPlaying) {
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
            isAlertPlaying = false;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (frontStream != null) frontStream.stopStream();
        if (backStream != null) backStream.stopStream();
        frontInferenceThread.quitSafely();
        backInferenceThread.quitSafely();
        sensorHandler.removeCallbacksAndMessages(null);
    }
}
