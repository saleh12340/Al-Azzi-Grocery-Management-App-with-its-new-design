package com.saleh.enezi;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupReceiver extends BroadcastReceiver {
    static final int REQ=7199;

    public static void schedule(Context c){
        try{
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            Intent in=new Intent(c,BackupReceiver.class);
            PendingIntent pi=PendingIntent.getBroadcast(c,REQ,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Calendar cal=Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY,23); cal.set(Calendar.MINUTE,59);
            cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
            if(cal.getTimeInMillis()<=System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR,1);
            long when=cal.getTimeInMillis();
            if(Build.VERSION.SDK_INT>=23){
                try{ am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi); return; }
                catch(SecurityException ignored){}
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        }catch(Exception ignored){}
    }

    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){ schedule(context); return; }
        backup(context);
        schedule(context);
    }

    static boolean backup(Context c){
        String fn="نسخة_احتياطية_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+".db";
        File src=c.getDatabasePath("alazzi_grocery_runtime_v5.db");
        File snapshot=new File(c.getCacheDir(),"alazzi_backup_snapshot.db");
        if(!src.isFile()||src.length()<=0) return false;
        SQLiteDatabase d=null;
        try{
            if(snapshot.exists()&&!snapshot.delete())throw new IOException("تعذر تنظيف النسخة المؤقتة");
            d=SQLiteDatabase.openDatabase(src.getPath(),null,SQLiteDatabase.OPEN_READWRITE);
            // القفل الحصري يمنع عمليات الكتابة أثناء أخذ لقطة ملف قاعدة البيانات.
            d.beginTransaction();
            android.database.Cursor check=d.rawQuery("PRAGMA quick_check",null);
            boolean valid=check.moveToFirst()&&"ok".equalsIgnoreCase(check.getString(0));
            check.close();
            if(!valid)throw new IOException("فشل فحص سلامة قاعدة البيانات");
            try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(snapshot,false)){
                byte[] buffer=new byte[16384];int count;
                while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                out.flush();
            }
            if(!snapshot.isFile()||snapshot.length()!=src.length())throw new IOException("النسخة المؤقتة غير مكتملة");
            d.setTransactionSuccessful();
            d.endTransaction();
            d.close();d=null;
            return AppStorage.saveDatabaseBackup(c,snapshot,fn);
        }catch(Exception e){
            android.util.Log.e("AlAzziBackup","Database backup failed",e);
            return false;
        }finally{
            if(d!=null){
                try{if(d.inTransaction())d.endTransaction();}catch(Exception ignored){}
                try{d.close();}catch(Exception ignored){}
            }
            if(snapshot.exists())snapshot.delete();
        }
    }
}
