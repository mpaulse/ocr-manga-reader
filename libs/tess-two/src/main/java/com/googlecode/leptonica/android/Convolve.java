package com.googlecode.leptonica.android;


public class Convolve {
    static {
        System.loadLibrary("pngt");
        System.loadLibrary("lept");
    }

    /**
     * Apply normalized box filter. Blurs image.
     *
     * @param pixs The source pix (8 bpp)
     * @param wc Convolution "half-width"
     * @param wh Convolution "half-height"
     * @return pix (8 bpp), or null on error
     */
    public static Pix blockconvGray(Pix pixs, int wc, int wh) {
        if (pixs == null)
            throw new IllegalArgumentException("Source pix must be non-null");
        if (wc < 0)
            throw new IllegalArgumentException("wc must be > 0");
        if (wh < 0)
          throw new IllegalArgumentException("wh must be > 0");
        
        long nativePix = nativePixBlockconvGray(pixs.getNativePix(), wc, wh);

        if (nativePix == 0)
            return null;

        return new Pix(nativePix);
    }
    

    // ***************
    // * NATIVE CODE *
    // ***************

    private static native long nativePixBlockconvGray(long nativePix, int wc, int wh);
}
