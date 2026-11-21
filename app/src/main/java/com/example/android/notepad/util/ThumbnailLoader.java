package com.example.android.notepad.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量缩略图加载器：内存 LruCache + 单线程池解码，避免为了一张列表图引入图片框架。
 * 图片走 BitmapFactory 采样解码，视频用 MediaMetadataRetriever 取首帧。
 */
public final class ThumbnailLoader {

    private ThumbnailLoader() {
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void load(File file, ImageView view, boolean video, int targetSize) {
        if (file == null || !file.exists()) {
            view.setImageDrawable(null);
            return;
        }
        final String key = file.getAbsolutePath() + "#" + targetSize + (video ? "#v" : "#i");
        view.setTag(key);

        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);

        POOL.execute(() -> {
            Bitmap bitmap = video ? videoFrame(file) : decodeSampled(file, targetSize);
            if (bitmap != null && !bitmap.isRecycled()) {
                CACHE.put(key, bitmap);
            }
            MAIN.post(() -> {
                Object tag = view.getTag();
                if (key.equals(tag) && bitmap != null && !bitmap.isRecycled()) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** 图片大图预览 */
    public static void loadFull(File file, ImageView view) {
        load(file, view, false, 1600);
    }

    private static Bitmap decodeSampled(File file, int targetSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > targetSize * 2) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError | Exception e) {
            return null;
        }
    }

    private static Bitmap videoFrame(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(0);
            if (frame == null) {
                return null;
            }
            // 缩放到卡片尺寸，避免大图占用内存
            int width = frame.getWidth();
            int height = frame.getHeight();
            if (width <= 0 || height <= 0) {
                return frame;
            }
            float scale = Math.min(320f / width, 320f / height);
            if (scale >= 1f) {
                return frame;
            }
            return Bitmap.createScaledBitmap(frame, (int) (width * scale), (int) (height * scale), true);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // 忽略释放异常
            }
        }
    }

    public static void clearCache() {
        CACHE.evictAll();
    }
}
