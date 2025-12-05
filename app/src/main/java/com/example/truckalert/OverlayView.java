package com.example.truckalert;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverlayView extends View {

    private List<Recognition> detections = new ArrayList<>();
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();

    private int frameWidth = 0;
    private int frameHeight = 0;
    private boolean isFrontCam = false;

    // Only these labels will be shown
    private final List<String> relevantLabels = Arrays.asList("person", "car", "truck", "motorcycle", "bicycle", "dog", "cat");

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(42f);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);
    }

    public void setDetections(List<Recognition> detections, int frameWidth, int frameHeight, int viewWidth, int viewHeight, boolean isFrontCam) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.isFrontCam = isFrontCam;

        // Filter only relevant objects
        this.detections = new ArrayList<>();
        if (detections != null) {
            for (Recognition r : detections) {
                if (relevantLabels.contains(r.getLabel().toLowerCase())) {
                    this.detections.add(r);
                }
            }
        }

        postInvalidate();
    }

    public void clearDetections() {
        if (detections != null) detections.clear();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections == null || detections.isEmpty() || frameWidth == 0 || frameHeight == 0) return;

        float scaleX = (float) getWidth() / frameWidth;
        float scaleY = (float) getHeight() / frameHeight;

        for (Recognition rec : detections) {
            RectF box = rec.getBoundingBox();
            float left = box.left * scaleX;
            float top = box.top * scaleY;
            float right = box.right * scaleX;
            float bottom = box.bottom * scaleY;

            float score = rec.getConfidence();
            if (score > 0.75f) boxPaint.setColor(Color.GREEN);
            else if (score > 0.4f) boxPaint.setColor(Color.YELLOW);
            else boxPaint.setColor(Color.RED);

            canvas.drawRect(left, top, right, bottom, boxPaint);
            canvas.drawText(rec.getLabel() + String.format(" (%.1f%%)", score * 100), left + 8, Math.max(top - 12, 40), textPaint);
        }
    }

    // Inner class for recognition
    public static class Recognition {
        private final String label;
        private final float confidence;
        private final RectF boundingBox;

        public Recognition(String label, float confidence, RectF boundingBox) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getLabel() { return label; }
        public float getConfidence() { return confidence; }
        public RectF getBoundingBox() { return boundingBox; }
    }
}
