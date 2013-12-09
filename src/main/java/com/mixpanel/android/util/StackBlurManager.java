/**
* StackBlur v1.0 for Android
*
* @Author: Enrique López Mañas <eenriquelopez@gmail.com>
* http://www.neo-tech.es
*
* Author of the original algorithm: Mario Klingemann <mario.quasimondo.com>
*
* This is a compromise between Gaussian Blur and Box blur
* It creates much better looking blurs than Box Blur, but is
* 7x faster than my Gaussian Blur implementation.

* I called it Stack Blur because this describes best how this
* filter works internally: it creates a kind of moving stack
* of colors whilst scanning through the image. Thereby it
* just has to add one new block of color to the right side
* of the stack and remove the leftmost color. The remaining
* colors on the topmost layer of the stack are either added on
* or reduced by one, depending on if they are on the right or
* on the left side of the stack.
*
* @copyright: Enrique López Mañas
* @license: Apache License 2.0
*
* This file has been modified from it's original version by Mixpanel, Inc
* The original was retrieved from
* https://github.com/kikoso/android-stackblur on October 23rd, 2013
*/


package com.mixpanel.android.util;

import android.graphics.Bitmap;

public class StackBlurManager {
    public static void process(Bitmap source, int radius) {
        if (radius < 1) {
            return; // No work to do
        }
        final int width = source.getWidth();
        final int height = source.getHeight();
        final int[] currentPixels = new int[width * height];
        source.getPixels(currentPixels, 0, width, 0, 0, width, height);

        final int wm = width-1;
        final int hm = height-1;
        final int wh = width * height;
        final int div = radius + radius + 1;

        final int r[] = new int[wh];
        final int g[] = new int[wh];
        final int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp;
        final int vmin[] = new int[Math.max(width, height)];

        int divsum = (div+1)>>1;
        divsum *= divsum;
        final int dv[] = new int[256*divsum];
        for (i = 0; i < 256 * divsum;i++){
            dv[i] = i / divsum;
        }

        int yw = 0;
        int yi = 0;

        final int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        final int r1=radius+1;
        int routsum,goutsum,boutsum;
        int rinsum,ginsum,binsum;

        for (y=0; y < height; y++){
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for(i = -radius; i <= radius; i++){
                p = currentPixels[yi + Math.min(wm, Math.max(i,0))];
                sir = stack[i+radius];
                sir[0] = (p & 0xff0000)>>16;
                sir[1] = (p & 0x00ff00)>>8;
                sir[2] = (p & 0x0000ff);
                rbs = r1-Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i>0){
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < width; x++){
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if(y==0){
                    vmin[x] = Math.min(x + radius + 1,wm);
                }
                p = currentPixels[yw + vmin[x]];

                sir[0] = (p & 0xff0000)>>16;
                sir[1] = (p & 0x00ff00)>>8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += width;
        }
        for (x=0; x < width; x++){
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * width;
            for (i = -radius; i <= radius; i++){
                yi = Math.max(0,yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum+=r[yi]*rbs;
                gsum+=g[yi]*rbs;
                bsum+=b[yi]*rbs;

                if (i>0){
                    rinsum+=sir[0];
                    ginsum+=sir[1];
                    binsum+=sir[2];
                } else {
                    routsum+=sir[0];
                    goutsum+=sir[1];
                    boutsum+=sir[2];
                }

                if(i<hm){
                    yp += width;
                }
            }
            yi=x;
            stackpointer=radius;
            for (y = 0; y < height; y++){
                currentPixels[yi] = 0xff000000 | (dv[rsum]<<16) | (dv[gsum]<<8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if(x == 0){
                    vmin[y] = Math.min(y + r1, hm) * width;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += width;
            }
        }
        source.setPixels(currentPixels, 0, width, 0, 0, width, height);
    }// process()
}
