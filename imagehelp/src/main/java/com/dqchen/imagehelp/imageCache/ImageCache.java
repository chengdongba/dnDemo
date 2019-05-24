package com.dqchen.imagehelp.imageCache;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.LruCache;

import com.dqchen.imagehelp.imageCache.disk.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 图片的三级缓存
 * 设置单例
 * 初始化
 * 设置内存和磁盘存储
 * 设置弱引用复用池
 */
public class ImageCache {

    public static volatile ImageCache instance;
    private Context context;
    private LruCache<String, Bitmap> memoryCache;
    private DiskLruCache diskLruCache;
    private ReferenceQueue referenceQueue;
    private Set<WeakReference<Bitmap>> reusablePool;
    private BitmapFactory.Options options = new BitmapFactory.Options();

    public static ImageCache getInstance() {
        if (null == instance) {
            synchronized (ImageCache.class) {
                if (null == instance) {
                    instance = new ImageCache();
                }
            }
        }
        return instance;
    }

    private boolean shutDown;
    private Thread clearThread;

    private ReferenceQueue<Bitmap> getReferenceQueue() {
        if (null == referenceQueue) {
            referenceQueue = new ReferenceQueue<Bitmap>();
            clearThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Reference<Bitmap> reference = referenceQueue.remove();
                        Bitmap bitmap = reference.get();
                        if (null != bitmap && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            clearThread.start();
        }
        return referenceQueue;
    }

    public void init(Context context, String dir) {
        this.context = context.getApplicationContext();
        reusablePool = Collections.synchronizedSet(new HashSet<WeakReference<Bitmap>>());
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();
        memoryCache = new LruCache<String, Bitmap>(memoryClass / 8 * 1024 * 1024) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    return value.getAllocationByteCount();
                }
                return value.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue.isMutable()) {
                    reusablePool.add(new WeakReference<Bitmap>(oldValue, referenceQueue));
                } else {
                    oldValue.recycle();
                }
            }
        };
        try {
            diskLruCache = DiskLruCache.open(new File(dir), Build.VERSION.SDK_INT, 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
        getReferenceQueue();
    }

    public void putBitmapToMemory(String key, Bitmap bitmap) {
        memoryCache.put(key, bitmap);
    }

    public Bitmap getBitmapFromMemory(String key) {
        return memoryCache.get(key);
    }

    public void clearMemoryCache() {
        memoryCache.evictAll();
    }

    public void putBitmapToDisk(String key, Bitmap bitmap) {
        DiskLruCache.Snapshot snapshot = null;
        OutputStream os = null;
        try {
            snapshot = diskLruCache.get(key);
            if (null == snapshot) {
                DiskLruCache.Editor editor = diskLruCache.edit(key);
                os = editor.newOutputStream(0);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, os);
                editor.commit();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != snapshot) {
                snapshot.close();
            }
            if (null != os) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Bitmap getBitmapFromDisk(String key, Bitmap reusable) {
        Bitmap bitmap = null;
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = diskLruCache.get(key);
            if (null != snapshot) {
                InputStream is = snapshot.getInputStream(0);
                options.inMutable = true;
                options.inBitmap = reusable;
                bitmap = BitmapFactory.decodeStream(is, null, options);
                if (null != bitmap) {
                    memoryCache.put(key, bitmap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != snapshot) {
                snapshot.close();
            }
        }
        return bitmap;
    }

    public Bitmap getReusable(int w, int h, int inSampleSize) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return null;
        }
        Bitmap reusable = null;
        Iterator<WeakReference<Bitmap>> iterator = reusablePool.iterator();
        while (iterator.hasNext()) {
            Bitmap bitmap = iterator.next().get();
            if (null != bitmap && checkInBitmap(bitmap, w, h, inSampleSize)) {
                reusable = bitmap;
                iterator.remove();
                break;
            } else {
                iterator.remove();
            }
        }
        return reusable;
    }

    private boolean checkInBitmap(Bitmap bitmap, int w, int h, int inSampleSize) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return w == bitmap.getWidth() && h == bitmap.getHeight() && inSampleSize == 1;
        }
        if (inSampleSize > 1) {
            w /= inSampleSize;
            h /= inSampleSize;
        }
        int byteCount = w * h * getPixelsCount(bitmap.getConfig());
        return byteCount <= bitmap.getAllocationByteCount();
    }

    private int getPixelsCount(Bitmap.Config config) {
        if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        }
        return 2;
    }
}
