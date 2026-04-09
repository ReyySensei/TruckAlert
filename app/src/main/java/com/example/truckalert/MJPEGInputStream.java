package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MJPEGInputStream extends BufferedInputStream {

    private static final int FRAME_MAX_LENGTH = 400000; // ~400KB max JPEG
    private final byte[] frameBuffer = new byte[FRAME_MAX_LENGTH];

    public MJPEGInputStream(InputStream in) {
        super(in, FRAME_MAX_LENGTH);
    }

    /**
     * Reads one JPEG frame and returns the raw bytes.
     * Used by MJPEGDecoder for both display AND recording.
     */
    public byte[] readMJPEGFrameBytes() throws IOException {
        int len = 0;
        boolean startFound = false;
        boolean endFound = false;

        int b;
        while ((b = read()) != -1) {
            if (!startFound) {
                if (b == 0xFF) {
                    int next = read();
                    if (next == 0xD8) { // SOI
                        frameBuffer[len++] = (byte) b;
                        frameBuffer[len++] = (byte) next;
                        startFound = true;
                    }
                }
            } else {
                frameBuffer[len++] = (byte) b;
                int next = read();
                if (next == -1) break;
                frameBuffer[len++] = (byte) next;

                if (b == 0xFF && next == 0xD9) { // EOI
                    endFound = true;
                    break;
                }

                if (len >= FRAME_MAX_LENGTH - 2) return null; // frame too large

                b = next;
            }
        }

        if (startFound && endFound) {
            byte[] result = new byte[len];
            System.arraycopy(frameBuffer, 0, result, 0, len);
            return result;
        }
        return null;
    }

    /**
     * Legacy method — kept for compatibility. Decodes to Bitmap.
     */
    public Bitmap readMJPEGFrame() throws IOException {
        byte[] bytes = readMJPEGFrameBytes();
        if (bytes == null) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}