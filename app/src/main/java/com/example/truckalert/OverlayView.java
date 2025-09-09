package com.example.truckalert;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private final Paint boxPaint;
    private final Paint textPaint;
    private List<Detection> detections = new ArrayList<>();

    private int imageWidth = 1;
    private int imageHeight = 1;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);

        textPaint = new Paint();
        textPaint.setColor(Color.YELLOW);
        textPaint.setTextSize(48f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);
    }

    public void setDetections(List<Detection> detections, int imgWidth, int imgHeight) {
        this.detections = detections != null ? detections : new ArrayList<>();
        this.imageWidth = imgWidth;
        this.imageHeight = imgHeight;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (detections == null || detections.isEmpty()) return;

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (Detection detection : detections) {
            if (detection.getBoundingBox() != null && !detection.getCategories().isEmpty()) {

                RectF bbox = detection.getBoundingBox();

                float left = bbox.left * scaleX;
                float top = bbox.top * scaleY;
                float right = bbox.right * scaleX;
                float bottom = bbox.bottom * scaleY;

                canvas.drawRect(left, top, right, bottom, boxPaint);

                String label = detection.getCategories().get(0).getLabel();
                float score = detection.getCategories().get(0).getScore() * 100;
                String text = label + " " + String.format("%.1f%%", score);

                canvas.drawText(text, left, Math.max(top - 10, 40), textPaint);
            }
        }
    }
}
