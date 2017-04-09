/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.leptonica.android;

import android.graphics.Rect;

/**
 * Java representation of a native Leptonica PIX object.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class Pix {
    static {
        System.loadLibrary("pngt");
        System.loadLibrary("lept");
    }

    /** Index of the image width within the dimensions array. */
    public static final int INDEX_W = 0;

    /** Index of the image height within the dimensions array. */
    public static final int INDEX_H = 1;

    /** Index of the image bit-depth within the dimensions array. */
    public static final int INDEX_D = 2;

    /** Package-accessible pointer to native pix */
    private final long mNativePix;

    private boolean mRecycled;

    /**
     * Creates a new Pix wrapper for the specified native PIX object.
     *
     * @param nativePix A pointer to the native PIX object.
     */
    public Pix(long nativePix) {
        mNativePix = nativePix;
        mRecycled = false;
    }

    public Pix(int width, int height, int depth) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Pix width and height must be > 0");
        } else if (depth != 1 && depth != 2 && depth != 4 && depth != 8 && depth != 16
                && depth != 24 && depth != 32) {
            throw new IllegalArgumentException("Depth must be one of 1, 2, 4, 8, 16, or 32");
        }

        mNativePix = nativeCreatePix(width, height, depth);
        mRecycled = false;
    }

    /**
     * Returns a pointer to the native Pix object. This is used by native code
     * and is only valid within the same process in which the Pix was created.
     *
     * @return a native pointer to the Pix object
     */
    public long getNativePix() {
        if (mRecycled)
            throw new IllegalStateException();

        return mNativePix;
    }

    /**
     * Return the raw bytes of the native PIX object. You can reconstruct the
     * Pix from this data using createFromPix().
     *
     * @return a copy of this PIX object's raw data
     */
    public byte[] getData() {
        if (mRecycled)
            throw new IllegalStateException();

        byte[] buffer = nativeGetData(mNativePix);

        if (buffer == null) {
            throw new RuntimeException("native getData failed");
        }

        return buffer;
    }

    /**
     * Returns an array of this image's dimensions. See Pix.INDEX_* for indices.
     *
     * @return an array of this image's dimensions or <code>null</code> on
     *         failure
     */
    public int[] getDimensions() {
        if (mRecycled)
            throw new IllegalStateException();

        int[] dimensions = new int[3];

        if (getDimensions(dimensions)) {
            return dimensions;
        }

        return null;
    }

    /**
     * Fills an array with this image's dimensions. The array must be at least 3
     * elements long.
     *
     * @param dimensions An integer array with at least three elements.
     * @return <code>true</code> on success
     */
    public boolean getDimensions(int[] dimensions) {
        if (mRecycled)
            throw new IllegalStateException();

        return nativeGetDimensions(mNativePix, dimensions);
    }

    /**
     * Returns a clone of this Pix. This does NOT create a separate copy, just a
     * new pointer that can be recycled without affecting other clones.
     *
     * @return a clone (shallow copy) of the Pix
     */
    @Override
    public Pix clone() {
        if (mRecycled)
            throw new IllegalStateException();

        long nativePix = nativeClone(mNativePix);

        if (nativePix == 0) {
            throw new OutOfMemoryError();
        }

        return new Pix(nativePix);
    }

    /**
     * Returns a deep copy of this Pix that can be modified without affecting
     * the original Pix.
     *
     * @return a copy of the Pix
     */
    public Pix copy() {
        if (mRecycled)
            throw new IllegalStateException();

        long nativePix = nativeCopy(mNativePix);

        if (nativePix == 0) {
            throw new OutOfMemoryError();
        }

        return new Pix(nativePix);
    }

    /**
     * Inverts this Pix in-place.
     *
     * @return <code>true</code> on success
     */
    public boolean invert() {
        if (mRecycled)
            throw new IllegalStateException();

        return nativeInvert(mNativePix);
    }

    /**
     * Releases resources and frees any memory associated with this Pix. You may
     * not modify or access the pix after calling this method.
     */
    public void recycle() {
        if (!mRecycled) {
            nativeDestroy(mNativePix);

            mRecycled = true;
        }
    }

    /**
     * Creates a new Pix from raw Pix data obtained from getData().
     *
     * @param pixData Raw pix data obtained from getData().
     * @param width The width of the original Pix.
     * @param height The height of the original Pix.
     * @param depth The bit-depth of the original Pix.
     * @return a new Pix or <code>null</code> on error
     */
    public static Pix createFromPix(byte[] pixData, int width, int height, int depth) {
        long nativePix = nativeCreateFromData(pixData, width, height, depth);

        if (nativePix == 0) {
            throw new OutOfMemoryError();
        }

        return new Pix(nativePix);
    }

    /**
     * Returns a Rect with the width and height of this Pix.
     *
     * @return a Rect with the width and height of this Pix
     */
    public Rect getRect() {
        int w = getWidth();
        int h = getHeight();

        return new Rect(0, 0, w, h);
    }

    /**
     * Returns the width of this Pix.
     *
     * @return the width of this Pix
     */
    public int getWidth() {
        if (mRecycled)
            throw new IllegalStateException();

        return nativeGetWidth(mNativePix);
    }

    /**
     * Returns the height of this Pix.
     *
     * @return the height of this Pix
     */
    public int getHeight() {
        if (mRecycled)
            throw new IllegalStateException();

        return nativeGetHeight(mNativePix);
    }

    /**
     * Returns the depth of this Pix.
     *
     * @return the depth of this Pix
     */
    public int getDepth() {
        if (mRecycled)
            throw new IllegalStateException();

        return nativeGetDepth(mNativePix);
    }

    public int getRefCount() {
        return nativeGetRefCount(mNativePix);
    }

    /**
     * Returns the {@link android.graphics.Color} at the specified location.
     *
     * @param x The x coordinate (0...width-1) of the pixel to return.
     * @param y The y coordinate (0...height-1) of the pixel to return.
     * @return The argb {@link android.graphics.Color} at the specified
     *         coordinate.
     * @throws IllegalArgumentException If x, y exceeds the image bounds.
     */
    public int getPixel(int x, int y) {
        if (mRecycled)
            throw new IllegalStateException();

        if (x < 0 || x >= getWidth()) {
            throw new IllegalArgumentException("Supplied x coordinate exceeds image bounds");
        } else if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Supplied y coordinate exceeds image bounds");
        }

        return nativeGetPixel(mNativePix, x, y);
    }

    /**
     * Sets the {@link android.graphics.Color} at the specified location.
     * 
     * @param x The x coordinate (0...width-1) of the pixel to set.
     * @param y The y coordinate (0...height-1) of the pixel to set.
     * @param color The argb {@link android.graphics.Color} to set at the
     *            specified coordinate.
     * @throws IllegalArgumentException If x, y exceeds the image bounds.
     */
    public void setPixel(int x, int y, int color) {
        if (mRecycled)
            throw new IllegalStateException();

        if (x < 0 || x >= getWidth()) {
            throw new IllegalArgumentException("Supplied x coordinate exceeds image bounds");
        } else if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Supplied y coordinate exceeds image bounds");
        }

        nativeSetPixel(mNativePix, x, y, color);
    }

    /* cb4960 */
    /**
     * Add external black or white border.
     *
     * @param pixs The source pix (all depths; colormap ok)
     * @param left Left border
     * @param right Right border
     * @param top Top border
     * @param bottom Bottom border
     * @param setBlack True = black border, False = white border
     * @return All pixels in the src plus border pixels
     */
    public static Pix addBlackOrWhiteBorder(Pix pixs, int left, int right, int top, int bottom, boolean setBlack) {
        if (pixs == null)
            throw new IllegalArgumentException("Source pix must be non-null");
        if (left < 0)
            throw new IllegalArgumentException("Left must be >= 0");
        if (right < 0)
          throw new IllegalArgumentException("Right must be >= 0");
        if (top < 0)
          throw new IllegalArgumentException("Top must be >= 0");
        if (bottom < 0)
          throw new IllegalArgumentException("Bottom must be >= 0");
        
        int op = 1; // White
        
        if(setBlack)
        {
          op = 2; // Black
        }
        
        long nativePix = nativePixAddBlackOrWhiteBorder(pixs.getNativePix(), left, right, top, bottom, op);

        if (nativePix == 0)
            return null;

        return new Pix(nativePix);
    }
    
    /* cb4960 */
    /**
     * Get average of pixel values in region.
     *
     * @param pixs The source pix (1, 2, 4, 8 bpp; not cmapped)
     * @param x X
     * @param y Y
     * @param w Width
     * @param h Height
     * @return Average of pixel values in region, -1.0 on error
     */
    public static float averageInRect(Pix pixs, int x, int y, int w, int h) {
        if (pixs == null)
            throw new IllegalArgumentException("Source pix must be non-null");
        if (x < 0)
            throw new IllegalArgumentException("X must be >= 0");
        if (y < 0)
          throw new IllegalArgumentException("Y must be >= 0");
        if (w < 0)
          throw new IllegalArgumentException("Width must be >= 0");
        if (h < 0)
          throw new IllegalArgumentException("Height must be >= 0");
        
        float ave = nativePixAverageInRect(pixs.getNativePix(), x, y, w, h);

        return ave;
    }
    // ***************
    // * NATIVE CODE *
    // ***************

    private static native int nativeGetRefCount(long nativePix);
    private static native long nativeCreatePix(int w, int h, int d);
    private static native long nativeCreateFromData(byte[] data, int w, int h, int d);
    private static native byte[] nativeGetData(long nativePix);
    private static native long nativeClone(long nativePix);
    private static native long nativeCopy(long nativePix);
    private static native boolean nativeInvert(long nativePix);
    private static native void nativeDestroy(long nativePix);
    private static native boolean nativeGetDimensions(long nativePix, int[] dimensions);
    private static native int nativeGetWidth(long nativePix);
    private static native int nativeGetHeight(long nativePix);
    private static native int nativeGetDepth(long nativePix);
    private static native int nativeGetPixel(long nativePix, int x, int y);
    private static native void nativeSetPixel(long nativePix, int x, int y, int color);
    private static native long nativePixAddBlackOrWhiteBorder(long nativePix, int left, int right, int top, int bot, int op);
    private static native float nativePixAverageInRect(long nativePix, int x, int y, int w, int h);
}
