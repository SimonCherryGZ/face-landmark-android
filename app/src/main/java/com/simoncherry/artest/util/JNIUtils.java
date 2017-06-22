package com.simoncherry.artest.util;

/**
 * <pre>
 *     author : Donald
 *     e-mail : xxx@xx
 *     time   : 2017/06/22
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class JNIUtils {

    static {
        System.loadLibrary("JNI_APP");
    }

    public static native int[] doGrayScale(int[] buf, int w, int h);

    public static native String doFaceSwap(String[] paths);
}
