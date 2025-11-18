package com.example.truckalert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
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

    private final String[] relevantLabels = {"person", "bicycle", "motorcycle", "cat", "dog"};

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
        Log.i(TAG, "EfficientDet Lite1 loaded successfully.");
    }

    public List<OverlayView.Recognition> detectObjects(Bitmap bitmap) {
        List<OverlayView.Recognition> recognitions = new ArrayList<>();
        if (bitmap == null) return recognitions;

        TensorImage image = TensorImage.fromBitmap(bitmap);
        List<Detection> detections = objectDetector.detect(image);

        for (Detection det : detections) {
            if (det.getCategories().isEmpty()) continue;
            String label = det.getCategories().get(0).getLabel();
            float score = det.getCategories().get(0).getScore();

            boolean relevant = false;
            for (String rl : relevantLabels) if (label.equalsIgnoreCase(rl)) relevant = true;
            if (!relevant) continue;

            RectF box = det.getBoundingBox();
            recognitions.add(new OverlayView.Recognition(label, score, box));
        }

        return recognitions;
    }
}
