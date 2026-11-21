package com.example.android.notepad.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import com.example.android.notepad.model.Attachment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 媒体文件工具：把相册 / 相机 / 录音产出的内容统一复制到应用私有目录，
 * 再以文件路径写入数据库，避免外部 Uri 授权过期导致附件打不开。
 */
public final class MediaStoreUtil {

    private MediaStoreUtil() {
    }

    private static final Map<String, String> MIME_BY_EXT = new HashMap<>();
    private static final Map<String, String> EXT_BY_MIME = new HashMap<>();

    static {
        put("jpg", "image/jpeg");
        put("jpeg", "image/jpeg");
        put("png", "image/png");
        put("webp", "image/webp");
        put("gif", "image/gif");
        put("bmp", "image/bmp");
        put("heic", "image/heic");

        put("mp3", "audio/mpeg");
        put("m4a", "audio/mp4");
        put("aac", "audio/aac");
        put("wav", "audio/wav");
        put("ogg", "audio/ogg");
        put("amr", "audio/amr");

        put("mp4", "video/mp4");
        put("3gp", "video/3gpp");
        put("mov", "video/quicktime");
        put("webm", "video/webm");
        put("mkv", "video/x-matroska");
    }

    private static void put(String ext, String mime) {
        MIME_BY_EXT.put(ext, mime);
        if (!EXT_BY_MIME.containsKey(mime)) {
            EXT_BY_MIME.put(mime, "." + ext);
        }
    }

    public static File mediaDir(Context context) {
        File dir = new File(context.getFilesDir(), "media");
        if (!dir.exists() && !dir.mkdirs()) {
            // 目录创建失败时退化为内部缓存目录，功能仍可用
            return context.getCacheDir();
        }
        return dir;
    }

    /** 把外部 Uri 复制到私有 media 目录，返回新文件。 */
    public static File importUri(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String mime = resolver.getType(uri);
        String displayName = queryDisplayName(resolver, uri);

        String ext = EXT_BY_MIME.get(mime == null ? "" : mime);
        if (ext == null) {
            ext = extensionOf(displayName);
        }
        if (ext == null || ext.isEmpty()) {
            ext = ".dat";
        }

        File target = new File(mediaDir(context), prefixFor(mime) + System.currentTimeMillis() + ext);
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("无法读取所选文件");
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return target;
    }

    private static String prefixFor(String mime) {
        if (mime == null) {
            return "FILE_";
        }
        if (mime.startsWith("image/")) {
            return "IMG_";
        }
        if (mime.startsWith("audio/")) {
            return "AUD_";
        }
        if (mime.startsWith("video/")) {
            return "VID_";
        }
        return "FILE_";
    }

    public static File newCameraFile(Context context) {
        return new File(mediaDir(context), "IMG_" + System.currentTimeMillis() + ".jpg");
    }

    public static Uri uriFor(Context context, File file) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }

    public static String mimeOf(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String mime = MIME_BY_EXT.get(name.substring(dot + 1));
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    public static int typeOf(String mime) {
        if (mime != null && mime.startsWith("image/")) {
            return Attachment.TYPE_IMAGE;
        }
        if (mime != null && mime.startsWith("video/")) {
            return Attachment.TYPE_VIDEO;
        }
        return Attachment.TYPE_AUDIO;
    }

    /** 读取音视频时长（毫秒），失败返回 0。 */
    public static long durationOf(Context context, File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
                // 忽略释放异常
            } catch (RuntimeException ignored) {
                // 忽略释放异常
            }
        }
    }

    /** 调起系统播放器打开视频/音频，找不到应用时返回 false。 */
    public static boolean openExternally(Activity activity, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uriFor(activity, file), mimeOf(file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
            // 某些 provider 不支持查询，忽略即可
        }
        return null;
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : null;
    }
}
