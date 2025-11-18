package com.example.truckalert;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.DefaultRetryPolicy;
import android.util.Log;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import android.media.MediaPlayer;
import android.media.AudioAttributes;

import androidx.appcompat.app.AppCompatActivity;
import org.tensorflow.lite.task.vision.detector.Detection;
import java.util.ArrayList;
import java.util.List;

public class CameraSensorsActivity extends AppCompatActivity {

    public TextureView frontCam, backCam;
    public OverlayView frontOverlay, backOverlay;
    public TextView leftSensor, backSensor, rightSensor;

    private ObjectDetectorHelper objectDetectorHelper;
    private HandlerThread frontInferenceThread, backInferenceThread;
    private Handler frontHandler, backHandler;

    private MJPEGDecoder frontStream, backStream;
    private Handler sensorHandler = new Handler();

    // Independent MediaPlayers for each sensor
    private MediaPlayer leftAlertPlayer;
    private MediaPlayer rightAlertPlayer;
    private MediaPlayer backAlertPlayer;

    private final Object detectionLock = new Object();

    private final String[] relevantLabels = {"person", "motorcycle", "bicycle", "dog", "cat"};

    // Sensor URLs
    private final String LEFT_SENSOR_URL = "http://192.168.4.103/left";
    private final String BACK_SENSOR_URL = "http://192.168.4.104/back";
    private final String RIGHT_SENSOR_URL = "http://192.168.4.105/right";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_sensors);

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        frontCam = findViewById(R.id.frontCam);
        backCam = findViewById(R.id.backCam);
        frontOverlay = findViewById(R.id.frontOverlay);
        backOverlay = findViewById(R.id.backOverlay);

        leftSensor = findViewById(R.id.leftSensor);
        backSensor = findViewById(R.id.backSensor);
        rightSensor = findViewById(R.id.rightSensor);

        // Initialize separate beep players
        leftAlertPlayer = MediaPlayer.create(this, R.raw.beep);
        rightAlertPlayer = MediaPlayer.create(this, R.raw.beep);
        backAlertPlayer = MediaPlayer.create(this, R.raw.beep);

        try {
            objectDetectorHelper = new ObjectDetectorHelper(this);
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }

        // Threads for inference
        frontInferenceThread = new HandlerThread("FrontInferenceThread");
        frontInferenceThread.start();
        frontHandler = new Handler(frontInferenceThread.getLooper());

        backInferenceThread = new HandlerThread("BackInferenceThread");
        backInferenceThread.start();
        backHandler = new Handler(backInferenceThread.getLooper());

        startMJPEGStreams();
        startSensorPolling();
    }

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
                long start = System.currentTimeMillis();
                List<OverlayView.Recognition> results = objectDetectorHelper.detectObjects(bitmap);
                long time = System.currentTimeMillis() - start;
                Log.d("TFLite", "Detection took: " + time + "ms");

                runOnUiThread(() -> {
                    OverlayView overlay = isFrontCam ? frontOverlay : backOverlay;
                    TextureView camView = isFrontCam ? frontCam : backCam;
                    if (overlay != null) {
                        overlay.setDetections(results, bitmap.getWidth(), bitmap.getHeight(),
                                camView.getWidth(), camView.getHeight(), isFrontCam);
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
                fetchDualSensorData(LEFT_SENSOR_URL, leftSensor, 30, leftAlertPlayer);
                fetchSensorData(BACK_SENSOR_URL, backSensor, 30, backAlertPlayer);
                fetchDualSensorData(RIGHT_SENSOR_URL, rightSensor, 30, rightAlertPlayer);
                sensorHandler.postDelayed(this, 600);
            }
        };
        sensorHandler.post(sensorTask);
    }

    private void fetchDualSensorData(String url, TextView targetView, int safeDistance, MediaPlayer alertPlayer) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        String[] parts = response.trim().split(",");
                        int dist1 = Integer.parseInt(parts[0].trim());
                        int dist2 = (parts.length > 1) ? Integer.parseInt(parts[1].trim()) : -1;
                        int nearest = (dist2 > 0 && dist2 < dist1) ? dist2 : dist1;

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
        Volley.newRequestQueue(this).add(request);
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
        Volley.newRequestQueue(this).add(request);
    }

    private void playIndependentAlert(MediaPlayer player) {
        if (player != null && !player.isPlaying()) {
            player.start();
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

        if (leftAlertPlayer != null) leftAlertPlayer.release();
        if (rightAlertPlayer != null) rightAlertPlayer.release();
        if (backAlertPlayer != null) backAlertPlayer.release();

        if (frontStream != null) frontStream.stopStream();
        if (backStream != null) backStream.stopStream();

        frontInferenceThread.quitSafely();
        backInferenceThread.quitSafely();
        sensorHandler.removeCallbacksAndMessages(null);
    }


}