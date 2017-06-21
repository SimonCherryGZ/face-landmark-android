package com.simoncherry.artest.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
/**
 * <pre>
 *     author : Donald
 *     e-mail : xxx@xx
 *     time   : 2017/06/21
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class BitmapUtils {

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromFilePath(String path,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    public static Bitmap getEvenWidthBitmap(String imgUrl) {
        Bitmap srcImg = BitmapFactory.decodeFile(imgUrl);
        if (srcImg != null) {
            Bitmap srcFace = srcImg.copy(Bitmap.Config.RGB_565, true);
            srcImg = null;
            int w = srcFace.getWidth();
            int h = srcFace.getHeight();
            if (w % 2 == 1) {
                w++;
                srcFace = Bitmap.createScaledBitmap(srcFace,
                        srcFace.getWidth() + 1, srcFace.getHeight(), false);
            }
            if (h % 2 == 1) {
                h++;
                srcFace = Bitmap.createScaledBitmap(srcFace,
                        srcFace.getWidth(), srcFace.getHeight() + 1, false);
            }
            return srcFace;
        }
        return null;
    }

    public static Bitmap getRequireWidthBitmap(Bitmap bitmap, int reqWidth) {
        if (bitmap == null) {
            return null;
        } else {
            if (bitmap.getWidth() <= reqWidth) {
                return bitmap;
            } else {
                float ratio = (float)bitmap.getHeight() / bitmap.getWidth();
                int reqHeight = (int) (reqWidth * ratio);
                return Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, true);
            }
        }
    }

    public static Bitmap getViewBitmap(View view){
        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache();
        Bitmap bitmap = view.getDrawingCache().copy(Bitmap.Config.RGB_565, false);
        view.setDrawingCacheEnabled(false);
        return bitmap;
    }
}
