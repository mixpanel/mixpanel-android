package com.mixpanel.android.util;

import android.graphics.Bitmap;

public class Blur {

    private static final int RADIUS = 10; // * SCALE
    private static final int SCALE = 4;

    // TODO use Renderscript where available
    // TODO should return the SMALL pixels[]? Or maybe a byte array?
    // TODO figure out how much memory this uses, maybe do even more shrinkage
    public static Bitmap cpuBlur(Bitmap source) {
        // First, downsample the image
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int smallWidth = sourceWidth / SCALE;
        int smallHeight = sourceHeight / SCALE;
        Bitmap smaller = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, false);

        int[] pixels = new int[smallWidth * smallHeight];
        smaller.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight);

        int window_size = RADIUS + RADIUS + 1;
        int[] divisions = new int[256];
        for (int i = 0; i < 256; i++) {
            divisions[i] = i / window_size;
        }

        // The RGB values of the current box, a ring buffer
        int[][] window = new int[window_size][3];

        // Y dimension first
        for (int x = 0; x < smallWidth; x++) {
            int resultRed = 0;
            int resultGreen = 0;
            int resultBlue = 0;
            int window_pointer = 0;
            for (int i = 0; i < window_size; i++) {
                window[i][0] = 0;
                window[i][1] = 0;
                window[i][2] = 0;
            }
            for (int y = 0; y < smallHeight; y++) {
                resultRed -= window[window_pointer][0];
                resultGreen -= window[window_pointer][1];
                resultBlue -= window[window_pointer][2];

                int px = pixels[x + (smallWidth * y)];
                int sourceRed = (px & 0xff0000) >> 16;
                int sourceGreen = (px & 0x00ff00) >> 8;
                int sourceBlue = (px & 0x0000ff);

                int divRed = divisions[sourceRed];
                int divGreen = divisions[sourceGreen];
                int divBlue = divisions[sourceBlue];

                resultRed += divRed;
                resultGreen += divGreen;
                resultBlue += divBlue;

                int destY = y - RADIUS;
                if (destY > 0) {
                    pixels[x + (smallWidth * destY)] = (resultRed << 16) | (resultGreen << 8 ) | resultBlue;
                }

                window[window_pointer][0] = divRed;
                window[window_pointer][1] = divGreen;
                window[window_pointer][2] = divBlue;
                window_pointer = (window_pointer + 1) % window_size;
            }
            for (int destY = smallWidth - RADIUS + 1; destY < smallWidth; destY++) {
                resultRed -= window[window_pointer][0];
                resultGreen -= window[window_pointer][1];
                resultBlue -= window[window_pointer][2];
                pixels[x + (smallWidth * destY)] = (resultRed << 16) | (resultGreen << 8 ) | resultBlue;
            }
        }

        for (int y = 0; y < smallHeight; y++) {
            int resultRed = 0;
            int resultGreen = 0;
            int resultBlue = 0;
            int window_pointer = 0;
            for (int i = 0; i < window_size; i++) {
                window[i][0] = 0;
                window[i][1] = 0;
                window[i][2] = 0;
            }
            for (int x = 0; x < smallWidth; x++) {
                resultRed -= window[window_pointer][0];
                resultGreen -= window[window_pointer][1];
                resultBlue -= window[window_pointer][2];

                int px = pixels[x + (smallWidth * y)];
                int sourceRed = (px & 0xff0000) >> 16;
                int sourceGreen = (px & 0x00ff00) >> 8;
                int sourceBlue = (px & 0x0000ff);

                int divRed = divisions[sourceRed];
                int divGreen = divisions[sourceGreen];
                int divBlue = divisions[sourceBlue];

                resultRed += divRed;
                resultGreen += divGreen;
                resultBlue += divBlue;

                int destX = x - RADIUS;
                if (destX > 0) {
                    pixels[destX + (smallWidth * y)] = (resultRed << 16) | (resultGreen << 8 ) | resultBlue;
                }

                window[window_pointer][0] = divRed;
                window[window_pointer][1] = divGreen;
                window[window_pointer][2] = divBlue;
                window_pointer = (window_pointer + 1) % window_size;
            }
            for (int destX = smallWidth - RADIUS + 1; destX < smallWidth; destX++) {
                resultRed -= window[window_pointer][0];
                resultGreen -= window[window_pointer][1];
                resultBlue -= window[window_pointer][2];
                pixels[destX + (smallWidth * y)] = (resultRed << 16) | (resultGreen << 8 ) | resultBlue;
            }
        }

        // TODO don't scale up here. Maybe don't scale down here either?
        smaller.setPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight);
        return Bitmap.createScaledBitmap(smaller, sourceWidth, sourceHeight, true);
    }
}
