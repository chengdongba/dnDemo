package com.dqchen.imagehelp.imageCache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * 加载一张图片时,设置图片的最大宽高,设置图片是否需要透明度,以减少图片的内存占用
 */
public class ImageResize {
    /**
     * @param context  上下文
     * @param id       图片id
     * @param maxW     最大宽
     * @param maxH     最大高
     * @param hasAlpha 透明度
     * @param reusable 复用图片
     */
    public static Bitmap resizeBitmap(Context context, int id, int maxW, int maxH, boolean hasAlpha, Bitmap reusable) {
        //先把bitmap弄出来
        Resources resources = context.getResources();
        BitmapFactory.Options options = new BitmapFactory.Options();
        BitmapFactory.decodeResource(resources, id, options);
        //可复用
        options.inMutable = true;
        options.inBitmap = reusable;
        //根据透明度设置config
        if (!hasAlpha) {
            options.inPreferredConfig = Bitmap.Config.RGB_565;
        }
        int w = options.outWidth;
        int h = options.outHeight;
        //设置缩放比例
        options.inSampleSize = calculateInSampleSize(w, h, maxW, maxH);
        return BitmapFactory.decodeResource(resources, id, options);
    }

    private static int calculateInSampleSize(int w, int h, int maxW, int maxH) {
        int inSampleSize = 1;
        if (w > maxW && h > maxH) {
            inSampleSize = 2;
            while (w / inSampleSize > maxW && h / inSampleSize > maxH) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
