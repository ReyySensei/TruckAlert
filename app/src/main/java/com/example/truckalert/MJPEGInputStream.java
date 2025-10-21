package com.example.truckalert;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MJPEGInputStream extends BufferedInputStream {

    private static final int HEADER_MAX_LENGTH = 100;
    private static final int FRAME_MAX_LENGTH = 400000; // max JPEG frame size ~400KB
    private final byte[] frameBuffer = new byte[FRAME_MAX_LENGTH];

    public MJPEGInputStream(InputStream in) {
        super(in, FRAME_MAX_LENGTH);
    }

    public Bitmap readMJPEGFrame() throws IOException {
        int len = 0;
        boolean startFound = false;
        boolean endFound = false;

        int b;
        while ((b = read()) != -1) {
            if (!startFound) {
                if (b == 0xFF) {
                    int next = read();
                    if (next == 0xD8) { // SOI marker
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

                if (b == 0xFF && next == 0xD9) { // EOI marker
                    endFound = true;
                    break;
                }

                if (len >= FRAME_MAX_LENGTH - 2) {
                    // frame too large, discard
                    return null;
                }

                b = next;
            }
        }

        if (startFound && endFound) {
            return BitmapFactory.decodeByteArray(frameBuffer, 0, len);
        }

        return null;
    }
}
