package com.saleh.enezi;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AppStorage {
    public static final String ROOT_FOLDER = "بقالة العزي خاص";
    public static final String SUB_INVOICE_PHOTOS = "صور الفواتير";
    public static final String SUB_APP_IMAGES = "الصور التي ينتجها التطبيق";
    public static final String SUB_BACKUPS = "النسخ الاحتياطية";

    public static final String RELATIVE_ROOT = "Download/" + ROOT_FOLDER;
    public static final String RELATIVE_INVOICE_PHOTOS = RELATIVE_ROOT + "/" + SUB_INVOICE_PHOTOS;
    public static final String RELATIVE_APP_IMAGES = RELATIVE_ROOT + "/" + SUB_APP_IMAGES;
    public static final String RELATIVE_BACKUPS = RELATIVE_ROOT + "/" + SUB_BACKUPS;

    public static File getPublicRootFolder() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File root = new File(downloads, ROOT_FOLDER);
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public static File getInvoicesPhotosDir() {
        File dir = new File(getPublicRootFolder(), SUB_INVOICE_PHOTOS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getAppImagesDir() {
        File dir = new File(getPublicRootFolder(), SUB_APP_IMAGES);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getBackupsDir() {
        File dir = new File(getPublicRootFolder(), SUB_BACKUPS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getInternalInvoicesDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), SUB_INVOICE_PHOTOS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getInternalAppImagesDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), SUB_APP_IMAGES);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void initializeAllDirectories(Context context) {
        try {
            getInvoicesPhotosDir();
            getAppImagesDir();
            getBackupsDir();
            getInternalInvoicesDir(context);
            getInternalAppImagesDir(context);
        } catch (Exception ignored) {}
    }

    public static String saveInvoicePhoto(Context context, Bitmap bitmap, String fileName) {
        if (bitmap == null) return null;
        try {
            // 1. Direct file in Download/بقالة العزي خاص/صور الفواتير
            File publicDir = getInvoicesPhotosDir();
            File destPublic = new File(publicDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(destPublic)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos);
                fos.flush();
            }

            // 2. Also save to internal app storage as backup
            File internalDir = getInternalInvoicesDir(context);
            File destInternal = new File(internalDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(destInternal)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos);
                fos.flush();
            }

            // 3. Register with MediaStore / MediaScanner
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_INVOICE_PHOTOS);
                    Uri u = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        try (OutputStream out = context.getContentResolver().openOutputStream(u)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out);
                        }
                    }
                }
            } catch (Exception ignored) {}

            try {
                MediaScannerConnection.scanFile(context,
                        new String[]{destPublic.getAbsolutePath(), destInternal.getAbsolutePath()},
                        new String[]{"image/jpeg"}, null);
            } catch (Exception ignored) {}

            return destPublic.getAbsolutePath();
        } catch (Exception e) {
            // Fallback to internal storage
            try {
                File fallback = new File(getInternalInvoicesDir(context), fileName);
                try (FileOutputStream fos = new FileOutputStream(fallback)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.flush();
                }
                return fallback.getAbsolutePath();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static Uri saveAppImage(Context context, Bitmap bitmap, String fileName) {
        if (bitmap == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_APP_IMAGES);
                Uri u = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(u)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                }
                // Also write to public file path if possible
                try {
                    File dest = new File(getAppImagesDir(), fileName);
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    MediaScannerConnection.scanFile(context, new String[]{dest.getAbsolutePath()}, new String[]{"image/png"}, null);
                } catch (Exception ignored) {}
                return u;
            } else {
                File dir = getAppImagesDir();
                File f = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                MediaScannerConnection.scanFile(context, new String[]{f.getAbsolutePath()}, new String[]{"image/png"}, null);
                return Uri.fromFile(f);
            }
        } catch (Exception e) {
            try {
                File fallback = new File(getInternalAppImagesDir(context), fileName);
                try (FileOutputStream fos = new FileOutputStream(fallback)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                return Uri.fromFile(fallback);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static boolean saveDatabaseBackup(Context context, File srcDb, String fileName) throws IOException {
        if (!srcDb.exists()) return false;
        File tmp = new File(context.getCacheDir(), "backup_tmp.db");
        try {
            try (InputStream in = new FileInputStream(srcDb); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }
            if (!tmp.exists() || tmp.length() == 0) throw new IOException("empty backup");

            boolean saved = false;
            // 1. MediaStore relative path for Android 10+
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_BACKUPS);
                    Uri u = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        try (InputStream in = new FileInputStream(tmp); OutputStream out = context.getContentResolver().openOutputStream(u)) {
                            if (out != null) {
                                byte[] buf = new byte[16384];
                                int n;
                                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                                saved = true;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 2. Direct folder file in Download/بقالة العزي خاص/النسخ الاحتياطية
            try {
                File dest = new File(getBackupsDir(), fileName);
                try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    saved = true;
                }
                MediaScannerConnection.scanFile(context, new String[]{dest.getAbsolutePath()}, null, null);
            } catch (Exception ignored) {}

            return saved;
        } finally {
            try { tmp.delete(); } catch (Exception ignored) {}
        }
    }
}
