package com.osacky.soundcloud.wallpaper;

import android.graphics.Bitmap;

public class Utils {

    private static final int RGB_MASK = 0xFFFFFFFF;

    public static Bitmap invert(Bitmap original) {
        // Create mutable Bitmap to invert, argument true makes it mutable
        Bitmap inversion = original.copy(Bitmap.Config.ARGB_8888, true);

        // Get info about Bitmap
        int width = inversion.getWidth();
        int height = inversion.getHeight();
        int pixels = width * height;

        // Get original pixels
        int[] pixel = new int[pixels];
        inversion.getPixels(pixel, 0, width, 0, 0, width, height);

        // Modify pixels
        for (int i = 0; i < pixels; i++)
            pixel[i] ^= RGB_MASK;
        inversion.setPixels(pixel, 0, width, 0, 0, width, height);

        // Return inverted Bitmap
        return inversion;
    }
}
