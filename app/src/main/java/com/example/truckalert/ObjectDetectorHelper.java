package com.example.truckalert;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetectorHelper {

    private static final String TAG = "ObjectDetectorHelper";

    // Using EfficientDet Lite3 (best for high-end phones)
    private static final String MODEL_PATH = "efficientdet_lite3.tflite";

    private ObjectDetector objectDetector;

    public ObjectDetectorHelper(Context context) {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(5)        // Detect up to 5 objects
                            .setScoreThreshold(0.35f) // Lower threshold to catch small objects
                            .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(context, MODEL_PATH, options);
            Log.d(TAG, "✅ EfficientDet Lite3 model loaded: " + MODEL_PATH);

        } catch (IOException e) {
            Log.e(TAG, "❌ Error loading model: " + e.getMessage(), e);
            objectDetector = null;
        }
    }

    public List<Detection> detectObjects(Bitmap bitmap) {
        if (objectDetector == null) {
            Log.e(TAG, "❌ ObjectDetector not initialized.");
            return new ArrayList<>();
        }

        try {
            // Resize bitmap to 512x512 for Lite3
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true);

            // Convert Bitmap → TensorImage
            TensorImage tensorImage = TensorImage.fromBitmap(resizedBitmap);

            // Run detection
            return objectDetector.detect(tensorImage);

        } catch (Exception e) {
            Log.e(TAG, "❌ Detection failed: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
