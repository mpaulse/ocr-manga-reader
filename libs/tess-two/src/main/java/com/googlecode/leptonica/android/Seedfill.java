package com.googlecode.leptonica.android;


public class Seedfill {
    static {
        System.loadLibrary("pngt");
        System.loadLibrary("lept");
    }

    /**
     * Removes all fg components touching the border.
     *
     * @param pixs The source pix (1 bpp)
     * @param connectivity Filling connectivity (4 or 8)
     * @return All pixels in the src that are not touching the border, or null on error
     */
    public static Pix removeBorderConnComps(Pix pixs, int connectivity) {
        if (pixs == null)
            throw new IllegalArgumentException("Source pix must be non-null");
        if (connectivity != 4 && connectivity != 8)
            throw new IllegalArgumentException("Connectivity not 4 or 8");

        long nativePix = nativePixRemoveBorderConnComps(pixs.getNativePix(), connectivity);

        if (nativePix == 0)
            return null;

        return new Pix(nativePix);
    }
    

    // ***************
    // * NATIVE CODE *
    // ***************

    private static native long nativePixRemoveBorderConnComps(long nativePix, int connectivity);
}
