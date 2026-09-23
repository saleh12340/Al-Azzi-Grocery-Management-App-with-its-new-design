package com.saleh.enezi;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * مركز موحد للمشاركة والمستندات.
 * كل الملفات المشاركة تستخدم FileProvider وصلاحيات قراءة مؤقتة.
 */
public final class DocumentCenter {
    private DocumentCenter(){}

    public static void shareText(Activity a, String text){
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT,text==null?"":text);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i,"مشاركة"));
    }

    public static void shareFile(Activity a, File file, String mime, String caption, String chooser){
        if(file==null || !file.exists()) throw new IllegalArgumentException("الملف غير موجود");
        Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".fileprovider",file);
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType(mime==null?"application/octet-stream":mime);
        i.putExtra(Intent.EXTRA_STREAM,uri);
        if(caption!=null&&!caption.isEmpty()) i.putExtra(Intent.EXTRA_TEXT,caption);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i,chooser==null?"مشاركة الملف":chooser));
    }

    public static void sharePdf(Activity a, File file, String caption, String chooser){
        shareFile(a,file,"application/pdf",caption,chooser==null?"مشاركة PDF":chooser);
    }

    public static void shareImage(Activity a, File file, String caption, String chooser){
        shareFile(a,file,"image/png",caption,chooser==null?"مشاركة الصورة":chooser);
    }

    public static String copyText(Context c,String text){
        ClipboardManager cm=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("نص المشاركة",text==null?"":text));
        return text;
    }
}
