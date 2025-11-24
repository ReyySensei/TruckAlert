package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MJPEGInputStream extends BufferedInputStream {

    private static final int FRAME_MAX_LENGTH = 300000; // Safe for ESP32 (up to 300 KB JPEG)
    private final byte[] frameBuffer = new byte[FRAME_MAX_LENGTH];

    public MJPEGInputStream(InputStream in) {
        super(in, FRAME_MAX_LENGTH);
    }

    // Combined stable SOI/EOI scanning (fixed version)
    public Bitmap readMJPEGFrame() throws IOException {
        int len = 0;

        // ---- FIND SOI (Start Of Image) ----
        int prev = -1;
        while (true) {
            int curr = read();
            if (curr == -1) return null;

            if (prev == 0xFF && curr == 0xD8) {
                // SOI found → write it
                frameBuffer[0] = (byte) 0xFF;
                frameBuffer[1] = (byte) 0xD8;
                len = 2;
                break;
            }
            prev = curr;
        }

        // ---- READ UNTIL EOI (End Of Image) ----
        prev = -1;
        while (true) {
            int curr = read();
            if (curr == -1) return null;

            frameBuffer[len++] = (byte) curr;

            if (len >= FRAME_MAX_LENGTH - 1) {
                // Too big → discard frame
                return null;
            }

            if (prev == 0xFF && curr == 0xD9) {
                // FULL JPEG frame detected
                break;
            }

            prev = curr;
        }

        // ---- Decode JPEG to Bitmap ----
        return BitmapFactory.decodeByteArray(frameBuffer, 0, len);
    }
}
