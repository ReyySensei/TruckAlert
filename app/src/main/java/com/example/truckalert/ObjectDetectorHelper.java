package com.example.truckalert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.util.Log;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetectorHelper {

    private static final String TAG = "ObjectDetectorHelper";
    private final ObjectDetector objectDetector;

    private MediaPlayer alertPlayer;
    private boolean isAlerting = false;

    private final String[] relevantLabels = {
            "person", "car", "truck", "bicycle", "motorcycle", "cat", "dog"
    };

    public ObjectDetectorHelper(Context context) throws IOException {

        ObjectDetectorOptions options = ObjectDetectorOptions.builder()
                .setMaxResults(3)
                .setScoreThreshold(0.4f)
                .build();

        objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "efficientdet_lite1.tflite",
                options
        );

        // 🔊 load titit.mp3
        alertPlayer = MediaPlayer.create(context, R.raw.titit);
        alertPlayer.setLooping(true);

        Log.i(TAG, "EfficientDet Lite1 loaded + alert system ready.");
    }

    public List<OverlayView.Recognition> detectObjects(Bitmap bitmap) {
        List<OverlayView.Recognition> recognitions = new ArrayList<>();
        if (bitmap == null) return recognitions;

        TensorImage image = TensorImage.fromBitmap(bitmap);
        List<Detection> detections = objectDetector.detect(image);

        boolean detectedSomething = false;

        for (Detection det : detections) {
            if (det.getCategories().isEmpty()) continue;

            String label = det.getCategories().get(0).getLabel();
            float score = det.getCategories().get(0).getScore();

            boolean relevant = false;
            for (String rl : relevantLabels)
                if (label.equalsIgnoreCase(rl)) relevant = true;

            if (!relevant) continue;

            detectedSomething = true;

            RectF box = det.getBoundingBox();
            recognitions.add(new OverlayView.Recognition(label, score, box));
        }

        // 🔊 control sound
        handleAlertSound(detectedSomething);

        return recognitions;
    }

    private void handleAlertSound(boolean detected) {
        if (detected) {
            if (!isAlerting && alertPlayer != null) {
                alertPlayer.start();
                isAlerting = true;
                Log.d(TAG, "🔊 Alert started");
            }
        } else {
            if (isAlerting && alertPlayer != null && alertPlayer.isPlaying()) {
                alertPlayer.pause();
                alertPlayer.seekTo(0);
                isAlerting = false;
                Log.d(TAG, "🔇 Alert stopped");
            }
        }
    }

    // ⭐ NEW: Allows Activity to force-stop alert when leaving
    public void stopAlertSound() {
        if (alertPlayer != null && alertPlayer.isPlaying()) {
            alertPlayer.pause();
            alertPlayer.seekTo(0);
        }
        isAlerting = false;
        Log.d(TAG, "🔇 Forced alert stop.");
    }

    // Cleanup
    public void release() {
        stopAlertSound();
        if (alertPlayer != null) {
            alertPlayer.release();
            alertPlayer = null;
        }
        Log.d(TAG, "ObjectDetectorHelper released.");
    }
}
