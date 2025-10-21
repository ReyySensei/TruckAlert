package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.graphics.Rect;
import android.media.Image;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtil {

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        Bitmap bitmap = null;
        if (image.getFormat() == ImageFormat.YUV_420_888) {
            bitmap = yuv420ToBitmap(image);
        }

        if (bitmap != null) {
            bitmap = rotateBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees());
        }

        return bitmap;
    }

    private static Bitmap yuv420ToBitmap(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);

        byte[] uBytes = new byte[uSize];
        byte[] vBytes = new byte[vSize];
        uBuffer.get(uBytes);
        vBuffer.get(vBytes);

        for (int i = 0; i < uSize; i++) {
            nv21[ySize + i * 2] = vBytes[i];
            nv21[ySize + i * 2 + 1] = uBytes[i];
        }

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();

        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap resizeBitmap(Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap, 512, 512, true);
    }
}
