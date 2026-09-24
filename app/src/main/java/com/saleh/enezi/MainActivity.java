package com.saleh.enezi;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextPaint;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import android.database.Cursor;
import android.database.sqlite.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int REQ_CONTACTS=4101, PICK_CONTACT=4102, REQ_CAMERA_SCAN=4103, REQ_GALLERY_SCAN=4104, REQ_PERM_CAMERA=4105, REQ_AUDIO=5110, REQ_VOICE_SEARCH=5111, REQ_VOICE_DETAIL=5112;
    EditText customerNameInput, customerPhoneInput;
    static final int GREEN=Color.rgb(30,78,121), DARK=Color.rgb(15,42,68), GOLD=Color.rgb(214,158,52), BLUE=Color.rgb(37,99,235), RED=Color.rgb(207,61,61);
    static final int BG=Color.rgb(245,247,250), TEXT=Color.rgb(20,28,38), MUTED=Color.rgb(76,88,102), CARD=Color.WHITE;
    volatile boolean startupFinished=false; DB db; LinearLayout root,content,bottom; PopupWindow learningPopup; TextView pageTitle; int textSize=16; String currentPage="الرئيسية"; ArrayDeque<String> pageStack=new ArrayDeque<>(); long currentNotePageId=-1; int noteFontSize=14; boolean noteScrollMode=true;
    Uri cameraScanTempUri; Bitmap scanRawBitmap; String scanFilterMode="magic"; float scanRotation=0; String scanCategoryFilter="الكل"; String scanSearchQuery="";
    EditText transferSenderName,transferSenderPhone,transferReceiverName,transferReceiverPhone,transferContactNameTarget,transferContactPhoneTarget;
    EditText activeVoiceField;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);

        // تجهيز قاعدة البيانات قبل فتح أي شاشة يمنع سباقاً كان يسمح للمستخدم
        // بفتح شاشة تعتمد على db قبل اكتمال تهيئتها، وهو سبب محتمل لانهيار التطبيق.
        startupFinished=false;
        try{
            DB localDb=new DB(this);
            SQLiteDatabase writable=localDb.getWritableDatabase();
            writable.setForeignKeyConstraintsEnabled(false);
            db=localDb;
            try{ AppStorage.initializeAllDirectories(this); }catch(Throwable e){
                android.util.Log.w("AlAzziStartup","Storage initialization skipped",e);
            }
            try{ BackupReceiver.schedule(this); }catch(Throwable e){
                android.util.Log.w("AlAzziStartup","Backup scheduling skipped",e);
            }
            startupFinished=true;
            try{ home(); }catch(Throwable e){
                android.util.Log.e("AlAzziStartup","Initial UI failed",e);
                showSafeHome(e);
            }
        }catch(Throwable e){
            android.util.Log.e("AlAzziStartup","Database startup failed",e);
            startupFinished=true;
            try{ home(); }catch(Throwable uiError){
                android.util.Log.e("AlAzziStartup","Fallback home failed",uiError);
                showSafeHome(uiError);
            }
            Toast.makeText(this,
                "تعذر تجهيز قاعدة البيانات. بعض العمليات ستحتاج إعادة المحاولة.",
                Toast.LENGTH_LONG).show();
        }
    }

    void showSafeHome(Throwable error){
        currentPage="الرئيسية"; pageStack.clear();
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setBackgroundColor(BG); box.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView head=tv("بقالة العزي للمواد الغذائية",21);
        head.setTextColor(Color.WHITE); head.setGravity(Gravity.CENTER); head.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        head.setBackground(rounded(DARK,dp(14)));
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(64)));
        addSafeHomeButton(box,"🧾 فواتير البيع",v->invoiceHistory());
        addSafeHomeButton(box,"👥 العملاء والحسابات",v->customers());
        addSafeHomeButton(box,"🛒 فواتير الشراء",v->purchaseInvoices());
        addSafeHomeButton(box,"📦 المخزون والأصناف",v->inventory());
        addSafeHomeButton(box,"📊 التقارير",v->reports());
        addSafeHomeButton(box,"📝 الملاحظات",v->notes());
        addSafeHomeButton(box,"💸 الحوالات",v->transfers());
        TextView status=tv("تم تشغيل وضع الواجهة الآمن.\nسبب الخطأ: "+(error==null?"غير معروف":error.getClass().getSimpleName()),12);
        status.setTextColor(MUTED); status.setGravity(Gravity.CENTER);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(58)));
        setContentView(box);
    }
    void addSafeHomeButton(LinearLayout box,String label,View.OnClickListener click){
        Button b=button(label); b.setTextSize(16); b.setTextColor(TEXT);
        b.setBackground(outlined(CARD,dp(1),12)); b.setOnClickListener(click);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52)); p.setMargins(0,dp(6),0,0); box.addView(b,p);
    }

    void showStartupRecovery(Throwable error){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24),dp(24),dp(24),dp(24));
        box.setBackgroundColor(BG);

        TextView title=new TextView(this);
        title.setText("بقالة العزي للمواد الغذائية");
        title.setTextSize(20);
        title.setTextColor(DARK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView msg=new TextView(this);
        msg.setText("تعذر تشغيل الشاشة الرئيسية.\\nتم إيقاف الخطأ لمنع ظهور شاشة بيضاء.\\nاضغط «إعادة المحاولة».");
        msg.setTextSize(15);
        msg.setTextColor(MUTED);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg,new LinearLayout.LayoutParams(-1,dp(95)));

        Button retry=new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setTextSize(15);
        retry.setAllCaps(false);
        retry.setOnClickListener(v->{
            try{
                if(db!=null) db.close();
                db=new DB(this);
                AppStorage.initializeAllDirectories(this);
                home();
            }catch(Throwable e){
                android.util.Log.e("AlAzziStartup","Retry failed",e);
                Toast.makeText(this,"لا يزال هناك خطأ في تشغيل التطبيق.",Toast.LENGTH_LONG).show();
            }
        });
        box.addView(retry,new LinearLayout.LayoutParams(-1,dp(52)));

        Button exit=new Button(this);
        exit.setText("خروج");
        exit.setTextSize(14);
        exit.setAllCaps(false);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(48));
        ep.setMargins(0,dp(10),0,0);
        box.addView(exit,ep);
        exit.setOnClickListener(v->finish());

        setContentView(box);
    }

    void confirmExit(){
        new AlertDialog.Builder(this)
            .setTitle("تأكيد الخروج")
            .setMessage("هل تريد الخروج من التطبيق؟")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("خروج",(d,w)->finish())
            .show();
    }
    @Override public void onBackPressed(){ goBack(); }
    void goBack(){
        if(pageStack.isEmpty()){ confirmExit(); return; }
        String prev=pageStack.pop();
        if(prev.equals("الرئيسية")) home();
        else if(prev.equals("الحسابات")||prev.equals("العملاء")) customers();
        else if(prev.equals("الفواتير")) invoiceHistory();
        else if(prev.equals("فواتير الشراء")) purchaseInvoices();
        else if(prev.equals("المخزون")) inventory();
        else if(prev.equals("التقارير")) reports(); else if(prev.equals("الملاحظات")) notes();
        else if(prev.equals("ماسح الفواتير")||prev.equals("الماسح الضوئي")) scanner();
        else if(prev.equals("الحوالات")) transfers();
        else home();
    }

    GradientDrawable rounded(int color,float radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g; }
    GradientDrawable outlined(int color,int stroke,float radius){ GradientDrawable g=rounded(color,radius); g.setStroke(stroke,Color.rgb(174,185,198)); return g; }
    float fitText(float z){return Math.max(8f, z);}
    void normalizeAppText(View v){
        if(v instanceof TextView){
            TextView t=(TextView)v;
            String x=t.getText()==null?"":t.getText().toString().trim();
            if(!x.matches("[\\p{So}\\p{Cs}\\uFE0F\\u200D ]+")) { if(t.getTextSize()<spToPx(16f)) t.setTextSize(16f); }
            t.setIncludeFontPadding(true);
            t.setHorizontallyScrolling(false);
            t.setEllipsize(null);
            if(!(t instanceof EditText)){
                t.setSingleLine(false);
                t.setMaxLines(4);
            }
            if(Build.VERSION.SDK_INT>=28){
                try{t.setFallbackLineSpacing(true);}catch(Throwable ignored){}
                try{t.setElegantTextHeight(true);}catch(Throwable ignored){}
            }
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) normalizeAppText(g.getChildAt(i));
        }
    }

    void fitInside(View v,float maxSp,float minSp){
        if(v instanceof TextView){
            TextView t=(TextView)v;
            t.setIncludeFontPadding(true);
            t.setHorizontallyScrolling(false);
            t.setEllipsize(null);
            t.setSingleLine(false);
            t.setMaxLines(4);
            if(android.os.Build.VERSION.SDK_INT>=23){
                try{t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);}catch(Throwable ignored){}
            }
            // لا نصغّر الخط بقوة ولا نقصّ النص: الأولوية لظهور البيانات كاملة.
            float size=Math.max(14f,Math.max(minSp,maxSp));
            t.setTextSize(size);
            t.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
                public void onLayoutChange(View view,int l,int top,int r,int bottom,int ol,int ot,int orr,int ob){
                    if(!(view instanceof TextView)) return;
                    TextView x=(TextView)view;
                    if(x.getLineCount()>1){
                        ViewParent pp=x.getParent();
                        if(pp instanceof View){
                            View pv=(View)pp;
                            ViewGroup.LayoutParams lp=pv.getLayoutParams();
                            if(lp instanceof LinearLayout.LayoutParams && lp.height>0){
                                lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;
                                pv.setLayoutParams(lp);
                            }
                        }
                    }
                }
            });
        }
    }
    void autoFitText(TextView t,float maxSp,float minSp,float stepSp){
        if(t==null)return;
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setEllipsize(null);
        t.setHorizontallyScrolling(false);
        t.setIncludeFontPadding(true);
        if(Build.VERSION.SDK_INT>=28){
            try{t.setFallbackLineSpacing(true);}catch(Throwable ignored){}
            try{t.setElegantTextHeight(true);}catch(Throwable ignored){}
        }
        if(Build.VERSION.SDK_INT>=26){
            try{
                int min=Math.max(8,Math.round(minSp));
                int max=Math.max(min+1,Math.round(maxSp));
                int step=Math.max(1,Math.round(stepSp<=0?1:stepSp));
                t.setAutoSizeTextTypeUniformWithConfiguration(min,max,step,android.util.TypedValue.COMPLEX_UNIT_SP);
            }catch(Throwable ignored){}
        }else{
            t.setTextSize(Math.max(minSp,maxSp));
        }
    }
    float spToPx(float sp){return sp*getResources().getDisplayMetrics().scaledDensity;}
    TextView tv(String s,float z){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(fitText(z*1.10f)); v.setTextColor(TEXT);
        v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); v.setPadding(dp(7),dp(5),dp(7),dp(5));
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); v.setTextDirection(View.TEXT_DIRECTION_RTL);
        if(Build.VERSION.SDK_INT>=28){try{v.setFallbackLineSpacing(true);}catch(Throwable ignored){} try{v.setElegantTextHeight(true);}catch(Throwable ignored){}}
        fitInside(v,fitText(z),8f); return v;
    }
    Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(10),dp(5),dp(10),dp(5)); b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null); b.setIncludeFontPadding(true); b.setMaxLines(2);
        b.setEllipsize(null); b.setHorizontallyScrolling(false);
        if(Build.VERSION.SDK_INT>=28){try{b.setFallbackLineSpacing(true);}catch(Throwable ignored){} try{b.setElegantTextHeight(true);}catch(Throwable ignored){}}
        b.setTextColor(TEXT);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(CARD); bg.setCornerRadius(dp(11)); bg.setStroke(dp(1),Color.rgb(213,223,234));
        b.setBackground(bg); b.setElevation(dp(1));
        b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); fitInside(b,17f,13f);
        return b;
    }
    EditText field(String h){
        EditText e=new EditText(this);
        e.setHint(h); e.setTextSize(15); e.setSingleLine(true); e.setIncludeFontPadding(false); e.setMaxLines(1);
        fitInside(e,18f,14f);
        e.setTextColor(TEXT); e.setHintTextColor(Color.rgb(118,132,148));
        e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(outlined(Color.rgb(252,253,255),dp(1),12));
        e.setElevation(dp(1));
        e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); e.setTextDirection(View.TEXT_DIRECTION_RTL);
        e.setSelectAllOnFocus(true);
        e.setOnClickListener(v -> e.selectAll());
        e.setOnFocusChangeListener((v,has)->{
            if(has){
                e.setBackground(outlined(Color.rgb(247,251,255),dp(2),12));
                e.postDelayed(() -> e.selectAll(),60);
            }else{
                e.setBackground(outlined(Color.rgb(252,253,255),dp(1),12));
            }
        });
        attachLearning(e,h);
        return e;
    }
    String learningKind(String hint){
        String h=hint==null?"":hint;
        if(h.contains("رقم")||h.contains("هاتف")) return "phone";
        if(h.contains("صنف")||h.contains("منتج")) return "item";
        if(h.contains("عميل")) return "customer";
        if(h.contains("مورد")) return "supplier";
        if(h.contains("اسم")) return "name";
        return "general";
    }
    void attachLearning(EditText e,String hint){
        final String kind=learningKind(hint);
        final Handler h=new Handler(Looper.getMainLooper());
        final Runnable[] pending=new Runnable[1];
        e.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                if(pending[0]!=null) h.removeCallbacks(pending[0]);
                final String q=s==null?"":s.toString().trim();
                if(q.length()<2){ dismissLearningSuggestions(); return; }
                pending[0]=()->{
                    if(e.hasFocus()) showLearningSuggestions(e,q,kind);
                };
                h.postDelayed(pending[0],180);
            }
            public void afterTextChanged(Editable x){}
        });
        e.setOnFocusChangeListener((v,has)->{
            if(has){
                e.postDelayed(e::selectAll,60);
                final String q=e.getText().toString().trim();
                if(q.length()>=2) h.postDelayed(()->{if(e.hasFocus())showLearningSuggestions(e,q,kind);},180);
            }else{
                String value=e.getText().toString().trim();
                if(value.length()>=2) learnTypedValue(value,kind);
                h.postDelayed(()->{if(!e.hasFocus())dismissLearningSuggestions();},140);
            }
        });
    }
    void learnTypedValue(String value,String kind){
        if(value==null||value.trim().length()<2)return;
        android.content.SharedPreferences p=getSharedPreferences("learned_suggestions",MODE_PRIVATE);
        java.util.HashSet<String> set=new java.util.HashSet<>(p.getStringSet(kind,new java.util.HashSet<String>()));
        set.remove(value.trim());set.add(value.trim());
        p.edit().putStringSet(kind,set).apply();
    }
    void dismissLearningSuggestions(){if(learningPopup!=null&&learningPopup.isShowing())learningPopup.dismiss();}
    void showLearningSuggestions(EditText anchor,String query,String kind){
        if(anchor==null||query.length()<2)return;
        android.content.SharedPreferences p=getSharedPreferences("learned_suggestions",MODE_PRIVATE);
        java.util.ArrayList<String> values=new java.util.ArrayList<>(p.getStringSet(kind,new java.util.HashSet<String>()));
        java.util.Collections.sort(values,(a,b)->Integer.compare(b.length(),a.length()));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(4),dp(4),dp(4),dp(4));box.setBackground(outlined(CARD,dp(1),10));
        int n=0;
        for(String value:values){
            String q=query.trim().toLowerCase(java.util.Locale.ROOT); String vv=value.trim().toLowerCase(java.util.Locale.ROOT); if(!vv.contains(q)||n>=5)continue;
            Button b=button(value);b.setTextSize(15);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setMaxLines(1);b.setEllipsize(TextUtils.TruncateAt.END);
            b.setOnClickListener(v->{anchor.setText(value);anchor.setSelection(anchor.length());dismissLearningSuggestions();});
            box.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));n++;
        }
        if(n==0){dismissLearningSuggestions();return;}
        dismissLearningSuggestions();
        int screenWidth=getResources().getDisplayMetrics().widthPixels; int popupWidth=Math.min(screenWidth-dp(16),Math.max(anchor.getWidth(),dp(280)));
        learningPopup=new PopupWindow(box,popupWidth,WindowManager.LayoutParams.WRAP_CONTENT,false);
        learningPopup.setTouchable(true);
        learningPopup.setFocusable(false);
        learningPopup.setOutsideTouchable(true);
        learningPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        learningPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        learningPopup.setBackgroundDrawable(rounded(CARD,dp(10)));
        learningPopup.setElevation(dp(8));
        learningPopup.showAsDropDown(anchor,(anchor.getWidth()-popupWidth)/2,dp(3));
    }
EditText numberField(String h){
        EditText e=field(h);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setRawInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        return e;
    }
    EditText inputNumber(String h){return numberField(h);}
    EditText phoneField(String h){
        EditText e=field(h);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        e.setRawInputType(InputType.TYPE_CLASS_PHONE);
        return e;
    }
    void addField(EditText e){
        content.addView(e,new LinearLayout.LayoutParams(-1,dp(46)));
        addSpace(4);
    }
    void addSpace(int h){Space s=new Space(this); content.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    TextView section(String s){
        TextView v=tv("  "+s,15); v.setTextColor(DARK); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setSingleLine(true); v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12),0,dp(12),0);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.rgb(235,242,249)); bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1),Color.rgb(211,223,236)); v.setBackground(bg);
        fitInside(v,15f,13f);
        content.addView(v,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(5); return v;
    }

    void base(String title){
        base(title,true);
    }
    void base(String title,boolean withDefaultNav){
        if(!title.equals(currentPage)){
            pageStack.push(currentPage);
            currentPage=title;
        }
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),dp(7),dp(8),dp(7));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{GREEN,DARK}));
        
        Button back=button("‹");
        back.setTextColor(Color.WHITE); back.setTextSize(28);
        back.setBackgroundColor(Color.TRANSPARENT); back.setElevation(0);
        back.setOnClickListener(v->goBack());
        bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(50)));

        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.setPadding(dp(8),0,dp(8),0);
        TextView logo=tv("بقالة العزي",17);
        logo.setTextColor(Color.WHITE); logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView pt=tv(title,18);
        pt.setTextColor(Color.WHITE); pt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        pt.setSingleLine(false); pt.setMaxLines(2); pt.setEllipsize(null);
        titleBox.addView(logo,new LinearLayout.LayoutParams(-1,dp(22)));
        titleBox.addView(pt,new LinearLayout.LayoutParams(-1,dp(28)));
        bar.addView(titleBox,new LinearLayout.LayoutParams(0,dp(52),1));

        TextView badge=tv("إدارة",11);
        badge.setTextColor(Color.WHITE); badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg=new GradientDrawable();
        badgeBg.setColor(Color.argb(55,255,255,255)); badgeBg.setCornerRadius(dp(18));
        badge.setBackground(badgeBg);
        bar.addView(badge,new LinearLayout.LayoutParams(dp(56),dp(34)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(66)));

        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true); sv.setClipToPadding(false);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8),dp(6),dp(8),dp(14));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if(!"الرئيسية".equals(title)){
            LinearLayout pageHero=new LinearLayout(this);
            pageHero.setOrientation(LinearLayout.VERTICAL);
            pageHero.setPadding(dp(12),dp(6),dp(12),dp(6));
            GradientDrawable heroBg=new GradientDrawable();
            heroBg.setColor(Color.WHITE); heroBg.setCornerRadius(dp(14));
            heroBg.setStroke(dp(1),Color.rgb(221,229,238));
            pageHero.setBackground(heroBg); pageHero.setElevation(dp(1));
            TextView heroTitle=tv(title,17);
            heroTitle.setTextColor(GREEN); heroTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            TextView heroSub=tv("واجهة مرتبة وسريعة لإدارة بياناتك",11.5f);
            heroSub.setTextColor(MUTED);
            pageHero.addView(heroTitle,new LinearLayout.LayoutParams(-1,dp(25)));
            pageHero.addView(heroSub,new LinearLayout.LayoutParams(-1,dp(20)));
            content.addView(pageHero,new LinearLayout.LayoutParams(-1,dp(50)));
            addSpace(5);
        }

        sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        // طبّق قواعد الملاءمة بعد اكتمال الشجرة حتى لا تُخفى الكلمات خلف البطاقات.
        root.postDelayed(() -> normalizeAppText(root), 40);
        if(withDefaultNav) attachDefaultBottomNav(title);
    }

    void attachDefaultBottomNav(String activeTitle){
        if(bottom==null) return;
        bottom.removeAllViews();
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nav.setPadding(dp(3),dp(3),dp(3),dp(3));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(CARD);
        bg.setStroke(dp(1),Color.rgb(174,185,198));
        nav.setBackground(bg);
        if(Build.VERSION.SDK_INT>=21) nav.setElevation(dp(6));

        String[] labels={"الرئيسية","الفواتير","العملاء","المشتريات","المخزون","الملاحظات","الحوالات"};
        String[] icons={"🏠","🧾","👥","🛒","📦","📝","💸"};
        String[] keys={"الرئيسية","سجل الفواتير","الحسابات والعملاء","فواتير الشراء","المخزون","الملاحظات","الحوالات"};

        for(int i=0;i<7;i++){
            final int idx=i;
            boolean isActive = activeTitle!=null && (activeTitle.contains(labels[i]) || activeTitle.equals(keys[i]) || (i==1 && activeTitle.contains("فاتورة") && !activeTitle.contains("شراء")) || (i==3 && activeTitle.contains("شراء")));
            LinearLayout tab=new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0,dp(2),0,dp(2));
            if(isActive){
                GradientDrawable tabBg=new GradientDrawable();
                tabBg.setColor(Color.rgb(226,236,247));
                tabBg.setCornerRadius(dp(8));
                tab.setBackground(tabBg);
            }
            TextView iconTv=new TextView(this);
            iconTv.setText(icons[i]);
            iconTv.setTextSize(21);
            iconTv.setGravity(Gravity.CENTER);
            tab.addView(iconTv,new LinearLayout.LayoutParams(-1,-2));

            TextView labelTv=new TextView(this);
            labelTv.setText(labels[i]);
            labelTv.setTextSize(13);
            labelTv.setGravity(Gravity.CENTER);
            labelTv.setTextColor(isActive ? DARK : TEXT);
            if(isActive) labelTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            tab.addView(labelTv,new LinearLayout.LayoutParams(-1,-2));

            tab.setOnClickListener(v->{
                hideKeyboard();
                if(idx==0) home();
                else if(idx==1) invoiceHistory();
                else if(idx==2) customers();
                else if(idx==3) purchaseInvoices();
                else if(idx==4) inventory();
                else if(idx==5) notes();
                else if(idx==6) transfers();
            });
            LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,dp(62),1);
            tlp.setMargins(dp(2),0,dp(2),0);
            nav.addView(tab,tlp);
        }
        bottom.addView(nav,new LinearLayout.LayoutParams(-1,dp(66)));
    }
    void navigate(String n){hideKeyboard(); if(n.equals("الرئيسية"))home();else if(n.equals("العملاء")||n.equals("الحسابات"))customers();else if(n.equals("الفواتير"))invoice();else if(n.equals("فواتير الشراء"))purchaseInvoices();else if(n.equals("المخزون"))inventory();else if(n.equals("ماسح الفواتير")||n.equals("الماسح الضوئي"))scanner();else if(n.equals("الحوالات"))transfers();else if(n.equals("الملاحظات"))notes();else reports();}
    void importContact(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission("android.permission.READ_CONTACTS")!=PackageManager.PERMISSION_GRANTED){ requestPermissions(new String[]{"android.permission.READ_CONTACTS"},REQ_CONTACTS); return; }
        try{ Intent i=new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI); startActivityForResult(i,PICK_CONTACT); }catch(Exception e){ Toast.makeText(this,"تعذر فتح جهات الاتصال",Toast.LENGTH_SHORT).show(); }
    }
    void startVoiceInput(EditText target,String prompt,int requestCode){
        if(target==null)return;
        activeVoiceField=target;
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            target.setTag(requestCode==REQ_VOICE_DETAIL?"detailVoice":"searchVoice");
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},REQ_AUDIO); return;
        }
        try{
            Intent i=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"ar-YE");
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,5);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,prompt);
            startActivityForResult(i,requestCode);
        }catch(Exception e){Toast.makeText(this,"الإدخال الصوتي غير متاح على هذا الجهاز",Toast.LENGTH_SHORT).show();}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_AUDIO){
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED&&activeVoiceField!=null){
                boolean detail=activeVoiceField.getTag()!=null&&"detailVoice".equals(activeVoiceField.getTag());
                startVoiceInput(activeVoiceField,detail?"تحدث بالبيان أو تفاصيل العملية":"تحدث باسم العميل أو رقم الهاتف",detail?REQ_VOICE_DETAIL:REQ_VOICE_SEARCH);
            }else Toast.makeText(this,"يلزم السماح بالميكروفون لاستخدام الإدخال الصوتي",Toast.LENGTH_SHORT).show();
        }
    }
    void addVoiceButton(LinearLayout container,EditText target,int requestCode,String prompt){
        Button mic=button("🎙"); mic.setContentDescription("إدخال صوتي"); mic.setTextSize(14); mic.setPadding(0,0,0,0);
        mic.setTextColor(GREEN); mic.setBackground(outlined(CARD,dp(1),10));
        mic.setOnClickListener(v->{target.setTag(requestCode==REQ_VOICE_DETAIL?"detailVoice":"searchVoice");startVoiceInput(target,prompt,requestCode);});
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(42),dp(42));mp.setMargins(dp(4),0,0,0);container.addView(mic,mp);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_CONTACT&&resultCode==RESULT_OK&&data!=null){
            Cursor c=null;
            try{
                c=getContentResolver().query(data.getData(),new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);
                if(c!=null&&c.moveToFirst()){
                    String n=c.getString(0),p=c.getString(1);
                    if(customerNameInput!=null)customerNameInput.setText(n==null?"":n);
                    if(customerPhoneInput!=null)customerPhoneInput.setText(p==null?"":p);
                    if(customerNameInput!=null)customerNameInput.requestFocus();
                    Toast.makeText(this,"تم استيراد اسم العميل ورقم الهاتف",Toast.LENGTH_SHORT).show();
                }
            }catch(Exception e){Toast.makeText(this,"تعذر قراءة بيانات جهة الاتصال",Toast.LENGTH_SHORT).show();}
            finally{if(c!=null)c.close();}
        }else if((requestCode==REQ_VOICE_SEARCH||requestCode==REQ_VOICE_DETAIL)&&resultCode==RESULT_OK&&data!=null){
            ArrayList<String> results=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if(activeVoiceField!=null&&results!=null&&!results.isEmpty()){
                String spoken=results.get(0)==null?"":results.get(0).trim();
                if(!spoken.isEmpty()){activeVoiceField.setText(spoken);activeVoiceField.setSelection(activeVoiceField.length());}
            }
            activeVoiceField=null;
        }else if(requestCode==8801&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            try{
                if(db!=null)db.close();
                restoreDatabaseFromUri(this,data.getData());
                db=new DB(this);
                Toast.makeText(this,"تم استرجاع النسخة الاحتياطية بنجاح.",Toast.LENGTH_LONG).show();
                home();
            }catch(Exception e){Toast.makeText(this,"تعذر استرجاع النسخة الاحتياطية: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }else if(requestCode==4106&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Cursor c=null;try{c=getContentResolver().query(data.getData(),new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);if(c!=null&&c.moveToFirst()){if(transferContactPhoneTarget!=null)transferContactPhoneTarget.setText(c.getString(1));}}catch(Exception ignored){}finally{if(c!=null)c.close();}
        }else if(requestCode==REQ_CAMERA_SCAN&&resultCode==RESULT_OK){
            handleScanCameraResult(data);
        }else if(requestCode==REQ_GALLERY_SCAN&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            handleScanGalleryResult(data.getData());
        }
    }

    void hideKeyboard(){View v=getCurrentFocus();if(v!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);v.clearFocus();}}

    TextView cardTitle(String title,String sub){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(10),dp(8),dp(10),dp(8));c.setBackground(outlined(CARD,dp(1),12));c.setElevation(dp(3));
        TextView a=tv(title,16);a.setTextColor(GREEN);a.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(a);
        TextView b=tv(sub,13);b.setTextColor(MUTED);c.addView(b);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(72)); cp.setMargins(0,0,0,6); content.addView(c,cp);return a;
    }    void addAction(String a,String sub,View.OnClickListener l){        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(10),dp(6),dp(10),dp(6));c.setBackground(outlined(CARD,dp(1),12));c.setElevation(dp(3));
        Button b=button(a);b.setTextSize(15);b.setTextColor(TEXT);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setOnClickListener(l);c.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView s=tv(sub,13);s.setTextColor(MUTED);c.addView(s,new LinearLayout.LayoutParams(-1,28));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(82)); ap.setMargins(0,0,0,6); content.addView(c,ap);
    }

    View createMetricCard(String icon, String label, String value, int color){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(6), dp(4), dp(6));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(224, 232, 226));
        card.setBackground(bg);
        card.setElevation(dp(3));

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(15);
        iconTv.setGravity(Gravity.CENTER);
        card.addView(iconTv, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView valTv = new TextView(this);
        valTv.setText(value);
        valTv.setTextSize(12);
        valTv.setTextColor(color);
        valTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        valTv.setGravity(Gravity.CENTER);
        valTv.setSingleLine(true);
        valTv.setEllipsize(TextUtils.TruncateAt.END);
        fitInside(valTv, 12f, 8.5f);
        card.addView(valTv, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView lblTv = new TextView(this);
        lblTv.setText(label);
        lblTv.setTextSize(9.5f);
        lblTv.setTextColor(MUTED);
        lblTv.setGravity(Gravity.CENTER);
        lblTv.setSingleLine(true);
        fitInside(lblTv, 10f, 7.5f);
        card.addView(lblTv, new LinearLayout.LayoutParams(-1, dp(15)));

        return card;
    }

    View createModernTabCard(String icon, String title, String subtitle, int accentColor, int badgeCount, View.OnClickListener onClick){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(8), dp(8), dp(8), dp(6));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(224, 232, 226));
        card.setBackground(bg);
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // Circular/Rounded Icon container
        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(18);
        iconTv.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.argb(26, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        iconBg.setCornerRadius(dp(10));
        iconTv.setBackground(iconBg);
        topRow.addView(iconTv, new LinearLayout.LayoutParams(dp(34), dp(34)));

        // Title
        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(12);
        titleTv.setTextColor(TEXT);
        titleTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleTv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        titleTv.setPadding(dp(8), 0, dp(4), 0);
        titleTv.setSingleLine(false);
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(null);
        fitInside(titleTv, 13.5f, 10f);
        topRow.addView(titleTv, new LinearLayout.LayoutParams(0, dp(34), 1));

        if(badgeCount > 0){
            TextView badge = new TextView(this);
            badge.setText(String.valueOf(badgeCount));
            badge.setTextSize(10);
            badge.setTextColor(Color.WHITE);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(accentColor);
            badgeBg.setCornerRadius(dp(9));
            badge.setBackground(badgeBg);
            topRow.addView(badge, new LinearLayout.LayoutParams(dp(22), dp(20)));
        }

        card.addView(topRow, new LinearLayout.LayoutParams(-1, dp(44)));

        // Subtitle
        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextSize(10.5f);
        subTv.setTextColor(MUTED);
        subTv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        subTv.setPadding(dp(4), dp(2), dp(4), 0);
        subTv.setSingleLine(false);
        subTv.setMaxLines(2);
        subTv.setEllipsize(null);
        fitInside(subTv, 11f, 8.5f);
        card.addView(subTv, new LinearLayout.LayoutParams(-1, dp(24)));

        card.setOnClickListener(onClick);
        return card;
    }

    int safeLowStockCount(){try{return db==null?0:db.lowStockCount();}catch(Throwable e){return 0;}}
    double safeTodaySales(){try{return db==null?0:db.todaySales();}catch(Throwable e){return 0;}}
    int safeTodayInvoiceCount(){try{return db==null?0:db.todayInvoiceCount();}catch(Throwable e){return 0;}}
    int safeCustomerCount(){try{return db==null?0:db.customerCount();}catch(Throwable e){return 0;}}
    int safeScannedInvoiceCount(){try{return db==null?0:db.scannedInvoiceCount();}catch(Throwable e){return 0;}}
    int safeTransferCount(){try{return db==null?0:db.transferCount();}catch(Throwable e){return 0;}}

    void home(){
        currentPage="الرئيسية";
        pageStack.clear();
        try{
            LinearLayout r=new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setBackgroundColor(BG);
            r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            LinearLayout h=new LinearLayout(this);
            h.setOrientation(LinearLayout.VERTICAL);
            h.setGravity(Gravity.CENTER);
            h.setPadding(dp(12),dp(8),dp(12),dp(8));
            h.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{GREEN,DARK}));
            TextView t=tv("بقالة العزي للمواد الغذائية",20);
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            t.setGravity(Gravity.CENTER);
            h.addView(t,new LinearLayout.LayoutParams(-1,dp(48)));
            TextView p=tv("نظام المبيعات والحسابات والمخزون والحوالات",15);
            p.setTextColor(Color.WHITE);
            p.setGravity(Gravity.CENTER);
            h.addView(p,new LinearLayout.LayoutParams(-1,dp(28)));
            r.addView(h,new LinearLayout.LayoutParams(-1,dp(82)));

            ScrollView sv=new ScrollView(this);
            LinearLayout grid=new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setPadding(dp(10),dp(12),dp(10),dp(20));
            grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TextView sec=tv("الأقسام الرئيسية",17);
            sec.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            sec.setTextColor(GREEN);
            grid.addView(sec,new LinearLayout.LayoutParams(-1,dp(52)));

            addSafeHomeButton(grid,"🧾 فواتير البيع",v->invoiceHistory());
            addSafeHomeButton(grid,"👥 العملاء والحسابات",v->customers());
            addSafeHomeButton(grid,"🛒 فواتير الشراء",v->purchaseInvoices());
            addSafeHomeButton(grid,"📦 المخزون والأصناف",v->inventory());
            addSafeHomeButton(grid,"📊 التقارير",v->reports());
            addSafeHomeButton(grid,"📝 الملاحظات",v->notes());
            addSafeHomeButton(grid,"💸 الحوالات",v->transfers());

            sv.addView(grid);
            r.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
            setContentView(r);
        }catch(Throwable e){
            android.util.Log.e("AlAzziHome","Home UI failed",e);
            showSafeHome(e);
        }
    }

    void showGeneralActions(){
        String[] choices={"🧾 فاتورة مبيعات جديدة","🛒 فاتورة شراء جديدة","📷 ماسح الفواتير (CamScanner)","👥 إضافة عميل","📦 إضافة صنف","📊 التقارير"};
        new AlertDialog.Builder(this).setTitle("إجراء عام").setItems(choices,(d,w)->{
            if(w==0) invoice();
            else if(w==1) newPurchaseInvoice();
            else if(w==2) scanner();
            else if(w==3) customers();
            else if(w==4) inventory();
            else reports();
        }).setNegativeButton("إغلاق",null).show();
    }

    double getCustomerPriorBalance(String cn, boolean edit, String origCustomer, double origNetImpact){
        if(cn == null || cn.trim().isEmpty()) return 0.0;
        double b = db.balanceByName(cn.trim());
        if(edit && origCustomer != null && !origCustomer.isEmpty() && cn.trim().equalsIgnoreCase(origCustomer.trim())){
            b -= origNetImpact;
        }
        if(Math.abs(b) < 0.005) b = 0.0;
        return b;
    }

    void invoice(){invoice(false,-1);}
    void invoice(boolean edit,long invoiceId){
        final String origCustomer = edit ? db.invoiceCustomer(invoiceId) : "";
        final double origTotal = edit ? db.invoiceTotal(invoiceId) : 0;
        final double origPaid = edit ? db.invoicePaid(invoiceId) : 0;
        final double origNetImpact = origTotal - origPaid;

        base(edit?"تعديل الفاتورة":"فاتورة جديدة",false);

        // شريط سفلي ثابت لملخص الفاتورة وأزرار الإجراءات السريعة
        bottom.removeAllViews();
        LinearLayout invFooter=new LinearLayout(this);
        invFooter.setOrientation(LinearLayout.VERTICAL);
        invFooter.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        invFooter.setPadding(dp(10),dp(5),dp(10),dp(6));
        GradientDrawable ifBg=new GradientDrawable();
        ifBg.setColor(CARD);
        ifBg.setStroke(dp(1),Color.rgb(215,225,218));
        invFooter.setBackground(ifBg);
        if(Build.VERSION.SDK_INT>=21) invFooter.setElevation(dp(8));

        LinearLayout fSummary=new LinearLayout(this);
        fSummary.setOrientation(LinearLayout.HORIZONTAL);
        fSummary.setGravity(Gravity.CENTER_VERTICAL);
        fSummary.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView fTotalTv=tv("الإجمالي: 0 ريال",15);
        fTotalTv.setTextColor(GREEN); fTotalTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        fSummary.addView(fTotalTv,new LinearLayout.LayoutParams(0,-2,1.2f));

        TextView fRemainTv=tv("الفاتورة فارغة",11.5f);
        fRemainTv.setTextColor(MUTED); fRemainTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        fRemainTv.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        fSummary.addView(fRemainTv,new LinearLayout.LayoutParams(0,-2,1f));

        invFooter.addView(fSummary,new LinearLayout.LayoutParams(-1,-2));
        spaceTo(invFooter,4);

        LinearLayout fButtons=new LinearLayout(this);
        fButtons.setOrientation(LinearLayout.HORIZONTAL);
        fButtons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        fButtons.setGravity(Gravity.CENTER_VERTICAL);

        Button fSave=action(edit?"💾 حفظ التعديل":"💾 حفظ الفاتورة",GREEN);
        fSave.setTextSize(13.5f);
        fButtons.addView(fSave,new LinearLayout.LayoutParams(0,dp(44),1.5f));

        Button fPrint=button("🖨️ طباعة ومعاينة");
        fPrint.setTextColor(GREEN); fPrint.setBackground(outline(Color.rgb(240,248,242),10));
        fPrint.setTextSize(12f);
        LinearLayout.LayoutParams fpp=new LinearLayout.LayoutParams(0,dp(44),1.1f); fpp.setMargins(dp(5),0,0,0);
        fButtons.addView(fPrint,fpp);

        Button fClear=button("🧹 مسح");
        fClear.setTextColor(MUTED); fClear.setBackground(outline(CARD,10));
        fClear.setTextSize(11.5f);
        LinearLayout.LayoutParams fcp=new LinearLayout.LayoutParams(0,dp(44),0.7f); fcp.setMargins(dp(5),0,0,0);
        fButtons.addView(fClear,fcp);

        invFooter.addView(fButtons,new LinearLayout.LayoutParams(-1,dp(46)));

        // أزرار طريقة البيع/السداد في الشريط السفلي الثابت
        LinearLayout payModes=new LinearLayout(this);
        payModes.setOrientation(LinearLayout.HORIZONTAL);
        payModes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        payModes.setGravity(Gravity.CENTER_VERTICAL);

        Button cashMode=button("💵 نقدي");
        Button creditMode=button("⏳ آجل");
        Button calcMode=button("🧮 حاسبة الصرف");
        cashMode.setTextSize(11.5f); creditMode.setTextSize(11.5f); calcMode.setTextSize(11.5f);
        payModes.addView(cashMode,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams cmlpFooter=new LinearLayout.LayoutParams(0,dp(50),1); cmlpFooter.setMargins(dp(4),0,0,0);
        payModes.addView(creditMode,cmlpFooter);
        LinearLayout.LayoutParams clmlpFooter=new LinearLayout.LayoutParams(0,dp(50),1.1f); clmlpFooter.setMargins(dp(4),0,0,0);
        payModes.addView(calcMode,clmlpFooter);
        invFooter.addView(payModes,new LinearLayout.LayoutParams(-1,dp(52)));

        bottom.addView(invFooter,new LinearLayout.LayoutParams(-1,dp(116)));

        section("بيانات الفاتورة");

        // صف بيانات الفاتورة: رقم الفاتورة + اسم العميل + التاريخ والوقت
        LinearLayout metaCard=new LinearLayout(this);
        metaCard.setOrientation(LinearLayout.VERTICAL);
        metaCard.setPadding(dp(8),dp(8),dp(8),dp(8));
        metaCard.setBackground(outlined(CARD,1,14));
        metaCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout metaRow=new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView no=tv(edit?db.invoiceNo(invoiceId):String.valueOf(db.nextInvoice()),14);
        no.setTextColor(GREEN); no.setTypeface(Typeface.DEFAULT,Typeface.BOLD); no.setGravity(Gravity.CENTER);
        GradientDrawable noBg=new GradientDrawable();
        noBg.setColor(Color.rgb(240,248,242));
        noBg.setCornerRadius(dp(10));
        noBg.setStroke(dp(1),Color.rgb(190,225,200));
        no.setBackground(noBg);
        no.setContentDescription("رقم الفاتورة");
        metaRow.addView(no,new LinearLayout.LayoutParams(0,dp(42),0.75f));

        AutoCompleteTextView customer=new AutoCompleteTextView(this);
        customer.setHint("اسم العميل"); customer.setTextSize(13.5f); customer.setSingleLine(true);
        customer.setTextColor(TEXT); customer.setHintTextColor(MUTED);
        customer.setPadding(dp(10),dp(4),dp(10),dp(4));
        GradientDrawable custBg=new GradientDrawable();
        custBg.setColor(Color.WHITE);
        custBg.setCornerRadius(dp(10));
        custBg.setStroke(dp(1),Color.rgb(215,225,218));
        customer.setBackground(custBg);
        customer.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        customer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); customer.setTextDirection(View.TEXT_DIRECTION_RTL);
        customer.setThreshold(1); customer.setSelectAllOnFocus(true);
        customer.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,db.customerNames()));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(42),1.35f); clp.setMargins(dp(5),0,dp(5),0);
        metaRow.addView(customer,clp);

        TextView dt=tv(db.now(),11); dt.setTextColor(MUTED); dt.setGravity(Gravity.CENTER);
        GradientDrawable dtBg=new GradientDrawable();
        dtBg.setColor(Color.rgb(248,250,248));
        dtBg.setCornerRadius(dp(10));
        dtBg.setStroke(dp(1),Color.rgb(228,235,230));
        dt.setBackground(dtBg);
        metaRow.addView(dt,new LinearLayout.LayoutParams(0,dp(42),1.1f));

        metaCard.addView(metaRow,new LinearLayout.LayoutParams(-1,dp(44)));

        // Dynamic Smart Hint Banner for Customer
        TextView custHint=tv("💡 اختر أو اكتب اسم العميل لعرض رصيده وحالته المالية فورياً",10f);
        custHint.setTextColor(MUTED); custHint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        custHint.setPadding(dp(4),dp(2),dp(4),dp(2));
        metaCard.addView(custHint,new LinearLayout.LayoutParams(-1,-2));

        content.addView(metaCard,new LinearLayout.LayoutParams(-1,-2));
        space(6);

        Runnable updateCustHint=()->{
            String cn=customer.getText().toString().trim();
            if(cn.isEmpty()){
                custHint.setText("💡 اختر أو اكتب اسم العميل لعرض رصيده وحالته المالية فورياً");
                custHint.setTextColor(MUTED);
                return;
            }
            long cid=db.customerIdByName(cn);
            if(cid>0){
                double bal=db.balance(cid);
                String ph=db.phoneByName(cn);
                String bStr=bal>0?("عليه "+fmt(bal)+" ر.ي"):(bal<0?("له "+fmt(Math.abs(bal))+" ر.ي"):"مسدد 0 ر.ي");
                custHint.setText("💡 العميل: "+cn+" • الرصيد الحالي: "+bStr+(ph.isEmpty()?"":(" • الهاتف: "+ph)));
                custHint.setTextColor(bal>0?RED:(bal<0?BLUE:GREEN));
            }else{
                custHint.setText("💡 عميل جديد: سيتم تعلمه وحفظه أوتوماتيكياً في قاعدة البيانات");
                custHint.setTextColor(BLUE);
            }
        };

        customer.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){updateCustHint.run();}
            public void afterTextChanged(Editable s){}
        });
        customer.setOnItemClickListener((parent,view,pos,id)->updateCustHint.run());

        if(edit){
            customer.setText(db.invoiceCustomer(invoiceId));
            updateCustHint.run();
        }

        // قسم إدخال الصنف: الحفاظ التام على ترتيب مربعات الإدخال (الإجمالي، الكمية، اسم الصنف)
        section("إدخال الصنف والتلميحات الذكية");
        LinearLayout entry=card();
        entry.setPadding(dp(8),dp(8),dp(8),dp(8));

        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        EditText total=numberField("الإجمالي");
        EditText qty=numberField("الكمية");
        AutoCompleteTextView item=new AutoCompleteTextView(this);
        item.setHint("اسم الصنف / التفاصيل"); item.setTextSize(13); item.setSingleLine(true); item.setTextColor(TEXT); item.setHintTextColor(MUTED);
        item.setPadding(dp(8),dp(4),dp(8),dp(4)); item.setBackground(outlined(CARD,1,10)); item.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); item.setTextDirection(View.TEXT_DIRECTION_RTL); item.setSelectAllOnFocus(true);
        item.setOnClickListener(v->item.selectAll());
        item.setOnFocusChangeListener((v,has)->{if(has)item.postDelayed(()->item.selectAll(),60);});
        ArrayList<String> itemSuggestions=new ArrayList<>(Arrays.asList("السمن"));
        Cursor itemCursor=db.items(); while(itemCursor.moveToNext()) itemSuggestions.add(itemCursor.getString(1)); itemCursor.close();
        item.setThreshold(1); item.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,itemSuggestions));

        total.setInputType(2|8192); qty.setInputType(2|8192); qty.setText("1");

        TextView itemHint=tv("💡 اكتب اسم الصنف وسيتم جلب السعر والمخزون وحسابه فورياً",10f);
        itemHint.setTextColor(MUTED); itemHint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);

        Runnable updateItemHint=()->{
            String iname=item.getText().toString().trim();
            if(iname.isEmpty()){
                itemHint.setText("💡 اكتب اسم الصنف وسيتم جلب السعر والمخزون وحسابه فورياً");
                itemHint.setTextColor(MUTED);
                return;
            }
            double saleP=db.itemSalePrice(iname);
            double costP=db.itemCostPrice(iname);
            double st=db.itemQty(iname);
            double reqQ=1;
            try{reqQ=Double.parseDouble(qty.getText().toString().trim());}catch(Exception ignored){}
            if(reqQ<=0) reqQ=1;

            if(saleP>0){
                double totalP=saleP*reqQ;
                if(total.getText().toString().trim().isEmpty() || total.getText().toString().trim().equals("0")){
                    total.setText(fmt(totalP));
                }
            }

            if(st>0 && reqQ>st){
                itemHint.setText("⚠️ تنبيه: الكمية المطلوبة ("+fmt(reqQ)+") تتجاوز المتوفر بالمخزون ("+fmt(st)+" حبة)!");
                itemHint.setTextColor(RED);
            }else if(saleP>0 || st>0){
                itemHint.setText("💡 الصنف: "+iname+" • سعر البيع: "+fmt(saleP)+" ر.ي • سعر التكلفة: "+fmt(costP)+" ر.ي • بالمخزون: "+fmt(st)+" حبة");
                itemHint.setTextColor(GREEN);
            }else{
                itemHint.setText("💡 صنف جديد: سيتم حفظه وسعره أوتوماتيكياً في قائمة الأصناف والتسعيرات");
                itemHint.setTextColor(BLUE);
            }
        };

        item.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){updateItemHint.run();}
            public void afterTextChanged(Editable s){}
        });
        qty.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){updateItemHint.run();}
            public void afterTextChanged(Editable s){}
        });
        item.setOnItemClickListener((parent,view,pos,id)->{
            String selectedName=(String)parent.getItemAtPosition(pos);
            double saleP=db.itemSalePrice(selectedName);
            if(saleP>0){
                double q=1;
                try{q=Double.parseDouble(qty.getText().toString().trim());}catch(Exception ignored){}
                if(q<=0)q=1;
                total.setText(fmt(saleP*q));
            }
            updateItemHint.run();
        });

        // الترتيب: الإجمالي -> الكمية -> اسم الصنف
        line.addView(total,new LinearLayout.LayoutParams(0,dp(52),1.0f));
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(0,dp(52),0.72f); qlp.setMargins(dp(4),0,dp(4),0);
        line.addView(qty,qlp);
        line.addView(item,new LinearLayout.LayoutParams(0,dp(52),1.35f));
        entry.addView(line,new LinearLayout.LayoutParams(-1,dp(42)));
        spaceTo(entry,4);

        entry.addView(itemHint,new LinearLayout.LayoutParams(-1,-2));
        spaceTo(entry,6);

        Button add=action("＋  إضافة الصنف إلى الفاتورة",GREEN);
        add.setTextSize(13.5f);
        entry.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));
        content.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        space(6);

        // صندوق عرض الفاتورة (الحفاظ على الهيكل وتنسيق العرض)
        section("صندوق عرض الفاتورة");
        LinearLayout invoiceBox=card();
        invoiceBox.setPadding(dp(8),dp(8),dp(8),dp(8));

        LinearLayout table=new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setBackground(outlined(Color.rgb(240,248,242),1,8));
        String[] heads={"الإجمالي","الكمية","اسم الصنف","سعر الوحدة","حذف"};
        float[] weights={1.0f,.72f,1.35f,.9f,.55f};
        for(int i=0;i<heads.length;i++){
            TextView hv=tv(heads[i],9.5f);
            hv.setTextColor(GREEN); hv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            hv.setGravity(Gravity.CENTER); hv.setSingleLine(true);
            hv.setBackgroundColor(Color.TRANSPARENT);
            head.addView(hv,new LinearLayout.LayoutParams(0,dp(30),weights[i]));
        }
        table.addView(head,new LinearLayout.LayoutParams(-1,dp(32)));
        spaceTo(table,4);

        LinearLayout rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        table.addView(rows,new LinearLayout.LayoutParams(-1,-2));
        invoiceBox.addView(table,new LinearLayout.LayoutParams(-1,-2));
        spaceTo(invoiceBox,6);

        // شريط الإجمالي العام
        TextView boxTotal=tv("الإجمالي: 0 ريال",18);
        boxTotal.setTextColor(GREEN); boxTotal.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        boxTotal.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        boxTotal.setPadding(dp(12),dp(6),dp(12),dp(6));
        GradientDrawable btBg=new GradientDrawable();
        btBg.setColor(Color.rgb(255,249,230));
        btBg.setCornerRadius(dp(12));
        btBg.setStroke(dp(1),Color.rgb(245,225,175));
        boxTotal.setBackground(btBg);
        invoiceBox.addView(boxTotal,new LinearLayout.LayoutParams(-1,dp(46)));
        spaceTo(invoiceBox,6);

        // المبلغ المدفوع
        LinearLayout paidRow=new LinearLayout(this);
        paidRow.setOrientation(LinearLayout.HORIZONTAL);
        paidRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        paidRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView paidTitle=tv("المبلغ المدفوع (ريال)",12.5f);
        paidTitle.setTextColor(TEXT); paidTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        EditText paid=numberField("0");
        paid.setText(edit?fmt(origPaid):"0"); paid.setTextSize(13.5f); paid.setSelectAllOnFocus(true);
        paidRow.addView(paidTitle,new LinearLayout.LayoutParams(0,dp(50),1));
        paidRow.addView(paid,new LinearLayout.LayoutParams(dp(130),dp(38)));
        invoiceBox.addView(paidRow,new LinearLayout.LayoutParams(-1,dp(52)));
        spaceTo(invoiceBox,4);

        // أزرار طريقة السداد موجودة في الشريط السفلي الثابت.
        if(edit){
            if(origPaid>=origTotal&&origTotal>0){
                cashMode.setTextColor(Color.WHITE); cashMode.setBackground(rounded(GREEN,dp(10)));
                creditMode.setTextColor(TEXT); creditMode.setBackground(outline(CARD,10));
            }else{
                creditMode.setTextColor(Color.WHITE); creditMode.setBackground(rounded(RED,dp(10)));
                cashMode.setTextColor(TEXT); cashMode.setBackground(outline(CARD,10));
            }
        }else{
            cashMode.setTextColor(Color.WHITE); cashMode.setBackground(rounded(GREEN,dp(10)));
            creditMode.setTextColor(TEXT); creditMode.setBackground(outline(CARD,10));
        }
        calcMode.setTextColor(Color.rgb(24,105,200)); calcMode.setBackground(outline(Color.rgb(240,248,255),10));
        // تم نقل أزرار نقدي وآجل والحاسبة إلى الشريط السفلي الثابت.
        spaceTo(invoiceBox,4);

        TextView remainingLabel=tv("المتبقي: 0 ريال",12.5f);
        remainingLabel.setTextColor(RED); remainingLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        remainingLabel.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        remainingLabel.setPadding(dp(10),0,dp(10),0);
        invoiceBox.addView(remainingLabel,new LinearLayout.LayoutParams(-1,dp(28)));

        content.addView(invoiceBox,new LinearLayout.LayoutParams(-1,-2));
        space(5);
        final ArrayList<Line> lines=new ArrayList<>();
        if(edit){Cursor c=db.invoiceLines(invoiceId);while(c.moveToNext())lines.add(new Line(c.getString(1),c.getDouble(2),c.getDouble(3)));c.close();}

        TextView customerBalance=tv("رصيد العميل: 0 ريال",11.5f);
        customerBalance.setTextColor(GREEN); customerBalance.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        customerBalance.setPadding(dp(10),dp(4),dp(10),dp(4));
        customerBalance.setBackground(outline(Color.rgb(241,247,242),10));
        content.addView(customerBalance,new LinearLayout.LayoutParams(-1,dp(32)));
        addSpace(3);

        TextView paymentMode=tv("نوع السداد: نقدي — ويمكن ترك الباقي آجلًا",10.5f);
        paymentMode.setTextColor(MUTED); paymentMode.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        content.addView(paymentMode,new LinearLayout.LayoutParams(-1,dp(24)));
        addSpace(3);

        cashMode.setOnClickListener(v->{
            paid.setText(fmt(totalOf(lines)));
            cashMode.setTextColor(Color.WHITE); cashMode.setBackground(rounded(GREEN,dp(10)));
            creditMode.setTextColor(TEXT); creditMode.setBackground(outline(CARD,10));
        });
        creditMode.setOnClickListener(v->{
            paid.setText("0");
            creditMode.setTextColor(Color.WHITE); creditMode.setBackground(rounded(RED,dp(10)));
            cashMode.setTextColor(TEXT); cashMode.setBackground(outline(CARD,10));
        });
        calcMode.setOnClickListener(v->showQuickCalculator(totalOf(lines)));

        Runnable updateCustomerBalance=()->{
            String cn=customer.getText().toString().trim();
            double cb=getCustomerPriorBalance(cn,edit,origCustomer,origNetImpact);
            customerBalance.setText((edit?"رصيد العميل السابق (قبل هذه الفاتورة): ":"رصيد العميل السابق: ")+balanceText(cb));
            double paidPreview=0;try{paidPreview=Double.parseDouble(paid.getText().toString().trim());}catch(Exception ignored){}
            double invPreview=0;for(Line lx:lines)invPreview+=lx.total;
            double net=cb+invPreview-paidPreview;
            if(Math.abs(net)<0.005) net=0;
            paymentMode.setText("نوع السداد: "+(paidPreview>=invPreview&&invPreview>0?"نقدي":"آجل")+" • بعد الفاتورة: "+balanceText(net));
        };
        customer.setOnItemClickListener((p,v,pos,id)->updateCustomerBalance.run());
        customer.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){updateCustomerBalance.run();}public void afterTextChanged(android.text.Editable e){}});

        final Runnable[] redraw=new Runnable[1];
        redraw[0]=()->{
            rows.removeAllViews();
            String cn=customer.getText().toString().trim();
            double priorBal=getCustomerPriorBalance(cn,edit,origCustomer,origNetImpact);
            double run=0;
            for(Line l:lines){run+=l.total;addRow(rows,l,run,priorBal,lines);}
            boxTotal.setText("الإجمالي: "+fmt(run)+" ريال");
            double paidNow=0; try{paidNow=Double.parseDouble(paid.getText().toString().trim());}catch(Exception ignored){}
            if(paidNow<0)paidNow=0;
            double remaining=priorBal+run-paidNow;if(Math.abs(remaining)<0.005)remaining=0;
            remainingLabel.setText("المتبقي: "+fmt(remaining)+" ريال");
            remainingLabel.setTextColor(remaining>0.005?RED:GREEN);
            updateCustomerBalance.run();

            // تحديث الشريط السفلي الثابت في الوقت الفعلي
            fTotalTv.setText("الإجمالي: "+fmt(run)+" ريال");
            if(run<=0){
                fRemainTv.setText("الفاتورة فارغة");
                fRemainTv.setTextColor(MUTED);
            }else if(paidNow>=run){
                fRemainTv.setText("✓ نقدي مسدد");
                fRemainTv.setTextColor(GREEN);
            }else{
                fRemainTv.setText("متبقي: "+fmt(run-paidNow)+" ريال");
                fRemainTv.setTextColor(RED);
            }
        };

        add.setOnClickListener(v->{
            try{
                double t=Double.parseDouble(total.getText().toString().trim());
                double q=Double.parseDouble(qty.getText().toString().trim());
                String n=item.getText().toString().trim();
                if(n.isEmpty()||q<=0||t<0)throw new Exception();
                lines.add(new Line(n,q,t));
                redraw[0].run();
                total.setText("");qty.setText("1");item.setText("");total.requestFocus();
            }catch(Exception e){
                Toast.makeText(this,"أدخل الإجمالي والكمية واسم الصنف بشكل صحيح",Toast.LENGTH_SHORT).show();
            }
        });

        fSave.setOnClickListener(v->{
            if(lines.isEmpty()){Toast.makeText(this,"أضف صنفاً واحداً على الأقل",Toast.LENGTH_SHORT).show();return;}
            String cn=customer.getText().toString().trim();
            // السماح بالفاتورة النقدية بدون إنشاء حساب عميل.
            if(cn.isEmpty() || "نقدي".equals(cn) || "عميل نقدي".equals(cn)){
                saveInvoice("نقدي",no.getText().toString(),lines,totalOf(lines),parsePaid(paid),"",edit,invoiceId);
                return;
            }
            String knownPhone=db.phoneByName(cn).trim();
            if(!knownPhone.isEmpty()) saveInvoice(cn,no.getText().toString(),lines,totalOf(lines),parsePaid(paid),knownPhone,edit,invoiceId);
            else showPhoneDialog(cn,no.getText().toString(),lines,totalOf(lines),parsePaid(paid),edit,invoiceId);
        });
        fPrint.setOnClickListener(v->preview(no.getText().toString(),customer.getText().toString(),lines,totalOf(lines),edit,invoiceId));
        fClear.setOnClickListener(v->{
            if(!lines.isEmpty()){
                new AlertDialog.Builder(this)
                    .setTitle("مسح الأصناف")
                    .setMessage("هل تريد مسح جميع أصناف الفاتورة؟")
                    .setPositiveButton("مسح",(d,w)->{lines.clear();redraw[0].run();})
                    .setNegativeButton("إلغاء",null).show();
            }
        });
        content.setPadding(dp(6),dp(4),dp(6),dp(22));

        item.setOnEditorActionListener((v,a,e)->{add.performClick();return true;});
        customer.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){redraw[0].run();}public void afterTextChanged(android.text.Editable e){}});
        paid.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){redraw[0].run();}public void afterTextChanged(android.text.Editable e){}});
        redraw[0].run();
    }

    double totalOf(ArrayList<Line> ls){double x=0;for(Line l:ls)x+=l.total;return x;}
    double parsePaid(EditText e){try{return Math.max(0,Double.parseDouble(e.getText().toString().trim()));}catch(Exception ex){return 0;}}
    void showPhoneDialog(String name,String no,ArrayList<Line> lines,double total,double paid,boolean edit,long oldId){
        EditText phone=phoneField("رقم هاتف العميل");
        phone.setText(db.phoneByName(name));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(4),dp(8),dp(4));
        box.addView(tv("رقم العميل غير مسجل. أضف رقم الهاتف حتى يمكن مشاركة الفاتورة معه عبر واتساب. لا يظهر 967 داخل خانة العميل.",12));
        box.addView(phone,new LinearLayout.LayoutParams(-1,dp(52)));
        new AlertDialog.Builder(this).setTitle("إضافة رقم العميل").setView(box)
            .setPositiveButton("حفظ الفاتورة",(d,w)->{
                String p=phone.getText().toString().trim();
                if(p.isEmpty()){Toast.makeText(this,"أدخل رقم العميل حتى يتم حفظه ومشاركة الفاتورة معه.",Toast.LENGTH_SHORT).show();return;}
                saveInvoice(name,no,lines,total,paid,p,edit,oldId);
            }).setNegativeButton("إلغاء",null).show();
    }
    void saveInvoice(String name,String no,ArrayList<Line> lines,double total,double paid,String phone,boolean edit,long oldId){
        String customerName=(name==null?"":name.trim());
        boolean cashCustomer=customerName.isEmpty() || "نقدي".equals(customerName) || "عميل نقدي".equals(customerName);
        if(paid<0 || total<0){Toast.makeText(this,"بيانات الفاتورة غير صحيحة.",Toast.LENGTH_SHORT).show();return;}
        if(lines==null||lines.isEmpty()){Toast.makeText(this,"أضف صنفاً واحداً على الأقل.",Toast.LENGTH_SHORT).show();return;}
        String stockWarning=db.saleStockWarning(lines,edit?oldId:-1);
        String storedCustomer=cashCustomer?"نقدي":customerName;
        long cid=cashCustomer?-1:db.customer(storedCustomer,phone==null?"":phone);
        String date=db.now();
        SQLiteDatabase txDb=db.getWritableDatabase();
        txDb.beginTransaction();
        try{
            if(edit && oldId>0) db.revertStockFromInvoice(oldId);
            if(edit){
                String oldNo=db.invoiceNo(oldId);
                db.deleteInvoiceTransactions(oldNo);
                db.updateInvoice(oldId,no,storedCustomer,total,paid,date);
                db.replaceInvoiceLines(oldId,lines);
                if(!db.applyStockFromSale(lines,oldId)) throw new Exception("stock");
            }else{
                long id=db.addInvoice(no,storedCustomer,total,paid,date);
                if(id<=0) throw new Exception("invoice");
                db.replaceInvoiceLines(id,lines);
                if(!db.applyStockFromSale(lines,id)) throw new Exception("stock");
            }
            if(!cashCustomer){
                if(total>0) db.addTransactionOnce(cid,total,"فاتورة مبيعات رقم "+no,date);
                if(paid>0) db.addPaymentTransaction(cid,paid,"دفعة فاتورة رقم "+no,date);
            }
            txDb.setTransactionSuccessful();
            cacheLastInvoice(no,storedCustomer,lines,total,date);
            clearInvoiceDraft();
            saveReceiptImage(no,storedCustomer,lines,total);
            showPostSaveActions(no,storedCustomer,lines,total,cid,paid,stockWarning);
        }catch(Exception ex){
            Toast.makeText(this,"تعذر حفظ الفاتورة بالكامل. لم يتم اعتماد العملية.",Toast.LENGTH_LONG).show();
        }finally{
            txDb.endTransaction();
        }
    }
    
    void cacheLastInvoice(String no,String customer,ArrayList<Line> lines,double total,String date){
        try{
            File dir=new File(getCacheDir(),"invoices"); if(!dir.exists())dir.mkdirs();
            File file=new File(dir,"last_invoice.txt");
            StringBuilder x=new StringBuilder();
            x.append("رقم الفاتورة: ").append(no).append("\nالعميل: ").append(customer).append("\nالتاريخ: ").append(date).append("\n");
            for(Line l:lines)x.append(l.name).append(" | ").append(fmt(l.qty)).append(" | ").append(fmt(l.total)).append("\n");
            x.append("الإجمالي: ").append(fmt(total)).append(" ريال");
            FileOutputStream out=new FileOutputStream(file,false);out.write(x.toString().getBytes("UTF-8"));out.close();
        }catch(Exception ignored){}
    }
    String b64(String s){return android.util.Base64.encodeToString((s==null?"":s).getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);}
    String unb64(String s){try{return new String(android.util.Base64.decode(s,android.util.Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    void saveInvoiceDraft(String no,String customer,String paid,ArrayList<Line> lines){
        try{
            StringBuilder s=new StringBuilder();
            s.append(b64(no)).append("\n").append(b64(customer)).append("\n").append(b64(paid)).append("\n");
            for(Line l:lines)s.append(b64(l.name)).append("\t").append(l.qty).append("\t").append(l.total).append("\n");
            getSharedPreferences("draft",MODE_PRIVATE).edit().putString("invoice",s.toString()).apply();
            Toast.makeText(this,"تم الحفظ المؤقت ويمكن استعادته لاحقًا",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"تعذر الحفظ المؤقت",Toast.LENGTH_SHORT).show();}
    }
    void restoreInvoiceDraft(TextView no,AutoCompleteTextView customer,EditText paid,ArrayList<Line> lines,Runnable refresh){
        try{
            String s=getSharedPreferences("draft",MODE_PRIVATE).getString("invoice","");
            if(s.isEmpty()){Toast.makeText(this,"لا يوجد حفظ مؤقت",Toast.LENGTH_SHORT).show();return;}
            String[] a=s.split("\n",-1);
            if(a.length<3)throw new Exception();
            no.setText(unb64(a[0]));customer.setText(unb64(a[1]));paid.setText(unb64(a[2]));
            lines.clear();
            for(int i=3;i<a.length;i++){
                if(a[i].trim().isEmpty())continue;
                String[] p=a[i].split("\t",-1);
                if(p.length>=3)lines.add(new Line(unb64(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2])));
            }
            refresh.run();Toast.makeText(this,"تم استعادة الحفظ المؤقت",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"الحفظ المؤقت غير صالح",Toast.LENGTH_SHORT).show();}
    }
    void clearInvoiceDraft(){getSharedPreferences("draft",MODE_PRIVATE).edit().remove("invoice").apply();}
    File appDownloadDir(){
        return AppStorage.getAppImagesDir();
    }
    Uri saveReceiptImage(String no,String customer,ArrayList<Line> lines,double total){
        try{
            Bitmap b=receiptBitmap(receiptTextFromLines(no,customer,lines,total,-1));
            String fn="فاتورة_"+no+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".png";
            return AppStorage.saveAppImage(this, b, fn);
        }catch(Exception e){Toast.makeText(this,"تعذر حفظ صورة الفاتورة",Toast.LENGTH_SHORT).show();return null;}
    }
    void saveAccountStatementImage(long id,String name){
        try{
            String s=statement(id,name);Bitmap b=receiptBitmap(s);
            String fn="كشف_"+name.replaceAll("[\\/:*?\"<>|]","_")+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".png";
            AppStorage.saveAppImage(this, b, fn);
            Toast.makeText(this,"تم حفظ صورة كشف الحساب في:\nDownload/بقالة العزي للمواد الغذائية خاص/الصور التي ينتجها التطبيق",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"تعذر حفظ صورة كشف الحساب",Toast.LENGTH_SHORT).show();}
    }
    static void restoreDatabaseFromUri(Context c,Uri uri){
        DB helper=new DB(c);helper.close();
        File target=c.getDatabasePath("enezi.db");File tmp=new File(c.getCacheDir(),"restore_enezi.db");
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){
                if(in==null)throw new Exception("null");byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
            }
            if(target.exists())target.delete();File wal=new File(target.getPath()+"-wal"),shm=new File(target.getPath()+"-shm");if(wal.exists())wal.delete();if(shm.exists())shm.delete();
            if(!tmp.renameTo(target)){try(InputStream in=new java.io.FileInputStream(tmp);OutputStream out=new FileOutputStream(target)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}}
            tmp.delete();new DB(c).close();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    void showBackupRestore(){
        new AlertDialog.Builder(this).setTitle("النسخ الاحتياطي والاسترجاع")
            .setMessage("النسخة التلقائية: كل يوم الساعة 11:59 مساءً.\n\nالمسار: Download/بقالة العزي للمواد الغذائية خاص/النسخ الاحتياطية\n\nيمكنك إنشاء نسخة احتياطية يدوياً الآن في أي وقت.")
            .setPositiveButton("💾 إنشاء نسخة الآن",(d,w)->{ BackupReceiver.backup(this); Toast.makeText(this,"تم حفظ النسخة في:\nDownload/بقالة العزي للمواد الغذائية خاص/النسخ الاحتياطية",Toast.LENGTH_LONG).show(); })
            .setNeutralButton("استرجاع نسخة",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,8801);})
            .setNegativeButton("إغلاق",null).show();
    }

    void notifyNewOperation(String title,String text){
        try{
            if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7201); return;
            }
            String channelId="operations";
            android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(android.os.Build.VERSION.SDK_INT>=26){
                android.app.NotificationChannel ch=new android.app.NotificationChannel(channelId,"إشعارات العمليات",android.app.NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("إشعار عند إضافة فاتورة أو عملية جديدة");nm.createNotificationChannel(ch);
            }
            android.app.Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(this,channelId):new android.app.Notification.Builder(this);
            b.setSmallIcon(com.saleh.enezi.R.drawable.ic_store).setContentTitle("بقالة العزي للمواد الغذائية").setContentText(title+" — "+text).setStyle(new android.app.Notification.BigTextStyle().bigText("بقالة العزي للمواد الغذائية — مستقبل تجارتك يبدأ من هنا\n"+title+" — "+text)).setAutoCancel(true);
            nm.notify((int)(System.currentTimeMillis()%100000),b.build());
        }catch(Exception ignored){}
    }

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    GradientDrawable bg(int color,float radius){return rounded(color,dp((int)radius));}
    GradientDrawable outline(int color,float radius){return outlined(color,1,dp((int)radius));}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(10),dp(7),dp(10),dp(7));c.setBackground(outline(CARD,12));c.setElevation(dp(1));c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return c;}
    void addCard(View v,int h){content.addView(v,new LinearLayout.LayoutParams(-1,dp(Math.max(50,h-18))));space(4);}
    void add(View v,int h){content.addView(v,new LinearLayout.LayoutParams(-1,dp(Math.max(42,h-12))));space(4);}
    void space(int h){addSpace(dp(h));}
    void spaceInside(LinearLayout p,int h){Space x=new Space(this);p.addView(x,new LinearLayout.LayoutParams(1,dp(h)));}
    Button action(String text,int color){Button b=button(text);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setBackground(rounded(color,dp(12)));b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    Button btn(String text){Button b=button(text);b.setTextColor(TEXT);b.setBackground(outline(CARD,14));return b;}
    static class Line{String name;double qty,total;Line(String n,double q,double t){name=n;qty=q;total=t;}}
    void addRow(LinearLayout parent,Line l,double running,double baseBal,ArrayList<Line> all){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(2),dp(2),dp(2),dp(2));
        float[] w={1.0f,.72f,1.35f,.9f,.55f};
        TextView total=tv(fmt(l.total),13);total.setGravity(Gravity.CENTER);total.setSingleLine(true);
        TextView qty=tv(fmt(l.qty),13);qty.setGravity(Gravity.CENTER);qty.setSingleLine(true);
        TextView item=tv(l.name,12);item.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);item.setMaxLines(4);item.setEllipsize(null);
        TextView unit=tv(l.qty==0?"0":fmt(l.total/l.qty),12);unit.setTextColor(MUTED);unit.setGravity(Gravity.CENTER);unit.setSingleLine(true);
        Button del=button("حذف");del.setTextSize(10);del.setTextColor(Color.RED);del.setBackgroundColor(Color.TRANSPARENT);
        total.setBackground(outline(Color.rgb(248,250,248),6));qty.setBackground(outline(Color.rgb(248,250,248),6));item.setBackground(outline(Color.rgb(248,250,248),6));
        total.setContentDescription("تعديل إجمالي الصنف");qty.setContentDescription("تعديل كمية الصنف");item.setContentDescription("تعديل اسم الصنف أو التفاصيل");
        View[] cells={total,qty,item,unit,del};for(int i=0;i<cells.length;i++)r.addView(cells[i],new LinearLayout.LayoutParams(0,dp(48),w[i]));
        total.setOnClickListener(v->editLineTotal(l,parent,all,baseBal));
        qty.setOnClickListener(v->editLineQuantity(l,parent,all,baseBal));
        item.setOnClickListener(v->editLineName(l,parent,all,baseBal));
        del.setOnClickListener(v->{all.remove(l);redrawInvoiceRows(parent,all,baseBal);});
        parent.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    void editLineTotal(Line line,LinearLayout parent,ArrayList<Line> all,double baseBal){
        EditText e=numberField("الإجمالي");e.setText(fmt(line.total));e.setSelectAllOnFocus(true);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(8),dp(4),dp(8),dp(2));box.addView(e,new LinearLayout.LayoutParams(-1,dp(42)));
        new AlertDialog.Builder(this).setTitle("تعديل إجمالي الصنف").setMessage(line.name+" — الإجمالي الحالي: "+fmt(line.total)+" ريال").setView(box)
            .setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{try{double value=Double.parseDouble(e.getText().toString().trim());if(value<0)throw new Exception();line.total=value;redrawInvoiceRows(parent,all,baseBal);}catch(Exception ex){Toast.makeText(this,"أدخل إجماليًا صحيحًا",Toast.LENGTH_SHORT).show();}}).show();
    }
    void editLineName(Line line,LinearLayout parent,ArrayList<Line> all,double baseBal){
        EditText e=field("اسم الصنف / التفاصيل");e.setText(line.name);e.setSelectAllOnFocus(true);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(8),dp(4),dp(8),dp(2));box.addView(e,new LinearLayout.LayoutParams(-1,dp(42)));
        new AlertDialog.Builder(this).setTitle("تعديل اسم الصنف / التفاصيل").setView(box)
            .setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{String value=e.getText().toString().trim();if(value.isEmpty()){Toast.makeText(this,"اسم الصنف لا يمكن أن يكون فارغًا",Toast.LENGTH_SHORT).show();return;}line.name=value;redrawInvoiceRows(parent,all,baseBal);}).show();
    }
    void editLineQuantity(Line line,LinearLayout parent,ArrayList<Line> all,double baseBal){
        EditText q=numberField("الكمية");q.setText(fmt(line.qty));q.setSelectAllOnFocus(true);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(8),dp(4),dp(8),dp(2));box.addView(q,new LinearLayout.LayoutParams(-1,dp(42)));
        new AlertDialog.Builder(this).setTitle("تعديل كمية الصنف").setMessage(line.name+" — الكمية الحالية: "+fmt(line.qty)).setView(box)
            .setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{try{double value=Double.parseDouble(q.getText().toString().trim());if(value<=0)throw new Exception();line.qty=value;redrawInvoiceRows(parent,all,baseBal);}catch(Exception e){Toast.makeText(this,"أدخل كمية صحيحة",Toast.LENGTH_SHORT).show();}}).show();
    }
    void redrawInvoiceRows(LinearLayout parent,ArrayList<Line> all,double baseBal){parent.removeAllViews();double run=0;for(Line x:all){run+=x.total;addRow(parent,x,run,baseBal,all);}}
    void spaceTo(LinearLayout p,int h){Space x=new Space(this);p.addView(x,new LinearLayout.LayoutParams(1,dp(h)));}
    void preview(String no,String customer,ArrayList<Line> lines,double total,boolean edit,long oldId){
        String cleanCustomer=customer==null?"":customer.trim();
        long cid=cleanCustomer.isEmpty()?-1:db.customerIdByName(cleanCustomer);
        double balanceAfter=0;
        if(cid>0){
            double prior=db.balance(cid);
            if(edit){
                String oldCustomer=db.invoiceCustomer(oldId);
                double oldTotal=db.invoiceTotal(oldId);
                double oldPaid=db.invoicePaid(oldId);
                double oldImpact=oldTotal-oldPaid;
                if(oldCustomer.equalsIgnoreCase(cleanCustomer)) prior-=oldImpact;
            }
            balanceAfter=prior+total;
            if(Math.abs(balanceAfter)<0.005) balanceAfter=0;
        }
        String s=receiptTextFromLines(no,cleanCustomer,lines,total,cid,balanceAfter);
        TextView v=tv(s,11);v.setTypeface(Typeface.MONOSPACE);v.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this).setTitle("معاينة إيصال 58mm").setView(v)
            .setPositiveButton("مشاركة واتساب",(d,w)->shareReceiptImageAndText(no,cleanCustomer,lines,total))
            .setNeutralButton("طباعة",(d,w)->printInvoiceBluetooth(no,cleanCustomer,lines,total))
            .setNegativeButton("إغلاق",null).show();
    }

    String receiptTextFromLines(String no,String customer,ArrayList<Line> lines,double total,long cid){
        return receiptTextFromLines(no,customer,lines,total,cid,cid>0?db.balance(cid):0);
    }
    String receiptTextFromLines(String no,String customer,ArrayList<Line> lines,double total,long cid,double balanceAfter){
        StringBuilder s=new StringBuilder();
        s.append("بقالة العزي للمواد الغذائية\nفاتورة ").append(no).append("\n");
        if(customer!=null&&!customer.trim().isEmpty())s.append("العميل: ").append(customer.trim()).append("\n");
        s.append("التاريخ: ").append(db.now()).append("\n\n");
        for(Line l:lines){
            String n=l.name==null?"":l.name.trim();
            s.append(n).append(" × ").append(fmt(l.qty)).append(" = ").append(fmt(l.total)).append(" ريال\n");
        }
        s.append("\nالإجمالي: ").append(fmt(total)).append(" ريال\n");
        if(cid>0&&Math.abs(balanceAfter)>=0.005)s.append(balanceAfter>0?"عليه: ":"له: ").append(fmt(Math.abs(balanceAfter))).append(" ريال\n");
        s.append("شكراً لتعاملكم");
        return s.toString();
    }

    void showPostSaveOperation(String title,String message,Runnable shareAction,Runnable hideAction){
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(12),dp(16),dp(10));box.setBackground(rounded(CARD,dp(18)));
        TextView t=tv(title,17);t.setTextColor(GREEN);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER);box.addView(t,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView m=tv(message,12);m.setTextColor(TEXT);m.setGravity(Gravity.CENTER);m.setMaxLines(5);fitInside(m,12f,9f);box.addView(m,new LinearLayout.LayoutParams(-1,dp(78)));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button share=button("📤 مشاركة");share.setTextColor(Color.WHITE);share.setBackgroundColor(GREEN);Button hide=button("إخفاء");hide.setTextColor(MUTED);
        actions.addView(share,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(hide,new LinearLayout.LayoutParams(0,dp(52),1));box.addView(actions);
        share.setOnClickListener(v->{dialog.dismiss();shareAction.run();});hide.setOnClickListener(v->{dialog.dismiss();hideAction.run();});
        dialog.setContentView(box);dialog.setCanceledOnTouchOutside(false);dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(dp(330),WindowManager.LayoutParams.WRAP_CONTENT);dialog.getWindow().setGravity(Gravity.CENTER);}
    }

    void showPostSaveActions(String no,String customer,ArrayList<Line> lines,double total,long cid,double paid,String stockWarning){
        String status=paid>=total?"مسددة":(paid>0?"متبقي "+fmt(total-paid)+" ريال":"غير مسددة");
        int statusColor=paid>=total?BLUE:RED;
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(12),dp(16),dp(10));box.setBackground(rounded(CARD,dp(18)));
        TextView title=tv("تم حفظ الفاتورة بنجاح",17);title.setTextColor(GREEN);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setGravity(Gravity.CENTER);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView sub=tv("الفاتورة: #"+no+"\nالإجمالي: "+fmt(total)+" ريال"+(paid>0?" | المدفوع: "+fmt(paid)+" ريال":""),12);sub.setTextColor(TEXT);sub.setGravity(Gravity.CENTER);
        box.addView(sub,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView statusV=tv(status,14);statusV.setTextColor(statusColor);statusV.setTypeface(Typeface.DEFAULT,Typeface.BOLD);statusV.setGravity(Gravity.CENTER);
        box.addView(statusV,new LinearLayout.LayoutParams(-1,dp(28)));
        double currentBalance=db.balance(cid);
        if(cid>0){
            TextView bal=tv("الرصيد بعد الفاتورة: "+balanceText(currentBalance),12);bal.setTextColor(balanceColor(currentBalance));bal.setGravity(Gravity.CENTER);
            box.addView(bal,new LinearLayout.LayoutParams(-1,dp(28)));
        }
        if(stockWarning!=null&&!stockWarning.trim().isEmpty()){
            TextView warn=tv("⚠️ تنبيه المخزون\n"+stockWarning,11.5f);
            warn.setTextColor(RED);warn.setGravity(Gravity.CENTER);warn.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            warn.setBackground(outlined(Color.rgb(255,246,236),1,8));
            box.addView(warn,new LinearLayout.LayoutParams(-1,dp(58)));spaceTo(box,5);
        }

        LinearLayout actions1=new LinearLayout(this);actions1.setOrientation(LinearLayout.HORIZONTAL);actions1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button share=button("📲 واتساب");share.setTextColor(Color.WHITE);share.setBackground(rounded(GREEN,dp(8)));share.setTextSize(11f);
        Button pdfBtn=button("📄 PDF");pdfBtn.setTextColor(Color.rgb(180,40,40));pdfBtn.setBackground(outline(CARD,8));pdfBtn.setTextSize(11f);
        Button imgBtn=button("🖼️ صورة");imgBtn.setTextColor(Color.rgb(30,100,200));imgBtn.setBackground(outline(CARD,8));imgBtn.setTextSize(11f);
        Button smsBtn=button("✉️ SMS");smsBtn.setTextColor(Color.rgb(20,100,50));smsBtn.setBackground(outline(CARD,8));smsBtn.setTextSize(11f);

        actions1.addView(share,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(0,dp(50),1f); plp.setMargins(dp(3),0,0,0);
        actions1.addView(pdfBtn,plp);
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,dp(50),1f); ilp.setMargins(dp(3),0,0,0);
        actions1.addView(imgBtn,ilp);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(50),1f); slp.setMargins(dp(3),0,0,0);
        actions1.addView(smsBtn,slp);
        box.addView(actions1);
        spaceTo(box,6);

        LinearLayout actions2=new LinearLayout(this);actions2.setOrientation(LinearLayout.HORIZONTAL);actions2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button printBtn=button("🖨️ طباعة");printBtn.setTextColor(Color.WHITE);printBtn.setBackground(rounded(DARK,dp(8)));printBtn.setTextSize(11f);
        Button newInvoice=button("＋ جديدة");newInvoice.setTextColor(GREEN);newInvoice.setBackground(outline(Color.rgb(241,247,242),8));newInvoice.setTextSize(11f);
        Button close=button("✓ إقفال");close.setTextColor(MUTED);close.setBackground(outline(CARD,8));close.setTextSize(11f);

        actions2.addView(printBtn,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,dp(50),1f); nlp.setMargins(dp(3),0,0,0);
        actions2.addView(newInvoice,nlp);
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(50),1f); clp.setMargins(dp(3),0,0,0);
        actions2.addView(close,clp);
        box.addView(actions2);

        share.setOnClickListener(v->{
            dialog.dismiss();
            invoiceHistory();
            shareReceiptImageAndText(no,customer,lines,total,paid);
        });
        pdfBtn.setOnClickListener(v->{
            dialog.dismiss();
            invoiceHistory();
            shareInvoicePdf(no,customer,lines,total,paid,currentBalance,db.now());
        });
        imgBtn.setOnClickListener(v->{
            dialog.dismiss();
            invoiceHistory();
            shareInvoiceImage(no,customer,lines,total,paid,currentBalance,db.now());
        });
        smsBtn.setOnClickListener(v->{
            dialog.dismiss();
            invoiceHistory();
            shareInvoiceSms(no,customer,lines,total,paid);
        });
        printBtn.setOnClickListener(v->{
            printInvoiceBluetooth(no,customer,lines,total);
        });
        newInvoice.setOnClickListener(v->{dialog.dismiss();invoice();});
        close.setOnClickListener(v->{dialog.dismiss();invoiceHistory();});

        dialog.setContentView(box);dialog.setCanceledOnTouchOutside(false);dialog.setCancelable(false);dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(dp(340),WindowManager.LayoutParams.WRAP_CONTENT);dialog.getWindow().setGravity(Gravity.CENTER);}
    }

    
void showOperationDetails(String customer,long tid,String details,double amount,int type){
        final Dialog dlg=new Dialog(this);
        String invNo=db.invoiceNoFromTransaction(details);boolean invoice=!invNo.isEmpty(),debit=type==1;
        int accent=invoice?GREEN:(debit?RED:Color.rgb(20,185,110));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);box.setPadding(dp(7),dp(7),dp(7),dp(7));
        box.setBackground(glassFill(Color.argb(238,245,250,249)));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView icon=denseText(invoice?"🧾":(debit?"🔴":"🟢"),15,9,accent);icon.setGravity(Gravity.CENTER);head.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);ht.setGravity(Gravity.CENTER_VERTICAL);ht.setPadding(dp(4),0,dp(3),0);
        TextView title=denseText(invoice?"فاتورة مبيعات":(debit?"حركة على العميل":"دفعة سداد"),11,8.5f,accent);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView cust=denseText("العميل: "+(customer==null||customer.trim().isEmpty()?"نقدي":customer),8.5f,7.5f,DARK);
        ht.addView(title,new LinearLayout.LayoutParams(-1,dp(16)));ht.addView(cust,new LinearLayout.LayoutParams(-1,dp(14)));head.addView(ht,new LinearLayout.LayoutParams(0,dp(30),1));
        Button close=button("×");close.setTextSize(17);close.setTextColor(DARK);close.setPadding(0,0,0,0);close.setBackgroundColor(Color.TRANSPARENT);close.setOnClickListener(v->dlg.dismiss());head.addView(close,new LinearLayout.LayoutParams(dp(28),dp(30)));
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(31)));

        TextView amountView=denseText((debit?"عليه: ":"له: ")+fmt(amount)+" ريال",12,8.5f,accent);amountView.setGravity(Gravity.CENTER);amountView.setTypeface(Typeface.DEFAULT,Typeface.BOLD);amountView.setBackground(glassFill(debit?Color.argb(205,255,100,115):Color.argb(170,215,246,226)));
        box.addView(amountView,new LinearLayout.LayoutParams(-1,dp(31)));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);body.setPadding(0,dp(3),0,dp(3));
        if(invoice){
            long iid=db.invoiceIdByNo(invNo);
            if(iid>0){
                TextView meta=denseText("#"+invNo+" • "+db.invoiceDate(iid),9,7.5f,DARK);meta.setGravity(Gravity.RIGHT);meta.setBackground(glassFill(Color.argb(145,255,255,255)));body.addView(meta,new LinearLayout.LayoutParams(-1,dp(25)));
                LinearLayout table=new LinearLayout(this);table.setOrientation(LinearLayout.VERTICAL);table.setPadding(dp(3),dp(2),dp(3),dp(2));table.setBackground(glassFill(Color.argb(150,255,255,255)));
                LinearLayout th=new LinearLayout(this);th.setOrientation(LinearLayout.HORIZONTAL);th.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView n1=denseText("الصنف",8.5f,7.5f,DARK),n2=denseText("الكمية",8.5f,7.5f,DARK),n3=denseText("الإجمالي",8.5f,7.5f,DARK);
                n1.setGravity(Gravity.RIGHT);n2.setGravity(Gravity.CENTER);n3.setGravity(Gravity.CENTER);
                th.addView(n1,new LinearLayout.LayoutParams(0,dp(22),1.5f));th.addView(n2,new LinearLayout.LayoutParams(0,dp(22),.7f));th.addView(n3,new LinearLayout.LayoutParams(0,dp(22),.9f));table.addView(th);
                Cursor ic=db.invoiceLines(iid);
                while(ic.moveToNext()){
                    LinearLayout tr=new LinearLayout(this);tr.setOrientation(LinearLayout.HORIZONTAL);tr.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    TextView x=denseText(ic.getString(1),8.5f,7f,DARK),q=denseText(fmt(ic.getDouble(2)),8.5f,7f,DARK),t=denseText(fmt(ic.getDouble(3)),8.5f,7f,GREEN);
                    x.setGravity(Gravity.RIGHT);q.setGravity(Gravity.CENTER);t.setGravity(Gravity.CENTER);
                    tr.addView(x,new LinearLayout.LayoutParams(0,dp(23),1.5f));tr.addView(q,new LinearLayout.LayoutParams(0,dp(23),.7f));tr.addView(t,new LinearLayout.LayoutParams(0,dp(23),.9f));table.addView(tr);
                }
                ic.close();body.addView(table,new LinearLayout.LayoutParams(-1,-2));
                double total=db.invoiceTotal(iid),paid=db.invoicePaid(iid),remain=Math.max(0,total-paid);
                TextView sum=denseText("الإجمالي "+fmt(total)+" • المدفوع "+fmt(paid)+" • المتبقي "+fmt(remain)+" ريال",8.8f,7.2f,accent);sum.setGravity(Gravity.CENTER);sum.setBackground(glassFill(Color.argb(150,255,255,255)));body.addView(sum,new LinearLayout.LayoutParams(-1,27));
            }
        }else{
            TextView det=denseText("البيان: "+((details==null||details.trim().isEmpty())?"لا يوجد بيان":details.trim()),9,7.5f,DARK);
            det.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);det.setBackground(glassFill(Color.argb(145,255,255,255)));body.addView(det,new LinearLayout.LayoutParams(-1,34));
        }
        if(tid>0){
            TextView rb=denseText("الرصيد بعد العملية: "+balanceText(db.balanceAfterTransaction(tid)),9,7.5f,BLUE);rb.setGravity(Gravity.CENTER);rb.setBackground(glassFill(Color.argb(145,255,255,255)));body.addView(rb,new LinearLayout.LayoutParams(-1,28));
        }
        scroll.addView(body);box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button share=button("واتساب"),print=button("58mm"),edit=button(invoice?"الفاتورة":"تعديل"),del=button("حذف");
        Button[] bs={share,print,edit,del};
        for(int i=0;i<4;i++){bs[i].setTextSize(8.5f);bs[i].setPadding(0,0,0,0);bs[i].setBackground(glassFill(Color.argb(175,255,255,255)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(31),1);if(i>0)p.setMargins(dp(3),0,0,0);actions.addView(bs[i],p);}
        share.setTextColor(GREEN);print.setTextColor(GREEN);edit.setTextColor(BLUE);del.setTextColor(RED);
        share.setOnClickListener(v->{dlg.dismiss();shareOperationImage(customer,details,amount,type,invNo);});
        print.setOnClickListener(v->{dlg.dismiss();printOperation(customer,details,amount,type,invNo);});
        long cid=db.customerIdByName(customer);
        edit.setOnClickListener(v->{dlg.dismiss();if(invoice){long iid=db.invoiceIdByNo(invNo);if(iid>0)invoice(true,iid);}else editTransaction(cid,customer,tid,amount,details,type);});
        del.setOnClickListener(v->{dlg.dismiss();new AlertDialog.Builder(this).setTitle("حذف العملية؟").setMessage("سيتم حذف هذه العملية من حساب العميل.").setPositiveButton("حذف",(d,w)->{db.deleteTransaction(tid);account(cid,customer);}).setNegativeButton("إلغاء",null).show();});
        box.addView(actions,new LinearLayout.LayoutParams(-1,dp(32)));

        dlg.setContentView(box);dlg.setCanceledOnTouchOutside(true);dlg.show();
        Window w=dlg.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.96f),(int)(getResources().getDisplayMetrics().heightPixels*.82f));w.setGravity(Gravity.CENTER);}
    }

void operationActions(long customerId,String customerName,long tid,String details,double amount,int type){
        String invNo=db.invoiceNoFromTransaction(details);ArrayList<String> choices=new ArrayList<>();
        if(!invNo.isEmpty()) choices.add("🧾 تعديل الفاتورة");
        else choices.add("✏ تعديل العملية");
        choices.add("📤 مشاركة واتساب (صورة + نص)");choices.add("💬 إرسال رسالة SMS");choices.add("🖨 طباعة 58mm");
        choices.add(invNo.isEmpty()?"🗑 حذف العملية":"🗑 حذف الفاتورة المرتبطة");
        String[] a=choices.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("خيارات العملية").setItems(a,(d,w)->{
            int i=0;
            if(!invNo.isEmpty()&&w==i++){long iid=db.invoiceIdByNo(invNo);if(iid>0)invoice(true,iid);return;}
            if(invNo.isEmpty() && w==i++){editTransaction(customerId,customerName,tid,amount,details,type);return;}
            if(w==i++){shareOperationImage(customerName,details,amount,type,invNo);return;}
            if(w==i++){shareOperationSms(customerName,details,amount,type,invNo);return;}
            if(w==i++){printOperation(customerName,details,amount,type,invNo);return;}
            new AlertDialog.Builder(this).setTitle(invNo.isEmpty()?"حذف العملية؟":"حذف الفاتورة المرتبطة؟").setMessage(invNo.isEmpty()?"سيتم حذف الحركة من حساب العميل.":"هذه الحركة مرتبطة بفاتورة؛ سيتم حذف الفاتورة بالكامل وإرجاع المخزون.")
                .setPositiveButton("حذف",(x,y)->{if(invNo.isEmpty())db.deleteTransaction(tid);else{long iid=db.invoiceIdByNo(invNo);if(iid>0)db.deleteInvoice(iid);}account(customerId,customerName);}).setNegativeButton("إلغاء",null).show();
        }).setNegativeButton("إغلاق",null).show();
    }

    void editTransaction(long id,String name,long tid,double oldAmount,String oldDetails,int oldType){
        EditText amount=numberField("المبلغ");amount.setText(fmt(oldAmount));EditText details=field("التفاصيل");details.setText(oldDetails==null?"":oldDetails);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(4),dp(8),dp(4));box.addView(amount);spaceInside(box,4);box.addView(details);        new AlertDialog.Builder(this).setTitle("تعديل العملية").setView(box).setPositiveButton("حفظ",(d,w)->{
            try{double a=Double.parseDouble(amount.getText().toString().trim());if(a<=0)throw new Exception();db.updateTransaction(tid,a,details.getText().toString().trim(),oldType,db.now());account(id,name);}
            catch(Exception e){Toast.makeText(this,"بيانات العملية غير صحيحة",Toast.LENGTH_SHORT).show();}
        }).setNegativeButton("إلغاء",null).show();
    }

    String compactOperationText(String customer,String details,double amount,int type,String invNo){
        StringBuilder t=new StringBuilder();
        t.append("بقالة العزي للمواد الغذائية :\n");
        if(invNo!=null&&!invNo.trim().isEmpty()){
            t.append("#").append(invNo.trim()).append("\n");
        }
        String cust=customer==null?"":customer.trim();
        if(!cust.isEmpty()){
            t.append(cust).append("\n");
        }
        if(type==1){
            t.append("عليك ").append(fmt(amount)).append(" يمني\n");
        }else{
            t.append("له ").append(fmt(amount)).append(" يمني (دفعة سداد)\n");
        }
        String det=details==null?"":details.trim();
        if(det.startsWith("فاتورة مبيعات رقم ")){
            det=det.replace("فاتورة مبيعات رقم ","فاتورة #");
        }
        if(!det.isEmpty()){
            t.append(det);
        }
        t.append("\n\n");
        double bal=db.balanceByName(customer);
        if(!cust.isEmpty()&&Math.abs(bal)>=0.005){
            if(bal>0.005){
                t.append("الإجمالي - عليك ").append(fmt(bal)).append(" يمني");
            }else{
                t.append("الإجمالي - له ").append(fmt(Math.abs(bal))).append(" يمني");
            }
        }else{
            t.append("الإجمالي - خالص (0 يمني)");
        }
        return t.toString();
    }
    void shareOperation(String customer,String details,double amount,int type,String invNo){
        shareWhatsAppToCustomer(db.phoneByName(customer),compactOperationText(customer,details,amount,type,invNo),null);
    }

    void shareOperationImage(String customer,String details,double amount,int type,String invNo){
        try{
            String text=compactOperationText(customer,details,amount,type,invNo);
            Bitmap b=operationBitmap(customer,details,amount,type,invNo);
            Uri uri=saveReceiptBitmap(b,invNo==null||invNo.isEmpty()?String.valueOf(System.currentTimeMillis()):"عملية_"+invNo);
            shareWhatsAppToCustomer(db.phoneByName(customer),text,uri);
        }catch(Exception e){shareOperation(customer,details,amount,type,invNo);}
    }

    Bitmap operationBitmap(String customer,String details,double amount,int type,String invNo){
        final int width=480;
        final int margin=18;
        Bitmap b=Bitmap.createBitmap(width,390,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.drawColor(Color.WHITE);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokeP=new Paint(Paint.ANTI_ALIAS_FLAG);strokeP.setStyle(Paint.Style.STROKE);strokeP.setStrokeWidth(2);strokeP.setColor(Color.rgb(220,230,222));
        Paint fillP=new Paint(Paint.ANTI_ALIAS_FLAG);

        // Outer border
        c.drawRoundRect(8,8,width-8,382,14,14,strokeP);

        // Header ribbon
        fillP.setColor(Color.rgb(240,248,242));
        c.drawRoundRect(12,12,width-12,82,10,10,fillP);

        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(23);p.setColor(GREEN);p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("بقالة العزي للمواد الغذائية",width-margin-10,40,p);
        p.setTextSize(11.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        c.drawText("مستقبل تجارتك يبدأ من هنا",width-margin-10,58,p);
        p.setTextSize(12.5f);p.setColor(DARK);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        c.drawText("سند قيد مالي إلكتروني  •  إشعار حركة",width-margin-10,68,p);

        int y=104;
        String custName=customer==null||customer.trim().isEmpty()?"عميل نقدي":customer.trim();
        fillP.setColor(Color.rgb(250,252,250));
        c.drawRoundRect(margin,y,width-margin,y+36,8,8,fillP);
        strokeP.setColor(Color.rgb(230,238,232));
        c.drawRoundRect(margin,y,width-margin,y+36,8,8,strokeP);

        p.setTextSize(13.5f);p.setColor(DARK);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("العميل: "+custName,width-margin-12,y+24,p);
        y+=46;

        // Operation Type & Amount Card
        int badgeBgColor=type==1?Color.rgb(255,242,242):Color.rgb(240,249,242);
        int badgeBorderColor=type==1?Color.rgb(245,190,190):Color.rgb(190,230,205);
        int badgeTextColor=type==1?RED:GREEN;
        fillP.setColor(badgeBgColor);
        c.drawRoundRect(margin,y,width-margin,y+64,10,10,fillP);
        strokeP.setColor(badgeBorderColor);
        c.drawRoundRect(margin,y,width-margin,y+64,10,10,strokeP);

        p.setTextSize(13);p.setColor(badgeTextColor);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(type==1?"🔴 حركة سحب (قيد عليه)":"🟢 دفعة سداد (قيد له)",width-margin-14,y+24,p);

        p.setTextSize(22);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.LEFT);
        String amtStr=(type==1?"عليك: ":"له: ")+fmt(amount)+" يمني";
        c.drawText(amtStr,margin+14,y+46,p);
        y+=74;

        // Details Card
        fillP.setColor(Color.rgb(252,254,252));
        c.drawRoundRect(margin,y,width-margin,y+58,8,8,fillP);
        strokeP.setColor(Color.rgb(235,240,236));
        c.drawRoundRect(margin,y,width-margin,y+58,8,8,strokeP);

        String det=details==null?"":details.trim();
        if(det.startsWith("فاتورة مبيعات رقم ")) det=det.replace("فاتورة مبيعات رقم ","فاتورة #");
        String detLabel=det;

        p.setTextSize(13);p.setColor(DARK);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(detLabel,width-margin-12,y+24,p);

        p.setTextSize(11.5f);p.setColor(MUTED);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        String subRef=(invNo!=null&&!invNo.trim().isEmpty()?"فاتورة #"+invNo.trim()+"  •  ":"")+db.now();
        c.drawText(subRef,width-margin-12,y+44,p);
        y+=68;

        // Balance Card
        double currentBal=db.balanceByName(customer);
        if(Math.abs(currentBal)>=0.005){
            fillP.setColor(currentBal>0.005?Color.rgb(255,243,243):Color.rgb(240,248,255));
            strokeP.setColor(currentBal>0.005?Color.rgb(245,200,200):Color.rgb(200,225,250));
            c.drawRoundRect(margin,y,width-margin,y+36,8,8,fillP);
            c.drawRoundRect(margin,y,width-margin,y+36,8,8,strokeP);
            p.setColor(balanceColor(currentBal));p.setTextSize(13.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            String bText=currentBal>0?"الإجمالي - عليك "+fmt(currentBal)+" يمني":"الإجمالي - له "+fmt(Math.abs(currentBal))+" يمني";
            c.drawText(bText,width/2,y+23,p);
            y+=42;
        }else{
            fillP.setColor(Color.rgb(240,248,242));
            strokeP.setColor(Color.rgb(200,235,210));
            c.drawRoundRect(margin,y,width-margin,y+36,8,8,fillP);
            c.drawRoundRect(margin,y,width-margin,y+36,8,8,strokeP);
            p.setColor(GREEN);p.setTextSize(13.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("الإجمالي - خالص (0 يمني)",width/2,y+23,p);
            y+=42;
        }

        y+=6;
        p.setColor(MUTED);p.setTextSize(11);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        c.drawText("✨ شكراً لتعاملكم معنا • بقالة العزي للمواد الغذائية ✨",width/2,y+12,p);

        return b;
    }

    Bitmap operationBitmap(String text){
        return receiptBitmap(text);
    }

    void shareSelectedTransactions(long customerId,String name,ArrayList<Long> ids){
        StringBuilder text=new StringBuilder("📋 *بقالة العزي للمواد الغذائية - كشف عمليات محددة*\n");
        text.append("━━━━━━━━━━━━━━━━━━\n");
        text.append("👤 *العميل:* ").append(name).append("\n");
        text.append("📅 *التاريخ:* ").append(db.now()).append("\n");
        text.append("━━━━━━━━━━━━━━━━━━\n");
        double debit=0,credit=0;
        for(Long tid:ids){
            Cursor c=db.transactionById(tid);
            if(c.moveToFirst()){
                String d=c.getString(3);double a=c.getDouble(4);int t=c.getInt(5);
                text.append(t==1?"🔴 عليه: ":"🟢 له: ").append(fmt(a)).append(" ر.ي");
                if(d!=null&&!d.trim().isEmpty())text.append(" • ").append(d.trim());
                text.append(" (").append(c.getString(2)).append(")\n");
                if(t==1)debit+=a;else credit+=a;
            }
            c.close();
        }
        text.append("━━━━━━━━━━━━━━━━━━\n");
        text.append("🔻 *إجمالي المحدد عليه:* ").append(fmt(debit)).append(" ريال\n");
        text.append("🔺 *إجمالي المحدد له:* ").append(fmt(credit)).append(" ريال\n");
        text.append("📊 *الرصيد الإجمالي الحالي:* ").append(balanceText(db.balance(customerId))).append("\n");
        text.append("━━━━━━━━━━━━━━━━━━\n");
        text.append("✨ *بقالة العزي للمواد الغذائية - خدمة متميزة* ✨");
        shareWhatsAppToCustomer(db.phoneByName(name),text.toString(),null);
    }

    void printSelectedTransactions(long customerId,String name,ArrayList<Long> ids){
        StringBuilder text=new StringBuilder("بقالة العزي للمواد الغذائية\nكشف عمليات: ").append(name).append("\nالتاريخ: ").append(db.now()).append("\n");
        text.append("------------------------------\n");
        double debit=0,credit=0;
        for(Long tid:ids){
            Cursor c=db.transactionById(tid);
            if(c.moveToFirst()){
                String d=c.getString(3);double a=c.getDouble(4);int t=c.getInt(5);
                text.append(c.getString(2)).append("\n");
                text.append(t==1?"عليه: ":"له: ").append(fmt(a)).append(" ريال");
                if(d!=null&&!d.trim().isEmpty())text.append(" | ").append(d.trim());
                text.append("\n");
                if(t==1)debit+=a;else credit+=a;
            }c.close();
        }
        text.append("------------------------------\nإجمالي المحدد عليه: ").append(fmt(debit)).append(" ريال\n");
        text.append("إجمالي المحدد له: ").append(fmt(credit)).append(" ريال\n");
        text.append("الرصيد الحالي: ").append(balanceText(db.balance(customerId)));
        previewTextForPrint(text.toString(),name);
    }

    void printOperation(String customer,String details,double amount,int type,String invNo){
        try{
            ArrayList<Line> ls=new ArrayList<>();
            if(invNo!=null&&!invNo.isEmpty()){
                long iid=db.invoiceIdByNo(invNo);
                if(iid>0){Cursor c=db.invoiceLines(iid);while(c.moveToNext())ls.add(new Line(c.getString(1),c.getDouble(2),c.getDouble(3)));c.close();}
            }
            String text=!ls.isEmpty()?receiptTextFromLines(invNo,customer,ls,totalOf(ls),db.customer(customer)):
                "بقالة العزي للمواد الغذائية\nعملية مالية\nالعميل: "+customer+"\n"+(details==null||details.isEmpty()?"":details+"\n")+(type==1?"عليه: ":"له: ")+fmt(amount)+" ريال\n"+balanceText(db.balanceByName(customer))+"\nالتاريخ: "+db.now();
            previewTextForPrint(text,customer);
        }catch(Exception e){Toast.makeText(this,"تعذر تجهيز العملية للطباعة",Toast.LENGTH_LONG).show();}
    }

    void previewTextForPrint(String text,String customer){
        TextView v=tv(text,12);v.setGravity(Gravity.CENTER);v.setTypeface(Typeface.MONOSPACE);
        new AlertDialog.Builder(this).setTitle("معاينة العملية 58mm").setView(v).setPositiveButton("طباعة",(d,w)->printTextBluetooth(text)).setNegativeButton("إغلاق",null).show();
    }
    String statement(long id,String name){
        StringBuilder s=new StringBuilder();
        s.append("بقالة العزي للمواد الغذائية\nكشف حساب\n");
        s.append("العميل: ").append(name).append("\n");
        s.append("التاريخ: ").append(db.now()).append("\n\n");
        double running=db.balance(id),debit=0,credit=0;Cursor c=db.transactions(id);
        while(c.moveToNext()){
            String date=c.getString(1),details=c.getString(2);double amount=c.getDouble(3);int type=c.getInt(4);
            if(type==1)debit+=amount;else credit+=amount;
            String inv=db.invoiceNoFromTransaction(details);
            s.append(date).append("\n");
            s.append(type==1?"عليه: ":"له: ").append(fmt(amount)).append(" ريال");
            if(!inv.isEmpty())s.append(" • فاتورة #").append(inv);
            s.append("\n");
            if(details!=null&&!details.trim().isEmpty())s.append(details.trim()).append("\n");
            s.append("الرصيد بعد العملية: ").append(balanceText(running)).append("\n\n");
            running-=(type==1?amount:-amount);
        }c.close();
        s.append("ــــــــــــــــــــ\n");
        s.append("إجمالي عليه: ").append(fmt(debit)).append(" ريال\n");
        s.append("إجمالي له: ").append(fmt(credit)).append(" ريال\n");
        s.append("الرصيد الحالي: ").append(balanceText(db.balance(id)));
        return s.toString();
    }

    void inventory(){
        base("المخزون");
        section("إضافة / تعديل صنف بالمخزون");

        LinearLayout formCard=card();
        formCard.setPadding(dp(10),dp(10),dp(10),dp(10));
        formCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        EditText name=field("اسم الصنف");
        EditText qty=numberField("الكمية الحالية");
        EditText min=numberField("الحد الأدنى للتنبيه");
        name.setHintTextColor(MUTED); qty.setHintTextColor(MUTED); min.setHintTextColor(MUTED);

        formCard.addView(name,new LinearLayout.LayoutParams(-1,dp(52)));
        spaceTo(formCard,4);

        LinearLayout rowQty=new LinearLayout(this);
        rowQty.setOrientation(LinearLayout.HORIZONTAL);
        rowQty.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        rowQty.addView(qty,new LinearLayout.LayoutParams(0,dp(52),1));
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(0,dp(52),1); mlp.setMargins(dp(6),0,0,0);
        rowQty.addView(min,mlp);
        formCard.addView(rowQty,new LinearLayout.LayoutParams(-1,dp(42)));
        spaceTo(formCard,6);

        LinearLayout stockForm=new LinearLayout(this);
        stockForm.setOrientation(LinearLayout.HORIZONTAL);
        stockForm.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button add=button("＋ حفظ الصنف");
        add.setTextColor(Color.WHITE); add.setBackground(rounded(GREEN,dp(10)));
        Button clearFormBtn=button("مسح");
        clearFormBtn.setTextColor(MUTED); clearFormBtn.setBackground(outline(CARD,10));

        stockForm.addView(add,new LinearLayout.LayoutParams(0,dp(52),1.7f));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(52),0.7f); clp.setMargins(dp(6),0,0,0);
        stockForm.addView(clearFormBtn,clp);
        formCard.addView(stockForm,new LinearLayout.LayoutParams(-1,dp(42)));

        content.addView(formCard,new LinearLayout.LayoutParams(-1,-2));
        addSpace(8);

        int lowStockCount=db.lowStockCount();
        if(lowStockCount>0){
            LinearLayout alertBanner=new LinearLayout(this);
            alertBanner.setOrientation(LinearLayout.HORIZONTAL);
            alertBanner.setGravity(Gravity.CENTER_VERTICAL);
            alertBanner.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            alertBanner.setPadding(dp(12),dp(8),dp(12),dp(8));
            GradientDrawable abBg=new GradientDrawable();
            abBg.setColor(Color.rgb(255,245,242));
            abBg.setCornerRadius(dp(12));
            abBg.setStroke(dp(1),Color.rgb(255,190,180));
            alertBanner.setBackground(abBg);

            TextView abIcon=tv("⚠️",18);
            abIcon.setGravity(Gravity.CENTER);
            alertBanner.addView(abIcon,new LinearLayout.LayoutParams(dp(32),dp(32)));

            LinearLayout abTexts=new LinearLayout(this);
            abTexts.setOrientation(LinearLayout.VERTICAL);
            abTexts.setPadding(dp(6),0,dp(6),0);
            TextView abTitle=tv("يوجد "+lowStockCount+" أصناف أوشكت على النفاد!",12.5f);
            abTitle.setTextColor(RED); abTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            TextView abSub=tv("اضغط لعرض قائمة النواقص وإرسالها لمندوب المورد عبر واتساب",10.5f);
            abSub.setTextColor(MUTED);
            abTexts.addView(abTitle,new LinearLayout.LayoutParams(-1,-2));
            abTexts.addView(abSub,new LinearLayout.LayoutParams(-1,-2));
            alertBanner.addView(abTexts,new LinearLayout.LayoutParams(0,-2,1));

            Button viewLowBtn=button("عرض");
            viewLowBtn.setTextSize(11);
            viewLowBtn.setTextColor(Color.WHITE);
            viewLowBtn.setBackground(rounded(RED,dp(8)));
            viewLowBtn.setOnClickListener(v->showLowStockDialog());
            alertBanner.addView(viewLowBtn,new LinearLayout.LayoutParams(dp(54),dp(32)));

            content.addView(alertBanner,new LinearLayout.LayoutParams(-1,-2));
            addSpace(8);
        }

        section("قائمة الأصناف بالمخزون");
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        final long[] editingId={-1};
        Runnable clearForm=()->{
            editingId[0]=-1;
            name.setText("");qty.setText("");min.setText("");
            add.setText("＋ حفظ الصنف");
        };

        clearFormBtn.setOnClickListener(v->clearForm.run());

        final Runnable[] refresh={null};
        refresh[0]=()->{
            list.removeAllViews();
            Cursor c=db.items();
            int count=0;
            while(c.moveToNext()){
                long id=c.getLong(0);
                String itemName=c.getString(1);
                double q=c.getDouble(2),m=c.getDouble(3);
                count++;

                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10),dp(8),dp(10),dp(8));
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                GradientDrawable rBg=new GradientDrawable();
                rBg.setColor(CARD);
                rBg.setCornerRadius(dp(12));
                rBg.setStroke(dp(1),q<=m?Color.rgb(245,210,180):Color.rgb(225,232,226));
                row.setBackground(rBg);

                LinearLayout topR=new LinearLayout(this);
                topR.setOrientation(LinearLayout.HORIZONTAL);
                topR.setGravity(Gravity.CENTER_VERTICAL);
                topR.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView nameTv=tv("📦 "+itemName,13.5f);
                nameTv.setTextColor(TEXT); nameTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                topR.addView(nameTv,new LinearLayout.LayoutParams(0,-2,1));

                if(q<=m){
                    TextView warn=tv("⚠️ منخفض",10);
                    warn.setTextColor(RED); warn.setGravity(Gravity.CENTER);
                    warn.setPadding(dp(6),dp(2),dp(6),dp(2));
                    GradientDrawable wBg=new GradientDrawable();
                    wBg.setColor(Color.rgb(255,240,238));
                    wBg.setCornerRadius(dp(6));
                    warn.setBackground(wBg);
                    topR.addView(warn,new LinearLayout.LayoutParams(-2,-2));
                }

                row.addView(topR,new LinearLayout.LayoutParams(-1,-2));
                spaceTo(row,4);

                LinearLayout metaRow=new LinearLayout(this);
                metaRow.setOrientation(LinearLayout.HORIZONTAL);
                metaRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView qtyTv=tv("الكمية الحالية: "+fmt(q),11.5f);
                qtyTv.setTextColor(q<=m?RED:GREEN); qtyTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                metaRow.addView(qtyTv,new LinearLayout.LayoutParams(0,-2,1));

                TextView minTv=tv("الحد الأدنى: "+fmt(m),11);
                minTv.setTextColor(MUTED);
                metaRow.addView(minTv,new LinearLayout.LayoutParams(0,-2,1));

                row.addView(metaRow,new LinearLayout.LayoutParams(-1,-2));
                spaceTo(row,6);

                LinearLayout actions=new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                Button editBtn=button("✏️ تعديل");
                editBtn.setTextSize(11.5f); editBtn.setTextColor(BLUE); editBtn.setBackground(outline(CARD,8));

                Button deleteBtn=button("🗑️ حذف");
                deleteBtn.setTextSize(11.5f); deleteBtn.setTextColor(RED); deleteBtn.setBackground(outline(CARD,8));

                editBtn.setOnClickListener(v->{
                    editingId[0]=id;
                    name.setText(itemName);qty.setText(fmt(q));min.setText(fmt(m));
                    add.setText("✓ حفظ التعديل");
                    name.requestFocus();
                    Toast.makeText(this,"تم تحميل الصنف للتعديل",Toast.LENGTH_SHORT).show();
                });

                deleteBtn.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("حذف الصنف")
                    .setMessage("هل تريد حذف «"+itemName+"» نهائياً؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("حذف",(d,w)->{
                        db.deleteItem(id);
                        if(editingId[0]==id)clearForm.run();
                        refresh[0].run();
                        Toast.makeText(this,"تم حذف الصنف",Toast.LENGTH_SHORT).show();
                    }).show());

                actions.addView(editBtn,new LinearLayout.LayoutParams(0,dp(32),1));
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(32),1); dlp.setMargins(dp(6),0,0,0);
                actions.addView(deleteBtn,dlp);
                row.addView(actions,new LinearLayout.LayoutParams(-1,dp(46)));

                LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);
                rlp.setMargins(0,0,0,dp(6));
                list.addView(row,rlp);
            }
            c.close();

            if(count==0){
                LinearLayout emptyBox=card();
                emptyBox.setPadding(dp(16),dp(16),dp(16),dp(16));
                emptyBox.setGravity(Gravity.CENTER);
                TextView em=tv("📦 لا توجد أصناف في المخزون حتى الآن",12.5f);
                em.setTextColor(MUTED); em.setGravity(Gravity.CENTER);
                emptyBox.addView(em,new LinearLayout.LayoutParams(-1,dp(30)));
                list.addView(emptyBox,new LinearLayout.LayoutParams(-1,-2));
            }
        };

        add.setOnClickListener(v->{
            try{
                String n=name.getText().toString().trim();
                double q=Double.parseDouble(qty.getText().toString().trim());
                double m=Double.parseDouble(min.getText().toString().trim());
                if(n.isEmpty()||q<0||m<0)throw new Exception();

                if(editingId[0]>0){
                    db.updateItem(editingId[0],n,q,m);
                    Toast.makeText(this,"تم تعديل الصنف وحفظه",Toast.LENGTH_SHORT).show();
                }else{
                    boolean existed=db.itemExists(n);
                    db.addItem(n,q,m);
                    Toast.makeText(this,existed?"الصنف موجود؛ تم تحديث بياناته":"تم حفظ الصنف",Toast.LENGTH_SHORT).show();
                }
                clearForm.run();
                refresh[0].run();
            }catch(Exception e){
                Toast.makeText(this,"أدخل بيانات الصنف بشكل صحيح",Toast.LENGTH_SHORT).show();
            }
        });
        refresh[0].run();
    }
    static class NoteItem { String name; double qty; int side; NoteItem(String n,double q,int s){name=n;qty=q;side=s;} }
    void transfers(){
        base("الحوالات");

        TextView title=tv("💸 الحوالات المالية",20);
        title.setTextColor(GREEN);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));

        TextView intro=tv("إدخال الحوالة ومراجعة سجل العمليات — بعرض الشاشة الكامل",15);
        intro.setTextColor(MUTED);
        intro.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        content.addView(intro,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(4);

        LinearLayout form=card();
        form.setPadding(dp(10),dp(10),dp(10),dp(12));

        TextView amountTitle=tv("المبلغ",18);
        amountTitle.setTextColor(DARK);
        amountTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        amountTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        form.addView(amountTitle,new LinearLayout.LayoutParams(-1,dp(46)));

        TextView amountHelp=tv("المبلغ بالريال اليمني — اكتب الرقم هنا، مثال: 2,500",16);
        amountHelp.setTextColor(BLUE);
        amountHelp.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        amountHelp.setPadding(dp(4),0,dp(4),0);
        form.addView(amountHelp,new LinearLayout.LayoutParams(-1,dp(46)));

        EditText amount=numberField("المبلغ بالريال — مثال 2,500");
        amount.setTextSize(18);
        amount.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        amount.setPadding(dp(14),0,dp(14),0);
        amount.setHintTextColor(MUTED);
        amount.setBackground(outlined(Color.rgb(248,250,253),dp(2),14));
        amount.setContentDescription("مربع إدخال مبلغ الحوالة بالريال");
        amount.setOnFocusChangeListener((v,has)->{
            if(has){ amount.postDelayed(amount::selectAll,60); }
            else{
                try{
                    double value=Double.parseDouble(amount.getText().toString().replace(",","").trim());
                    if(value>0) amount.setText(fmt(value));
                }catch(Exception ignored){}
            }
        });
        form.addView(amount,new LinearLayout.LayoutParams(-1,dp(72)));
        spaceTo(form,12);

        TextView receiverTitle=tv("المستلم",20);
        receiverTitle.setTextColor(GREEN);
        receiverTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        form.addView(receiverTitle,new LinearLayout.LayoutParams(-1,dp(46)));

        transferReceiverName=field("اسم المستلم");
        transferReceiverPhone=phoneField("رقم هاتف المستلم");
        form.addView(transferReceiverName,new LinearLayout.LayoutParams(-1,dp(56)));
        spaceTo(form,6);

        form.addView(transferReceiverPhone,new LinearLayout.LayoutParams(-1,dp(58)));
        spaceTo(form,6);
        Button rc=button("📇 اختيار رقم المستلم");
        rc.setTextSize(15);
        rc.setTextColor(BLUE);
        rc.setContentDescription("اختيار رقم المستلم من جهات الاتصال");
        rc.setOnClickListener(v->{transferContactNameTarget=transferReceiverName;transferContactPhoneTarget=transferReceiverPhone;openTransferContactPicker();});
        form.addView(rc,new LinearLayout.LayoutParams(-1,dp(50)));
        spaceTo(form,12);

        TextView senderTitle=tv("المرسل",20);
        senderTitle.setTextColor(GREEN);
        senderTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        form.addView(senderTitle,new LinearLayout.LayoutParams(-1,dp(46)));

        transferSenderName=field("اسم المرسل");
        transferSenderPhone=phoneField("رقم هاتف المرسل");
        form.addView(transferSenderName,new LinearLayout.LayoutParams(-1,dp(56)));
        spaceTo(form,6);

        form.addView(transferSenderPhone,new LinearLayout.LayoutParams(-1,dp(58)));
        spaceTo(form,6);
        Button pc=button("📇 اختيار رقم المرسل");
        pc.setTextSize(15);
        pc.setTextColor(BLUE);
        pc.setContentDescription("اختيار رقم المرسل من جهات الاتصال");
        pc.setOnClickListener(v->{transferContactNameTarget=transferSenderName;transferContactPhoneTarget=transferSenderPhone;openTransferContactPicker();});
        form.addView(pc,new LinearLayout.LayoutParams(-1,dp(50)));
        spaceTo(form,12);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save=action("✓ حفظ الحوالة",GREEN);
        save.setTextSize(17);
        Button clear=button("🧹 مسح الحقول");
        clear.setTextColor(RED);
        clear.setTextSize(15);
        clear.setOnClickListener(v->{
            amount.setText("");
            transferReceiverName.setText("");
            transferReceiverPhone.setText("");
            transferSenderName.setText("");
            transferSenderPhone.setText("");
            transferReceiverName.requestFocus();
        });
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1.25f));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(52),1);
        clp.setMargins(dp(7),0,0,0);
        actions.addView(clear,clp);
        form.addView(actions,new LinearLayout.LayoutParams(-1,dp(54)));

        content.addView(form,new LinearLayout.LayoutParams(-1,-2));
        addSpace(14);

        TextView st=tv("سجل الحوالات",22);
        st.setTextColor(GREEN);
        st.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        content.addView(st,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView stHint=tv("🔴 تحتاج مراجعة وتجهيز  •  🔵 مجهزة",16);
        stHint.setTextColor(MUTED);
        content.addView(stHint,new LinearLayout.LayoutParams(-1,dp(32)));

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list,new LinearLayout.LayoutParams(-1,-2));

        Runnable render=()->{
            list.removeAllViews();
            Cursor c=db.transfers();
            int count=0;
            while(c.moveToNext()){
                long id=c.getLong(0);
                double am=c.getDouble(1);
                String sn=c.getString(2),sp=c.getString(3),rn=c.getString(4),rp2=c.getString(5),dt=c.getString(6);
                int rev=c.getInt(8);
                count++;

                LinearLayout row=card();
                row.setPadding(dp(10),dp(9),dp(10),dp(10));
                GradientDrawable rowBg=new GradientDrawable();
                rowBg.setColor(rev!=0?Color.rgb(255,247,247):Color.rgb(244,250,247));
                rowBg.setCornerRadius(dp(14));
                rowBg.setStroke(dp(1),rev!=0?Color.rgb(226,155,155):Color.rgb(145,205,165));
                row.setBackground(rowBg);

                LinearLayout top=new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                TextView amountOut=tv(fmt(am)+" ريال",20);
                amountOut.setTextColor(rev!=0?RED:GREEN);
                amountOut.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                amountOut.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                top.addView(amountOut,new LinearLayout.LayoutParams(0,dp(42),1));

                TextView status=tv(rev!=0?"🔴 تحتاج مراجعة":"🔵 مجهزة",14);
                status.setTextColor(rev!=0?RED:BLUE);
                status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                status.setGravity(Gravity.CENTER);
                top.addView(status,new LinearLayout.LayoutParams(dp(120),dp(38)));
                row.addView(top,new LinearLayout.LayoutParams(-1,dp(44)));

                LinearLayout receiverBox=card();
                receiverBox.setPadding(dp(8),dp(5),dp(8),dp(5));
                TextView rr=tv("المستلم: "+(rn==null||rn.isEmpty()?"—":rn)+"\nالهاتف: "+(rp2==null||rp2.isEmpty()?"—":rp2),16);
                rr.setMaxLines(3);
                rr.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                rr.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
                receiverBox.addView(rr,new LinearLayout.LayoutParams(-1,dp(62)));
                row.addView(receiverBox,new LinearLayout.LayoutParams(-1,dp(74)));
                spaceTo(row,6);

                LinearLayout senderBox=card();
                senderBox.setPadding(dp(8),dp(5),dp(8),dp(5));
                TextView sr=tv("المرسل: "+(sn==null||sn.isEmpty()?"—":sn)+"\nالهاتف: "+(sp==null||sp.isEmpty()?"—":sp),16);
                sr.setMaxLines(3);
                sr.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                sr.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
                senderBox.addView(sr,new LinearLayout.LayoutParams(-1,dp(62)));
                row.addView(senderBox,new LinearLayout.LayoutParams(-1,dp(74)));
                spaceTo(row,6);

                TextView dateOut=tv("التاريخ والوقت: "+dt,13);
                dateOut.setTextColor(MUTED);
                dateOut.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                row.addView(dateOut,new LinearLayout.LayoutParams(-1,dp(30)));

                LinearLayout acts=new LinearLayout(this);
                acts.setOrientation(LinearLayout.HORIZONTAL);
                acts.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                Button sh=button("📲 واتساب");
                sh.setTextColor(Color.WHITE);
                sh.setTextSize(14);
                sh.setBackground(rounded(Color.rgb(0,145,70),dp(9)));
                String transferText=fmt(am)+" صافي\nالمستلم: "+rn+"\n"+rp2+"\nالمرسل: "+sn+"\n"+sp;
                sh.setOnClickListener(v->shareTransferDirectToWhatsApp("776425052",transferText));

                Button copy=button("📋 نسخ");
                copy.setTextColor(BLUE);
                copy.setTextSize(14);
                copy.setOnClickListener(v->{
                    ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("نص الحوالة",transferText));
                    Toast.makeText(this,"تم نسخ بيانات الحوالة",Toast.LENGTH_SHORT).show();
                });

                acts.addView(sh,new LinearLayout.LayoutParams(0,dp(46),1));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);
                cp.setMargins(dp(7),0,0,0);
                acts.addView(copy,cp);

                if(rev!=0){
                    Button ready=action("✓ تجهيز الحوالة",BLUE);
                    ready.setTextSize(15);
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(46),1.25f);
                    rp.setMargins(dp(7),0,0,0);
                    acts.addView(ready,rp);
                    ready.setOnClickListener(v->{db.markTransferReady(id);transfers();});
                }

                Button de=button("🗑 حذف");
                de.setTextColor(RED);
                de.setTextSize(14);
                LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,dp(46),.9f);
                dl.setMargins(dp(7),0,0,0);
                acts.addView(de,dl);
                de.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("حذف الحوالة")
                    .setMessage("هل تريد حذف هذه الحوالة نهائياً؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("حذف",(x,y)->{db.deleteTransfer(id);transfers();})
                    .show());

                row.addView(acts,new LinearLayout.LayoutParams(-1,dp(50)));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,0,0,dp(9));
                list.addView(row,lp);
            }
            c.close();
            if(count==0){
                TextView empty=tv("لا توجد حوالات مسجلة بعد",16);
                empty.setTextColor(MUTED);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));
            }
        };

        save.setOnClickListener(v->{
            double am=0;
            try{am=Double.parseDouble(amount.getText().toString().replace(",","").trim());}catch(Exception ignored){}
            String rn=transferReceiverName.getText().toString().trim();
            String rp2=transferReceiverPhone.getText().toString().trim();
            String sn=transferSenderName.getText().toString().trim();
            String sp=transferSenderPhone.getText().toString().trim();

            if(am<=0){
                amount.setError("أدخل مبلغ الحوالة بالريال");
                amount.requestFocus();
                return;
            }
            if(rn.isEmpty()||rp2.isEmpty()||sn.isEmpty()||sp.isEmpty()){
                Toast.makeText(this,"أكمل اسم ورقم المستلم واسم ورقم المرسل",Toast.LENGTH_LONG).show();
                return;
            }

            boolean dup=db.transferDuplicate(am,sp,rp2);
            if(dup){
                final double finalAm=am;
                final String finalRn=rn,finalRp2=rp2,finalSn=sn,finalSp=sp;
                new AlertDialog.Builder(this)
                    .setTitle("⚠️ عملية مكررة")
                    .setMessage("توجد حوالة مشابهة بالفعل. هل تريد حفظها للمراجعة؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("حفظ للمراجعة",(d,w)->{
                        db.addTransfer(finalAm,finalSn,finalSp,finalRn,finalRp2,"",1);
                        transfers();
                    }).show();
                return;
            }

            db.addTransfer(am,sn,sp,rn,rp2,"",1);
            amount.setText("");
            transferReceiverName.setText("");
            transferReceiverPhone.setText("");
            transferSenderName.setText("");
            transferSenderPhone.setText("");
            render.run();
            Toast.makeText(this,"تم حفظ الحوالة للمراجعة والتجهيز",Toast.LENGTH_SHORT).show();
        });
        render.run();
    }

    void openTransferContactPicker(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission("android.permission.READ_CONTACTS")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.READ_CONTACTS"},REQ_CONTACTS);return;}try{Intent i=new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI);startActivityForResult(i,4106);}catch(Exception e){Toast.makeText(this,"تعذر فتح جهات الاتصال",Toast.LENGTH_SHORT).show();}}

void notes(){ base("الملاحظات");
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button clear=action("🧹 تفريغ",Color.rgb(235,130,35));clear.setOnClickListener(v->clearNotesPage());
        Button fresh=action("＋ صفحة",Color.rgb(35,155,190));fresh.setOnClickListener(v->newNotesPage());
        Button history=action("📚 السجل",Color.rgb(125,70,170));history.setOnClickListener(v->showNotesHistory());
        Button shareNotes=action("📤 مشاركة",Color.rgb(37,211,102));shareNotes.setOnClickListener(v->shareCurrentNotes());
        Button print=action("🖨 طباعة",GREEN);print.setOnClickListener(v->printCurrentNotes());
        top.addView(clear,new LinearLayout.LayoutParams(0,dp(42),1));
        top.addView(fresh,new LinearLayout.LayoutParams(0,dp(42),1));
        top.addView(history,new LinearLayout.LayoutParams(0,dp(42),1));
        top.addView(shareNotes,new LinearLayout.LayoutParams(0,dp(42),1));
        top.addView(print,new LinearLayout.LayoutParams(0,dp(42),1));
        content.addView(top,new LinearLayout.LayoutParams(-1,dp(46)));addSpace(5);
        LinearLayout controls=card();LinearLayout cr=new LinearLayout(this);cr.setGravity(Gravity.CENTER);Button minus=button("−");TextView fs=tv("حجم الخط "+noteFontSize,11);fs.setGravity(Gravity.CENTER);Button plus=button("+");minus.setOnClickListener(v->{noteFontSize=Math.max(10,noteFontSize-1);notes();});plus.setOnClickListener(v->{noteFontSize=Math.min(24,noteFontSize+1);notes();});cr.addView(minus,new LinearLayout.LayoutParams(dp(38),dp(34)));cr.addView(fs,new LinearLayout.LayoutParams(dp(100),dp(34)));cr.addView(plus,new LinearLayout.LayoutParams(dp(38),dp(34)));Switch sw=new Switch(this);sw.setText("وضع التمرير: "+(noteScrollMode?"مفعل":"متوقف"));sw.setChecked(noteScrollMode);sw.setOnCheckedChangeListener((b,x)->{noteScrollMode=x;b.setText("وضع التمرير: "+(x?"مفعل":"متوقف"));});cr.addView(sw,new LinearLayout.LayoutParams(-2,dp(34)));controls.addView(cr);content.addView(controls,new LinearLayout.LayoutParams(-1,dp(44)));addSpace(5);
        if(currentNotePageId<1)currentNotePageId=db.createNotePage("ملاحظة جديدة",db.now());final long pid=currentNotePageId;ArrayList<NoteItem> left=new ArrayList<>(),right=new ArrayList<>();db.loadNoteItems(pid,left,right);
        LinearLayout form=card();LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.HORIZONTAL);fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);EditText qty=numberField("العدد / الرقم");qty.setText("1");EditText name=field("اكتب اسم الصنف...");fields.addView(qty,new LinearLayout.LayoutParams(0,dp(52),.8f));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(52),2.1f);np.setMargins(dp(4),0,dp(4),0);fields.addView(name,np);form.addView(fields);
        LinearLayout adds=new LinearLayout(this);adds.setOrientation(LinearLayout.HORIZONTAL);Button al=button("＋ للشق الأيسر");al.setTextColor(Color.WHITE);al.setBackground(rounded(GREEN,dp(10)));al.setOnClickListener(v->addNoteItem(pid,name,qty,1));Button ar=button("＋ للشق الأيمن");ar.setTextColor(Color.WHITE);ar.setBackground(rounded(BLUE,dp(10)));ar.setOnClickListener(v->addNoteItem(pid,name,qty,2));adds.addView(al,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams arp=new LinearLayout.LayoutParams(0,dp(50),1);arp.setMargins(dp(4),0,0,0);adds.addView(ar,arp);form.addView(adds);content.addView(form,new LinearLayout.LayoutParams(-1,dp(88)));addSpace(5);
        LinearLayout split=new LinearLayout(this);split.setOrientation(LinearLayout.HORIZONTAL);split.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);split.addView(noteColumn("الشق الأيسر",left,1,pid),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.setMargins(dp(4),0,0,0);split.addView(noteColumn("الشق الأيمن",right,2,pid),rp);content.addView(split,new LinearLayout.LayoutParams(-1,-2));
    }
    LinearLayout noteColumn(String title,ArrayList<NoteItem> items,int side,long pid){LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(3),dp(3),dp(3),dp(5));col.setBackground(outlined(Color.rgb(252,253,252),1,12));TextView h=tv(title,11);h.setTextColor(side==1?GREEN:BLUE);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setGravity(Gravity.CENTER);col.addView(h,new LinearLayout.LayoutParams(-1,dp(30)));if(items.isEmpty()){TextView e=tv("لا توجد عناصر",9);e.setTextColor(MUTED);e.setGravity(Gravity.CENTER);col.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));return col;}for(NoteItem it:items){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);row.setBackground(outlined(CARD,1,9));Button del=button("🗑");del.setTextColor(RED);del.setOnClickListener(v->{db.deleteNoteItem(pid,it.name,it.qty,it.side);notes();});TextView nm=tv(it.name,noteFontSize);nm.setTextColor(Color.rgb(20,65,120));nm.setMaxLines(2);TextView q=tv(fmt(it.qty),noteFontSize);q.setGravity(Gravity.CENTER);q.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(del,new LinearLayout.LayoutParams(dp(38),dp(44)));row.addView(nm,new LinearLayout.LayoutParams(0,dp(44),1));row.addView(q,new LinearLayout.LayoutParams(dp(45),dp(44)));col.addView(row,new LinearLayout.LayoutParams(-1,dp(46)));spaceTo(col,2);}return col;}
    void addNoteItem(long pid,EditText name,EditText qty,int side){String n=name.getText().toString().trim();double q=0; try { q=Double.parseDouble(qty.getText().toString().trim().replace(",", ".")); } catch(Exception ignored) {}if(n.isEmpty()){Toast.makeText(this,"اكتب اسم الصنف أولاً",Toast.LENGTH_SHORT).show();return;}if(q<=0){Toast.makeText(this,"العدد يجب أن يكون أكبر من صفر",Toast.LENGTH_SHORT).show();return;}db.addNoteItem(pid,n,q,side);name.setText("");qty.setText("1");notes();}
    void clearNotesPage(){if(currentNotePageId<1)return;new AlertDialog.Builder(this).setTitle("تفريغ الصفحة").setMessage("سيتم حذف عناصر الصفحة الحالية فقط. هل تريد المتابعة؟").setNegativeButton("إلغاء",null).setPositiveButton("تفريغ",(d,w)->{db.clearNoteItems(currentNotePageId);notes();}).show();}
    void newNotesPage(){if(currentNotePageId>0)db.touchNotePage(currentNotePageId);currentNotePageId=db.createNotePage("ملاحظة جديدة",db.now());notes();}
    void showNotesHistory(){base("سجل الصفحات");section("الصفحات المحفوظة");Cursor c=db.notePages();while(c.moveToNext()){long id=c.getLong(0);String title=c.getString(1),date=c.getString(2);int n=c.getInt(3);LinearLayout row=card();TextView t=tv("📝 "+title+"\n"+date+" • "+n+" عنصر",12);t.setMaxLines(2);row.addView(t,new LinearLayout.LayoutParams(-1,dp(52)));row.setOnClickListener(v->{currentNotePageId=id;notes();});content.addView(row,new LinearLayout.LayoutParams(-1,dp(62)));addSpace(3);}c.close();}
    String notesWhatsAppText(){
        StringBuilder s=new StringBuilder("📝 *بقالة العزي للمواد الغذائية - الملاحظات الذكية*\n");
        s.append("━━━━━━━━━━━━━━━━━━\n");
        s.append("📅 *التاريخ:* ").append(db.now()).append("\n");
        s.append("━━━━━━━━━━━━━━━━━━\n");
        ArrayList<NoteItem> l=new ArrayList<>(),r=new ArrayList<>();
        db.loadNoteItems(currentNotePageId,l,r);
        if(!l.isEmpty()){
            s.append("🔹 *الشق الأيسر:*\n");
            for(NoteItem x:l)s.append("▪️ ").append(x.name).append(" × ").append(fmt(x.qty)).append("\n");
            s.append("──────────────────\n");
        }
        if(!r.isEmpty()){
            s.append("🔸 *الشق الأيمن:*\n");
            for(NoteItem x:r)s.append("▪️ ").append(x.name).append(" × ").append(fmt(x.qty)).append("\n");
            s.append("──────────────────\n");
        }
        s.append("✨ *بقالة العزي للمواد الغذائية* ✨");
        return s.toString();
    }
    String notesReceiptText(){
        StringBuilder s=new StringBuilder("بقالة العزي للمواد الغذائية\nالملاحظات الذكية\nالتاريخ: ").append(db.now()).append("\n");
        ArrayList<NoteItem> l=new ArrayList<>(),r=new ArrayList<>();
        db.loadNoteItems(currentNotePageId,l,r);
        if(!l.isEmpty()){
            s.append("------------------------------\nالشق الأيسر:\n");
            for(NoteItem x:l)s.append(x.name).append(" × ").append(fmt(x.qty)).append("\n");
        }
        if(!r.isEmpty()){
            s.append("------------------------------\nالشق الأيمن:\n");
            for(NoteItem x:r)s.append(x.name).append(" × ").append(fmt(x.qty)).append("\n");
        }
        s.append("------------------------------\n");
        return s.toString();
    }
    void shareCurrentNotes(){
        if(currentNotePageId>0){
            shareText(notesWhatsAppText());
        }else Toast.makeText(this,"لا توجد صفحة ملاحظات للمشاركة",Toast.LENGTH_SHORT).show();
    }
    void printCurrentNotes(){if(currentNotePageId>0)printTextBluetooth(notesReceiptText());else Toast.makeText(this,"لا توجد صفحة ملاحظات للطباعة",Toast.LENGTH_SHORT).show();}
    void purchaseInvoices(){
        base("فواتير الشراء");

        // شريط الإحصائيات السريع لفواتير الشراء
        int pTotalInvoices=0;
        double pTotalSum=0;
        Cursor sc=db.getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(total),0) FROM purchase_invoices",null);
        if(sc.moveToFirst()){pTotalInvoices=sc.getInt(0);pTotalSum=sc.getDouble(1);}
        sc.close();

        LinearLayout statsCard=new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.HORIZONTAL);
        statsCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        statsCard.setPadding(dp(12),dp(8),dp(12),dp(8));
        statsCard.setBackground(outlined(CARD,1,12));

        TextView cntTv=tv("🛒 عدد فواتير الشراء:\n"+pTotalInvoices+" فاتورة",11.5f);
        cntTv.setTextColor(GOLD); cntTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); cntTv.setGravity(Gravity.CENTER);
        statsCard.addView(cntTv,new LinearLayout.LayoutParams(0,-2,1));

        TextView sumTv=tv("💰 إجمالي المشتريات:\n"+fmt(pTotalSum)+" ريال",11.5f);
        sumTv.setTextColor(GOLD); sumTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); sumTv.setGravity(Gravity.CENTER);
        statsCard.addView(sumTv,new LinearLayout.LayoutParams(0,-2,1.2f));

        content.addView(statsCard,new LinearLayout.LayoutParams(-1,-2));
        addSpace(6);

        // زر عائم لإضافة فاتورة شراء جديدة — بنفس أسلوب زر فاتورة البيع
        if(root.getChildCount()>1){
            View sv=root.getChildAt(1);
            int svIdx=root.indexOfChild(sv);
            if(svIdx>=0){
                root.removeViewAt(svIdx);

                FrameLayout frame=new FrameLayout(this);
                frame.addView(sv,new FrameLayout.LayoutParams(-1,-1));

                Button fab=new Button(this);
                fab.setText("＋");
                fab.setTextSize(26);
                fab.setTextColor(Color.WHITE);
                fab.setGravity(Gravity.CENTER);
                fab.setIncludeFontPadding(false);

                GradientDrawable fabBg=new GradientDrawable();
                fabBg.setShape(GradientDrawable.OVAL);
                fabBg.setColor(GOLD);
                if(Build.VERSION.SDK_INT>=21){
                    fab.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.rgb(255,235,175)),fabBg,null));
                }else{
                    fab.setBackground(fabBg);
                }
                fab.setElevation(dp(8));
                fab.setContentDescription("إضافة فاتورة شراء جديدة");
                fab.setOnClickListener(v->newPurchaseInvoice());

                FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(54),dp(54));
                fp.gravity=Gravity.BOTTOM|Gravity.LEFT;
                fp.setMargins(dp(16),0,dp(16),dp(14));
                frame.addView(fab,fp);

                root.addView(frame,svIdx,new LinearLayout.LayoutParams(-1,0,1));
                content.setPadding(dp(5),dp(4),dp(5),dp(74));
            }
        }

        // حقل البحث
        EditText search=field("🔍 بحث برقم الفاتورة أو اسم المورد...");
        search.setPadding(dp(10),dp(4),dp(10),dp(4));
        content.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        addSpace(6);

        section("سجل فواتير الشراء");
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable renderList=()->{
            list.removeAllViews();
            String q=search.getText().toString().trim().toLowerCase();
            Cursor c=db.getReadableDatabase().rawQuery("SELECT id,no,supplier,total,date FROM purchase_invoices ORDER BY datetime(date) DESC,id DESC",null);
            int pCount=0;
            while(c.moveToNext()){
                long id=c.getLong(0);
                String no=c.getString(1);
                String supplier=c.getString(2);
                double total=c.getDouble(3);
                String date=c.getString(4);

                if(!q.isEmpty()){
                    boolean matchNo=no!=null && no.toLowerCase().contains(q);
                    boolean matchSup=supplier!=null && supplier.toLowerCase().contains(q);
                    if(!matchNo && !matchSup) continue;
                }
                pCount++;

                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10),dp(8),dp(10),dp(8));
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                GradientDrawable rBg=new GradientDrawable();
                rBg.setColor(CARD);
                rBg.setCornerRadius(dp(12));
                rBg.setStroke(dp(1),Color.rgb(240,225,185));
                row.setBackground(rBg);

                LinearLayout topR=new LinearLayout(this);
                topR.setOrientation(LinearLayout.HORIZONTAL);
                topR.setGravity(Gravity.CENTER_VERTICAL);
                topR.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView badge=tv("#"+no,11.5f);
                badge.setTextColor(GOLD); badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD); badge.setGravity(Gravity.CENTER);
                GradientDrawable bBg=new GradientDrawable();
                bBg.setColor(Color.rgb(255,250,235));
                bBg.setCornerRadius(dp(8));
                bBg.setStroke(dp(1),Color.rgb(240,220,175));
                badge.setBackground(bBg);
                topR.addView(badge,new LinearLayout.LayoutParams(dp(50),dp(28)));

                LinearLayout sCol=new LinearLayout(this);
                sCol.setOrientation(LinearLayout.VERTICAL);
                sCol.setPadding(dp(8),0,dp(8),0);

                TextView sTv=tv("المورد: "+(supplier==null||supplier.isEmpty()?"بدون مورد":supplier),13);
                sTv.setTextColor(TEXT); sTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                sCol.addView(sTv,new LinearLayout.LayoutParams(-1,dp(20)));

                TextView dTv=tv("📅 "+date,10);
                dTv.setTextColor(MUTED);
                sCol.addView(dTv,new LinearLayout.LayoutParams(-1,dp(16)));

                topR.addView(sCol,new LinearLayout.LayoutParams(0,-2,1));

                TextView totTv=tv(fmt(total)+" ر.ي",13.5f);
                totTv.setTextColor(GOLD); totTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                topR.addView(totTv,new LinearLayout.LayoutParams(-2,-2));

                row.addView(topR,new LinearLayout.LayoutParams(-1,-2));
                row.setOnClickListener(v->showPurchaseInvoiceDialog(id,no,supplier,total,date));

                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,0,0,dp(6));
                list.addView(row,lp);
            }
            c.close();

            if(pCount==0){
                LinearLayout emptyBox=card();
                emptyBox.setPadding(dp(16),dp(16),dp(16),dp(16));
                emptyBox.setGravity(Gravity.CENTER);
                TextView em=tv(q.isEmpty()?"🛒 لا توجد فواتير شراء مسجلة حتى الآن":"🔍 لا توجد نتائج مطابقة للبحث",12.5f);
                em.setTextColor(MUTED); em.setGravity(Gravity.CENTER);
                emptyBox.addView(em,new LinearLayout.LayoutParams(-1,dp(30)));
                list.addView(emptyBox,new LinearLayout.LayoutParams(-1,-2));
            }
        };

        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){renderList.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        renderList.run();
    }

    void newPurchaseInvoice(){
        purchaseInvoiceForm(false,0);
    }

    void purchaseInvoiceForm(boolean edit,long purchaseId){
        base(edit?"تعديل فاتورة الشراء":"فاتورة شراء جديدة",false);

        // شريط سفلي ثابت لفاتورة الشراء
        bottom.removeAllViews();
        LinearLayout pFooter=new LinearLayout(this);
        pFooter.setOrientation(LinearLayout.VERTICAL);
        pFooter.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pFooter.setPadding(dp(10),dp(5),dp(10),dp(6));
        GradientDrawable pfBg=new GradientDrawable();
        pfBg.setColor(CARD);
        pfBg.setStroke(dp(1),Color.rgb(240,225,185));
        pFooter.setBackground(pfBg);
        if(Build.VERSION.SDK_INT>=21) pFooter.setElevation(dp(8));

        LinearLayout pSumRow=new LinearLayout(this);
        pSumRow.setOrientation(LinearLayout.HORIZONTAL);
        pSumRow.setGravity(Gravity.CENTER_VERTICAL);
        pSumRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView pTotTv=tv("إجمالي المشتريات: 0 ريال",15);
        pTotTv.setTextColor(GOLD); pTotTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        pSumRow.addView(pTotTv,new LinearLayout.LayoutParams(0,-2,1.2f));

        TextView pCountTv=tv("0 أصناف",11.5f);
        pCountTv.setTextColor(MUTED); pCountTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        pCountTv.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        pSumRow.addView(pCountTv,new LinearLayout.LayoutParams(0,-2,0.8f));

        pFooter.addView(pSumRow,new LinearLayout.LayoutParams(-1,-2));
        spaceTo(pFooter,4);

        LinearLayout pButtons=new LinearLayout(this);
        pButtons.setOrientation(LinearLayout.HORIZONTAL);
        pButtons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button pSaveBtn=action(edit?"💾 حفظ تعديل الفاتورة":"💾 حفظ فاتورة الشراء",GOLD);
        pSaveBtn.setTextSize(13.5f);
        pButtons.addView(pSaveBtn,new LinearLayout.LayoutParams(0,dp(44),1.5f));

        Button pClearBtn=button("🧹 مسح الأصناف");
        pClearBtn.setTextColor(MUTED); pClearBtn.setBackground(outline(CARD,10));
        pClearBtn.setTextSize(11.5f);
        LinearLayout.LayoutParams pclp=new LinearLayout.LayoutParams(0,dp(44),0.8f); pclp.setMargins(dp(6),0,0,0);
        pButtons.addView(pClearBtn,pclp);

        pFooter.addView(pButtons,new LinearLayout.LayoutParams(-1,dp(46)));
        bottom.addView(pFooter,new LinearLayout.LayoutParams(-1,-2));

        section("بيانات فاتورة الشراء");

        LinearLayout metaCard=card();
        metaCard.setPadding(dp(8),dp(8),dp(8),dp(8));
        metaCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout meta=new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        AutoCompleteTextView supplier=new AutoCompleteTextView(this);
        supplier.setHint("اسم المورد"); supplier.setTextSize(13); supplier.setSingleLine(true);
        supplier.setTextColor(TEXT); supplier.setHintTextColor(MUTED);
        supplier.setPadding(dp(8),dp(3),dp(8),dp(3)); supplier.setBackground(outline(CARD,10));
        supplier.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        supplier.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); supplier.setTextDirection(View.TEXT_DIRECTION_RTL);
        supplier.setThreshold(1); supplier.setSelectAllOnFocus(true);
        supplier.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,db.supplierNames()));

        EditText invoiceNo=field("رقم فاتورة الشراء");
        invoiceNo.setText(edit?db.purchaseNo(purchaseId):String.valueOf(db.nextPurchaseNo())); invoiceNo.setTextSize(13);
        if(edit) supplier.setText(db.purchaseSupplier(purchaseId));

        meta.addView(supplier,new LinearLayout.LayoutParams(0,dp(52),1.35f));
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,dp(52),1f); nlp.setMargins(dp(6),0,0,0);
        meta.addView(invoiceNo,nlp);

        metaCard.addView(meta,new LinearLayout.LayoutParams(-1,dp(42)));

        TextView suppHint=tv("💡 اختر أو اكتب اسم المورد وسيتم حفظ بياناته وتحديثها تلقائياً",10f);
        suppHint.setTextColor(MUTED); suppHint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        suppHint.setPadding(dp(4),dp(2),dp(4),dp(2));
        metaCard.addView(suppHint,new LinearLayout.LayoutParams(-1,-2));

        content.addView(metaCard,new LinearLayout.LayoutParams(-1,-2));
        addSpace(6);

        Runnable updateSuppHint=()->{
            String sn=supplier.getText().toString().trim();
            if(sn.isEmpty()){
                suppHint.setText("💡 اختر أو اكتب اسم المورد وسيتم حفظ بياناته وتحديثها تلقائياً");
                suppHint.setTextColor(MUTED);
            }else{
                suppHint.setText("💡 المورد: "+sn+" • سيتم تسجيل الفاتورة تحت حسابه بانتظام");
                suppHint.setTextColor(GOLD);
            }
        };
        supplier.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){updateSuppHint.run();}
            public void afterTextChanged(Editable s){}
        });

        section("إدخال الصنف والتلميحات الذكية");
        LinearLayout entry=card();
        entry.setPadding(dp(8),dp(8),dp(8),dp(8));
        entry.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        EditText total=numberField("القيمة الإجمالية");
        EditText qty=numberField("الكمية"); qty.setText("1");
        AutoCompleteTextView item=new AutoCompleteTextView(this);
        item.setHint("اسم الصنف"); item.setTextSize(13); item.setSingleLine(true);
        item.setTextColor(TEXT); item.setHintTextColor(MUTED);
        item.setPadding(dp(6),dp(2),dp(6),dp(2)); item.setBackground(outline(CARD,10));
        item.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); item.setTextDirection(View.TEXT_DIRECTION_RTL);
        item.setSelectAllOnFocus(true); item.setThreshold(1);
        item.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,db.itemNames()));

        EditText unit=numberField("سعر الوحدة");
        unit.setTextColor(TEXT); unit.setBackground(outline(CARD,10));

        EditText sale=numberField("سعر البيع");

        TextView itemHint=tv("💡 اكتب اسم الصنف وسيتم ملء أسعار الشراء والبيع والمخزون أوتوماتيكياً",10f);
        itemHint.setTextColor(MUTED); itemHint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);

        Runnable updateItemHint=()->{
            String iname=item.getText().toString().trim();
            if(iname.isEmpty()){
                itemHint.setText("💡 اكتب اسم الصنف وسيتم ملء أسعار الشراء والبيع والمخزون أوتوماتيكياً");
                itemHint.setTextColor(MUTED);
                return;
            }
            double costP=db.itemCostPrice(iname);
            double saleP=db.itemSalePrice(iname);
            double st=db.itemQty(iname);

            if(costP>0 && unit.getText().toString().trim().isEmpty()){
                unit.setText(fmt(costP));
                double q=1; try{q=Double.parseDouble(qty.getText().toString().trim());}catch(Exception ignored){}
                if(q<=0)q=1;
                total.setText(fmt(costP*q));
            }
            if(saleP>0 && sale.getText().toString().trim().isEmpty()){
                sale.setText(fmt(saleP));
            }

            if(costP>0 || saleP>0 || st>0){
                itemHint.setText("💡 الصنف: "+iname+" • آخر تكلفة شراء: "+fmt(costP)+" ر.ي • سعر البيع الحالي: "+fmt(saleP)+" ر.ي • بالمخزون: "+fmt(st)+" حبة");
                itemHint.setTextColor(GOLD);
            }else{
                itemHint.setText("💡 صنف جديد: سيتم حفظه وتحديث تكلفته وسعر بيعه أوتوماتيكياً بالمخزن");
                itemHint.setTextColor(BLUE);
            }
        };

        item.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){updateItemHint.run();}
            public void afterTextChanged(Editable s){}
        });

        item.setOnItemClickListener((parent,view,pos,id)->{
            String selectedName=(String)parent.getItemAtPosition(pos);
            double costP=db.itemCostPrice(selectedName);
            double saleP=db.itemSalePrice(selectedName);
            if(costP>0 && unit.getText().toString().trim().isEmpty()){
                unit.setText(fmt(costP));
                double q=1;
                try{q=Double.parseDouble(qty.getText().toString().trim());}catch(Exception ignored){}
                if(q<=0)q=1;
                total.setText(fmt(costP*q));
            }
            if(saleP>0 && sale.getText().toString().trim().isEmpty()){
                sale.setText(fmt(saleP));
            }
            updateItemHint.run();
        });

        fields.addView(total,new LinearLayout.LayoutParams(0,dp(50),1.0f));
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(0,dp(50),0.72f); qlp.setMargins(dp(3),0,0,0);
        fields.addView(qty,qlp);
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,dp(50),1.25f); ilp.setMargins(dp(3),0,0,0);
        fields.addView(item,ilp);
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(0,dp(50),0.9f); ulp.setMargins(dp(3),0,0,0);
        fields.addView(unit,ulp);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(50),0.9f); slp.setMargins(dp(3),0,0,0);
        fields.addView(sale,slp);

        entry.addView(fields,new LinearLayout.LayoutParams(-1,dp(52)));
        spaceTo(entry,4);

        entry.addView(itemHint,new LinearLayout.LayoutParams(-1,dp(22)));
        spaceTo(entry,4);

        Button add=action("＋ إضافة الصنف إلى صندوق الفاتورة",GOLD);
        add.setTextSize(12.5f);
        entry.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));

        content.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        addSpace(6);

        section("صندوق الفاتورة");
        LinearLayout box=card();
        box.setPadding(dp(6),dp(6),dp(6),dp(8));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String[] heads={"القيمة الإجمالية","الكمية","اسم الصنف","سعر الوحدة","سعر البيع","حذف"};
        float[] w={1.0f,.72f,1.25f,.9f,.9f,.55f};

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setBackground(outlined(Color.rgb(255,250,240),1,8));

        for(int i=0;i<heads.length;i++){
            TextView h=tv(heads[i],8.5f);
            h.setTextColor(GOLD); h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            h.setGravity(Gravity.CENTER); h.setMaxLines(2);
            head.addView(h,new LinearLayout.LayoutParams(0,dp(30),w[i]));
        }
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(32)));
        spaceTo(box,4);

        LinearLayout rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.addView(rows);
        spaceTo(box,4);

        TextView grand=tv("إجمالي فاتورة الشراء: 0 ريال",16);
        grand.setTextColor(GOLD); grand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        grand.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        grand.setPadding(dp(10),dp(4),dp(10),dp(4));
        GradientDrawable gBg=new GradientDrawable();
        gBg.setColor(Color.rgb(255,249,235));
        gBg.setCornerRadius(dp(10));
        gBg.setStroke(dp(1),Color.rgb(245,225,185));
        grand.setBackground(gBg);
        box.addView(grand,new LinearLayout.LayoutParams(-1,dp(44)));

        content.addView(box,new LinearLayout.LayoutParams(-1,-2));
        addSpace(6);

        ArrayList<PurchaseLine> lines=new ArrayList<>();
        if(edit && purchaseId>0) lines.addAll(loadPurchaseLines(purchaseId));

        final Runnable[] redraw={null};
        redraw[0]=()->{
            rows.removeAllViews();
            double sum=0;
            for(PurchaseLine l:lines){
                sum+=l.total;
                LinearLayout r=new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                r.setGravity(Gravity.CENTER_VERTICAL);

                String[] vals={fmt(l.total),fmt(l.qty),l.name,fmt(l.cost),fmt(l.sale)};
                for(int i=0;i<5;i++){
                    TextView v=tv(vals[i],8.5f);
                    v.setGravity(i==2?Gravity.RIGHT|Gravity.CENTER_VERTICAL:Gravity.CENTER);
                    v.setMaxLines(4); v.setEllipsize(null);
                    v.setBackground(outline(Color.rgb(248,250,248),6));
                    r.addView(v,new LinearLayout.LayoutParams(0,dp(32),w[i]));
                }
                Button del=button("✕");
                del.setTextSize(11); del.setTextColor(Color.RED); del.setBackgroundColor(Color.TRANSPARENT);
                del.setOnClickListener(v->{lines.remove(l); redraw[0].run();});
                r.addView(del,new LinearLayout.LayoutParams(0,dp(32),w[5]));

                rows.addView(r,new LinearLayout.LayoutParams(-1,dp(46)));
                spaceTo(rows,2);
            }
            grand.setText("إجمالي فاتورة الشراء: "+fmt(sum)+" ريال");
            pTotTv.setText("إجمالي المشتريات: "+fmt(sum)+" ريال");
            pCountTv.setText(lines.size()+" صنف");
        };

        final boolean[] updatingTotal={false};
        final boolean[] updatingUnit={false};

        Runnable calcFromTotal=()->{
            if(updatingUnit[0]) return;
            updatingTotal[0]=true;
            try{
                double t=Double.parseDouble(total.getText().toString().trim());
                double q=Double.parseDouble(qty.getText().toString().trim());
                if(q>0) unit.setText(fmt(t/q));
            }catch(Exception e){unit.setText("");}
            updatingTotal[0]=false;
        };

        Runnable calcFromUnit=()->{
            if(updatingTotal[0]) return;
            updatingUnit[0]=true;
            try{
                double u=Double.parseDouble(unit.getText().toString().trim());
                double q=Double.parseDouble(qty.getText().toString().trim());
                if(q>0) total.setText(fmt(u*q));
            }catch(Exception e){total.setText("");}
            updatingUnit[0]=false;
        };

        total.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){calcFromTotal.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        unit.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){calcFromUnit.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        qty.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){
                if(!unit.getText().toString().trim().isEmpty()){
                    calcFromUnit.run();
                }else{
                    calcFromTotal.run();
                }
            }
            public void afterTextChanged(android.text.Editable e){}
        });

        add.setOnClickListener(v->{
            try{
                double t=0;
                double q=Double.parseDouble(qty.getText().toString().trim());
                double s=Double.parseDouble(sale.getText().toString().trim());
                String n=item.getText().toString().trim();
                if(n.isEmpty()||q<=0||s<0)throw new Exception();

                if(!total.getText().toString().trim().isEmpty()){
                    t=Double.parseDouble(total.getText().toString().trim());
                }else if(!unit.getText().toString().trim().isEmpty()){
                    double u=Double.parseDouble(unit.getText().toString().trim());
                    t=u*q;
                }else throw new Exception();

                if(t<0)throw new Exception();
                lines.add(new PurchaseLine(n,q,t/q,s,t));
                db.learnItemPrice(n, s, t/q);
                redraw[0].run();
                total.setText(""); qty.setText("1"); item.setText(""); sale.setText(""); unit.setText(""); total.requestFocus();
            }catch(Exception e){
                Toast.makeText(this,"أدخل القيمة الإجمالية أو سعر الوحدة والكمية واسم الصنف وسعر البيع بشكل صحيح",Toast.LENGTH_SHORT).show();
            }
        });

        pSaveBtn.setOnClickListener(v->{
            SQLiteDatabase ptx=null;
            long savedPurchaseId=0;
            boolean saved=false;
            String savedNo="", savedSupplier="";
            double savedSum=0;
            try{
                String sn=supplier.getText().toString().trim(), no=invoiceNo.getText().toString().trim();
                if(sn.isEmpty()||no.isEmpty()||lines.isEmpty())throw new Exception();
                double sum=0; for(PurchaseLine l:lines){ if(l.qty<=0||l.cost<0||l.sale<0||l.total<0)throw new Exception(); sum+=l.total; }
                ptx=db.getWritableDatabase();
                ptx.beginTransaction();
                db.supplier(sn,"");
                if(edit && purchaseId>0){
                    db.updatePurchase(purchaseId,no,sn,sum);
                    db.revertStockFromPurchase(purchaseId);
                    db.replacePurchaseLines(purchaseId,lines);
                    db.updateStockFromPurchase(lines);
                    savedPurchaseId=purchaseId;
                }else{
                    long pid=db.addPurchase(no,sn,sum,db.now());
                    if(pid<=0)throw new Exception("تعذر حفظ الفاتورة");
                    db.replacePurchaseLines(pid,lines);
                    db.updateStockFromPurchase(lines);
                    savedPurchaseId=pid;
                }
                ptx.setTransactionSuccessful();
                saved=true;
                savedNo=no; savedSupplier=sn; savedSum=sum;
            }catch(Exception e){
                Toast.makeText(this,"لم يتم اعتماد فاتورة الشراء. تحقق من البيانات وحاول مرة أخرى.",Toast.LENGTH_LONG).show();
            }finally{
                if(ptx!=null)ptx.endTransaction();
            }
            if(saved){
                Toast.makeText(this,edit?"تم حفظ تعديل فاتورة الشراء وتحديث المخزون":"تم حفظ فاتورة الشراء وتحديث المخزون",Toast.LENGTH_SHORT).show();
                showPostSavePurchaseActions(savedPurchaseId,savedNo,savedSupplier,lines,savedSum,db.now());
            }
        });
        pClearBtn.setOnClickListener(v->{lines.clear(); redraw[0].run();});
        content.setPadding(dp(6),dp(4),dp(6),dp(22));

        redraw[0].run();
    }

    static class PurchaseLine{String name;double qty,cost,sale,total;PurchaseLine(String n,double q,double c,double s,double t){name=n;qty=q;cost=c;sale=s;total=t;}}

    void reports(){
        base("التقارير المالية المفسّلة");
        section("مركز التقارير، ملخص الأداء، كشف العمليات وحركة الصندوق");

        try{
            final String todayDate=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
            final String yesterdayDate=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(System.currentTimeMillis()-86400000L));
            final String monthPrefix=new SimpleDateFormat("yyyy-MM",Locale.US).format(new Date());

            final int PERIOD_ALL=0, PERIOD_TODAY=1, PERIOD_YESTERDAY=2, PERIOD_MONTH=3;
            final int[] currentPeriod={PERIOD_ALL};

            final int FILTER_ALL=0, FILTER_SALES=1, FILTER_PURCHASES=2, FILTER_OPS=3;
            final int[] currentFilter={FILTER_ALL};

            // Period Filter Bar
            LinearLayout periodBar=new LinearLayout(this);
            periodBar.setOrientation(LinearLayout.HORIZONTAL);
            periodBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            Button pAll=button("🗂️ الكل");
            Button pToday=button("☀️ اليوم");
            Button pYesterday=button("🌙 الأمس");
            Button pMonth=button("🗓️ هذا الشهر");
            pAll.setTextSize(11f); pToday.setTextSize(11f); pYesterday.setTextSize(11f); pMonth.setTextSize(11f);

            periodBar.addView(pAll,new LinearLayout.LayoutParams(0,dp(48),1));
            LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(0,dp(48),1); plp.setMargins(dp(3),0,0,0);
            periodBar.addView(pToday,plp);
            periodBar.addView(pYesterday,plp);
            periodBar.addView(pMonth,plp);
            content.addView(periodBar,new LinearLayout.LayoutParams(-1,dp(50)));
            addSpace(6);

            // Live Search Box
            EditText searchInput=field("🔍 بحث حسب اسم العميل، المورد، الصنف، أو رقم الفاتورة...");
            searchInput.setTextSize(12f); searchInput.setSingleLine(true);
            searchInput.setBackground(outline(CARD,10));
            searchInput.setPadding(dp(10),dp(4),dp(10),dp(4));
            content.addView(searchInput,new LinearLayout.LayoutParams(-1,dp(50)));
            addSpace(6);

            // Summary Cards Grid (Dynamic Stats for current active filter)
            LinearLayout statGrid=new LinearLayout(this);
            statGrid.setOrientation(LinearLayout.VERTICAL);
            statGrid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            LinearLayout statRow1=new LinearLayout(this);
            statRow1.setOrientation(LinearLayout.HORIZONTAL);
            statRow1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            // Card 1: Sales
            LinearLayout cSales=card(); cSales.setOrientation(LinearLayout.VERTICAL); cSales.setGravity(Gravity.CENTER); cSales.setPadding(dp(6),dp(6),dp(6),dp(6));
            TextView cSt=tv("💰 المبيعات",10f); cSt.setTextColor(MUTED); cSt.setGravity(Gravity.CENTER);
            TextView cSv=tv("0 ر.ي",12.5f); cSv.setTextColor(GREEN); cSv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); cSv.setGravity(Gravity.CENTER);
            cSales.addView(cSt,new LinearLayout.LayoutParams(-1,-2)); cSales.addView(cSv,new LinearLayout.LayoutParams(-1,-2));

            // Card 2: Purchases
            LinearLayout cPurch=card(); cPurch.setOrientation(LinearLayout.VERTICAL); cPurch.setGravity(Gravity.CENTER); cPurch.setPadding(dp(6),dp(6),dp(6),dp(6));
            TextView cPt=tv("🛒 المشتريات",10f); cPt.setTextColor(MUTED); cPt.setGravity(Gravity.CENTER);
            TextView cPv=tv("0 ر.ي",12.5f); cPv.setTextColor(GOLD); cPv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); cPv.setGravity(Gravity.CENTER);
            cPurch.addView(cPt,new LinearLayout.LayoutParams(-1,-2)); cPurch.addView(cPv,new LinearLayout.LayoutParams(-1,-2));

            // Card 3: Estimated Net Profit
            LinearLayout cProfit=card(); cProfit.setOrientation(LinearLayout.VERTICAL); cProfit.setGravity(Gravity.CENTER); cProfit.setPadding(dp(6),dp(6),dp(6),dp(6));
            TextView cProftT=tv("📈 الربح الإجمالي التقديري",10f); cProftT.setTextColor(MUTED); cProftT.setGravity(Gravity.CENTER);
            TextView cProftV=tv("0 ر.ي",12.5f); cProftV.setTextColor(BLUE); cProftV.setTypeface(Typeface.DEFAULT,Typeface.BOLD); cProftV.setGravity(Gravity.CENTER);
            cProfit.addView(cProftT,new LinearLayout.LayoutParams(-1,-2)); cProfit.addView(cProftV,new LinearLayout.LayoutParams(-1,-2));
            statRow1.addView(cSales,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams slp1=new LinearLayout.LayoutParams(0,-2,1); slp1.setMargins(dp(4),0,0,0);
            statRow1.addView(cPurch,slp1);
            LinearLayout.LayoutParams slp2=new LinearLayout.LayoutParams(0,-2,1); slp2.setMargins(dp(4),0,0,0);
            statRow1.addView(cProfit,slp2);

            statGrid.addView(statRow1,new LinearLayout.LayoutParams(-1,-2));
            content.addView(statGrid,new LinearLayout.LayoutParams(-1,-2));
            addSpace(8);

            // Category Filter Tabs Bar
            section("نوع الحركة والعملية");
            LinearLayout filterBar=new LinearLayout(this);
            filterBar.setOrientation(LinearLayout.HORIZONTAL);
            filterBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            Button fAll=button("🗂️ الكل");
            Button fSales=button("🧾 المبيعات");
            Button fPurchases=button("🛒 المشتريات");
            Button fOps=button("💵 الحركات");
            fAll.setTextSize(11f); fSales.setTextSize(11f); fPurchases.setTextSize(11f); fOps.setTextSize(11f);

            filterBar.addView(fAll,new LinearLayout.LayoutParams(0,dp(48),1));
            LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(0,dp(48),1); flp.setMargins(dp(3),0,0,0);
            filterBar.addView(fSales,flp);
            filterBar.addView(fPurchases,flp);
            filterBar.addView(fOps,flp);
            content.addView(filterBar,new LinearLayout.LayoutParams(-1,dp(50)));
            addSpace(8);

            LinearLayout reportsListContainer=new LinearLayout(this);
            reportsListContainer.setOrientation(LinearLayout.VERTICAL);
            reportsListContainer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            Runnable[] updatePeriodStyle=new Runnable[1];
            Runnable[] updateCategoryStyle=new Runnable[1];

            Runnable renderReportsList=()->{
                reportsListContainer.removeAllViews();
                try{
                    String q=searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
                    Cursor c=db.recentActivity();
                    int actCount=0;

                    double periodSalesTotal=0;
                    double periodPurchasesTotal=0;
                    double periodCogsTotal=0;

                    while(c.moveToNext()){
                        int kind=c.getInt(0); // 1: sales invoice, 2: transaction, 3: purchase invoice
                        String ref=c.getString(1);
                        String title=c.getString(2);
                        double amount=c.getDouble(3);
                        String date=c.getString(4);
                        long sortId=c.getLong(5);
                        int operationType=c.getInt(6);

                        String fr=ref==null?"":ref;
                        String ft=title==null?"":title;
                        String fd=date==null?"":date;

                        // Date period filter check
                        if(currentPeriod[0]==PERIOD_TODAY && !fd.startsWith(todayDate)) continue;
                        if(currentPeriod[0]==PERIOD_YESTERDAY && !fd.startsWith(yesterdayDate)) continue;
                        if(currentPeriod[0]==PERIOD_MONTH && !fd.startsWith(monthPrefix)) continue;

                        // Search text query check
                        if(!q.isEmpty()){
                            String searchContent=(fr+" "+ft+" "+fd).toLowerCase(Locale.ROOT);
                            if(kind==1) searchContent+=" "+db.invoiceCompactDetails(fr).toLowerCase(Locale.ROOT);
                            if(!searchContent.contains(q)) continue;
                        }

                        // Category filter check
                        if(currentFilter[0]==FILTER_SALES && kind!=1) continue;
                        if(currentFilter[0]==FILTER_PURCHASES && kind!=3) continue;
                        if(currentFilter[0]==FILTER_OPS && kind!=2) continue;

                        if(kind==1){
                            periodSalesTotal+=amount;
                            long reportInvoiceId=db.invoiceIdByNo(fr);
                            if(reportInvoiceId>0){
                                Cursor costCursor=db.getReadableDatabase().rawQuery("SELECT COALESCE(qty,0),COALESCE(unit_cost,0) FROM invoice_items WHERE invoice_id=?",new String[]{String.valueOf(reportInvoiceId)});
                                while(costCursor.moveToNext()) periodCogsTotal += costCursor.getDouble(0)*costCursor.getDouble(1);
                                costCursor.close();
                            }
                        }
                        if(kind==3) periodPurchasesTotal+=amount;

                        actCount++;

                        final int fk=kind;
                        final String ffr=fr;
                        final String fft=ft;
                        final double fa=amount;
                        final String ffd=fd;
                        final long fid=sortId;

                        int activityColor=(kind==3)?GOLD:((kind==2 && operationType==1)?RED:BLUE);

                        LinearLayout row=card();
                        row.setPadding(dp(10),dp(8),dp(10),dp(8));
                        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                        GradientDrawable rBg=new GradientDrawable();
                        rBg.setColor(CARD);
                        rBg.setCornerRadius(dp(12));
                        rBg.setStroke(dp(1),kind==1?Color.rgb(205,235,215):(kind==3?Color.rgb(245,225,185):(operationType==1?Color.rgb(250,215,215):Color.rgb(215,230,250))));
                        row.setBackground(rBg);

                        String label=kind==1?"🧾 فاتورة مبيعات":(kind==3?"🛒 فاتورة شراء":(operationType==1?"🔴 عليه (مدين)":"🔵 له (دائن)"));
                        
                        String paymentBadge="";
                        String lineItemsPreview="";
                        if(kind==1){
                            long invId=db.invoiceIdByNo(ffr);
                            if(invId>0){
                                double tot=db.invoiceTotal(invId);
                                double paid=db.invoicePaid(invId);
                                double rem=tot-paid;
                                if(rem<=0.005) paymentBadge=" [نقدي مسدد]";
                                else if(paid<=0.005) paymentBadge=" [آجل - مدين]";
                                else paymentBadge=" [مسدد جزئياً: "+fmt(paid)+"]";
                            }
                            lineItemsPreview=db.invoiceCompactDetails(ffr);
                        }

                        LinearLayout topRow=new LinearLayout(this);
                        topRow.setOrientation(LinearLayout.HORIZONTAL);
                        topRow.setGravity(Gravity.CENTER_VERTICAL);
                        topRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                        TextView main=tv(label+" • "+fft+paymentBadge,12f);
                        main.setTextColor(kind==1?GREEN:activityColor);
                        main.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                        main.setMaxLines(2);
                        topRow.addView(main,new LinearLayout.LayoutParams(0,-2,1));

                        TextView valTv=tv(fmt(fa)+" ر.ي",12.5f);
                        valTv.setTextColor(kind==1?GREEN:activityColor);
                        valTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                        topRow.addView(valTv,new LinearLayout.LayoutParams(-2,-2));

                        row.addView(topRow,new LinearLayout.LayoutParams(-1,-2));

                        if(!lineItemsPreview.isEmpty()){
                            spaceTo(row,2);
                            TextView itemPrev=tv(lineItemsPreview,10f);
                            itemPrev.setTextColor(MUTED); itemPrev.setMaxLines(4);
                            row.addView(itemPrev,new LinearLayout.LayoutParams(-1,-2));
                        }

                        spaceTo(row,3);

                        TextView meta=tv("📅 "+ffd,10);
                        meta.setTextColor(MUTED);
                        row.addView(meta,new LinearLayout.LayoutParams(-1,-2));

                        row.setOnClickListener(v->{
                            if(fk==3){
                                showPurchaseInvoiceDialog(fid,ffr,db.purchaseSupplier(fid),fa,ffd);
                            }else showReportActivityDetails(fk,ffr,fft,fa,ffd,fid);
                        });

                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                        lp.setMargins(0,0,0,dp(6));
                        reportsListContainer.addView(row,lp);
                    }
                    c.close();

                    // Update summary stat values
                    cSv.setText(fmt(periodSalesTotal)+" ر.ي");
                    cPv.setText(fmt(periodPurchasesTotal)+" ر.ي");
                    double estProfit=periodSalesTotal-periodCogsTotal;
                    cProftV.setText(fmt(estProfit)+" ر.ي");
                    cProftV.setTextColor(estProfit>=0?BLUE:RED);

                    if(actCount==0){
                        LinearLayout emptyBox=card();
                        emptyBox.setPadding(dp(16),dp(16),dp(16),dp(16));
                        emptyBox.setGravity(Gravity.CENTER);
                        TextView em=tv("📊 لا توجد حركات مطابقة لهذا الفلتر أو البحث",12.5f);
                        em.setTextColor(MUTED); em.setGravity(Gravity.CENTER);
                        emptyBox.addView(em,new LinearLayout.LayoutParams(-1,dp(30)));
                        reportsListContainer.addView(emptyBox,new LinearLayout.LayoutParams(-1,-2));
                    }
                }catch(Exception e){
                    TextView err=tv("تعذر تحميل الحركات.",11);
                    err.setTextColor(Color.rgb(170,75,35));
                    reportsListContainer.addView(err,new LinearLayout.LayoutParams(-1,dp(52)));
                }
            };

            updatePeriodStyle[0]=()->{
                pAll.setTextColor(currentPeriod[0]==PERIOD_ALL?Color.WHITE:TEXT);
                pAll.setBackground(currentPeriod[0]==PERIOD_ALL?rounded(GREEN,dp(8)):outline(CARD,8));

                pToday.setTextColor(currentPeriod[0]==PERIOD_TODAY?Color.WHITE:TEXT);
                pToday.setBackground(currentPeriod[0]==PERIOD_TODAY?rounded(GREEN,dp(8)):outline(CARD,8));

                pYesterday.setTextColor(currentPeriod[0]==PERIOD_YESTERDAY?Color.WHITE:TEXT);
                pYesterday.setBackground(currentPeriod[0]==PERIOD_YESTERDAY?rounded(GOLD,dp(8)):outline(CARD,8));

                pMonth.setTextColor(currentPeriod[0]==PERIOD_MONTH?Color.WHITE:TEXT);
                pMonth.setBackground(currentPeriod[0]==PERIOD_MONTH?rounded(BLUE,dp(8)):outline(CARD,8));

                renderReportsList.run();
            };

            updateCategoryStyle[0]=()->{
                fAll.setTextColor(currentFilter[0]==FILTER_ALL?Color.WHITE:TEXT);
                fAll.setBackground(currentFilter[0]==FILTER_ALL?rounded(GREEN,dp(8)):outline(CARD,8));

                fSales.setTextColor(currentFilter[0]==FILTER_SALES?Color.WHITE:TEXT);
                fSales.setBackground(currentFilter[0]==FILTER_SALES?rounded(GREEN,dp(8)):outline(CARD,8));

                fPurchases.setTextColor(currentFilter[0]==FILTER_PURCHASES?Color.WHITE:GOLD);
                fPurchases.setBackground(currentFilter[0]==FILTER_PURCHASES?rounded(GOLD,dp(8)):outline(CARD,8));

                fOps.setTextColor(currentFilter[0]==FILTER_OPS?Color.WHITE:TEXT);
                fOps.setBackground(currentFilter[0]==FILTER_OPS?rounded(BLUE,dp(8)):outline(CARD,8));

                renderReportsList.run();
            };

            pAll.setOnClickListener(v->{currentPeriod[0]=PERIOD_ALL; updatePeriodStyle[0].run();});
            pToday.setOnClickListener(v->{currentPeriod[0]=PERIOD_TODAY; updatePeriodStyle[0].run();});
            pYesterday.setOnClickListener(v->{currentPeriod[0]=PERIOD_YESTERDAY; updatePeriodStyle[0].run();});
            pMonth.setOnClickListener(v->{currentPeriod[0]=PERIOD_MONTH; updatePeriodStyle[0].run();});

            fAll.setOnClickListener(v->{currentFilter[0]=FILTER_ALL; updateCategoryStyle[0].run();});
            fSales.setOnClickListener(v->{currentFilter[0]=FILTER_SALES; updateCategoryStyle[0].run();});
            fPurchases.setOnClickListener(v->{currentFilter[0]=FILTER_PURCHASES; updateCategoryStyle[0].run();});
            fOps.setOnClickListener(v->{currentFilter[0]=FILTER_OPS; updateCategoryStyle[0].run();});

            searchInput.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s, int start, int count, int after){}
                public void onTextChanged(CharSequence s, int start, int before, int count){renderReportsList.run();}
                public void afterTextChanged(Editable s){}
            });

            content.addView(reportsListContainer,new LinearLayout.LayoutParams(-1,-2));

            updatePeriodStyle[0].run();
            updateCategoryStyle[0].run();

        }catch(Exception e){
            TextView err=tv("تعذر تحميل التقارير المالية.",11);
            err.setTextColor(Color.rgb(170,75,35));
            content.addView(err,new LinearLayout.LayoutParams(-1,dp(44)));
        }
    }

    // فواتير الشراء تُعرض في التقارير بنفس أسلوب فواتير البيع.
    void showReportActivityDetails(int kind,String ref,String title,double amount,String date,long sortId){
        try{
            LinearLayout box=new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(8),dp(4),dp(8),dp(4));

            if(kind==1){
                long invoiceId=db.invoiceIdByNo(ref);
                if(invoiceId>0){
                    String customer=db.invoiceCustomer(invoiceId);
                    double total=db.invoiceTotal(invoiceId);
                    double paid=db.invoicePaid(invoiceId);
                    String invoiceDate=db.invoiceDate(invoiceId);
                    long cid=customer==null||customer.trim().isEmpty()?-1:db.customerIdByName(customer.trim());
                    long tid=db.transactionIdForInvoice(ref);
                    double balanceAfter=tid>0?db.balanceAfterTransaction(tid):(cid>0?db.balance(cid):0);

                    TextView head=tv("فاتورة رقم "+ref,16);
                    head.setTextColor(GREEN);head.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    head.setGravity(Gravity.CENTER);
                    box.addView(head,new LinearLayout.LayoutParams(-1,dp(46)));

                    box.addView(detailLine("العميل",customer==null||customer.isEmpty()?"نقدي":customer));
                    box.addView(detailLine("التاريخ والوقت",invoiceDate==null||invoiceDate.isEmpty()?date:invoiceDate));
                    box.addView(detailLine("المبلغ الإجمالي",fmt(total)+" ريال"));
                    box.addView(detailLine("المبلغ المدفوع",fmt(paid)+" ريال"));
                    box.addView(detailLine("المتبقي",fmt(Math.max(0,total-paid))+" ريال"));
                    box.addView(detailLine("الرصيد بعد العملية",balanceText(balanceAfter)));

                    sectionInside(box,"أصناف الفاتورة");
                    Cursor lines=db.invoiceLines(invoiceId);
                    int count=0;
                    while(lines.moveToNext()){
                        String n=lines.getString(1);
                        double q=lines.getDouble(2),t=lines.getDouble(3);
                        TextView lr=tv(n+"   × "+fmt(q)+"   = "+fmt(t)+" ريال",11);
                        lr.setBackground(outline(Color.rgb(248,250,248),7));
                        lr.setMaxLines(2);lr.setEllipsize(null);
                        box.addView(lr,new LinearLayout.LayoutParams(-1,dp(30)));
                        spaceInside(box,2);count++;
                    }
                    lines.close();
                    if(count==0) box.addView(detailLine("الأصناف","لا توجد تفاصيل محفوظة"));

                    new AlertDialog.Builder(this).setTitle("تفاصيل الفاتورة").setView(box)
                        .setPositiveButton("إغلاق",null).show();
                    return;
                }
            }

            long tid=kind==2?sortId:-1;
            if(tid>0){
                Cursor tc=db.transactionById(tid);
                if(tc.moveToFirst()){
                    long customerId=tc.getLong(1);
                    String tdate=tc.getString(2);
                    String details=tc.getString(3);
                    double ta=tc.getDouble(4);
                    int type=tc.getInt(5);

                    String customerName=db.customerNameById(customerId);
                    double after=db.balanceAfterTransaction(tid);

                    TextView head=tv("تفاصيل العملية",15);
                    head.setTextColor(GREEN);head.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    head.setGravity(Gravity.CENTER);
                    box.addView(head,new LinearLayout.LayoutParams(-1,dp(32)));
                    box.addView(detailLine("من الحساب",type==1?"المحل / المبيعات":"العميل"));
                    box.addView(detailLine("إلى الحساب",type==1?(customerName==null?"العميل":customerName):"المحل / الدفعات"));
                    box.addView(detailLine("العميل",customerName==null?"":customerName));
                    box.addView(detailLine("نوع العملية",type==1?"عليه":"له / دفعة"));
                    box.addView(detailLine("التفاصيل",details==null||details.trim().isEmpty()?"عملية مالية":details));
                    box.addView(detailLine("المبلغ",fmt(ta)+" ريال"));
                    box.addView(detailLine("التاريخ والوقت",tdate==null?"":tdate));
                    box.addView(detailLine("الرصيد بعد العملية",balanceText(after)));

                    tc.close();
                    new AlertDialog.Builder(this).setTitle("بيانات العملية كاملة").setView(box)
                        .setPositiveButton("إغلاق",null).show();
                    return;
                }
                tc.close();
            }

            new AlertDialog.Builder(this).setTitle("بيانات الحركة")
                .setMessage(title+"\nالمبلغ: "+fmt(amount)+" ريال\nالتاريخ والوقت: "+date)
                .setPositiveButton("إغلاق",null).show();
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("بيانات الحركة")
                .setMessage("تعذر عرض كل تفاصيل هذه الحركة.")
                .setPositiveButton("إغلاق",null).show();
        }
    }

    TextView detailLine(String label,String value){
        TextView v=tv(label+": "+(value==null?"":value),11);
        v.setBackground(outline(Color.rgb(248,250,248),7));
        v.setMaxLines(3);v.setEllipsize(null);
        v.setPadding(dp(4),dp(1),dp(4),dp(1));
        return v;
    }

    // ==========================================
    // ماسح الفواتير والمستندات الذكي (CamScanner)
    // ==========================================
    File getInvoicesDownloadsDir(){
        return AppStorage.getInvoicesPhotosDir();
    }

    File getInvoicesImagesDir(){
        return AppStorage.getInternalInvoicesDir(this);
    }

    void launchScanCamera(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.CAMERA},REQ_PERM_CAMERA);
            return;
        }
        try{
            File tempFile=new File(getInvoicesImagesDir(),"cam_temp_"+System.currentTimeMillis()+".jpg");
            cameraScanTempUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",tempFile);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,cameraScanTempUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i,REQ_CAMERA_SCAN);
        }catch(Exception e){
            Toast.makeText(this,"تعذر تشغيل الكاميرا: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    void launchScanGallery(){
        try{
            Intent i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i,"اختر صورة الفاتورة"),REQ_GALLERY_SCAN);
        }catch(Exception e){
            Toast.makeText(this,"تعذر فتح المعرض: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    void handleScanCameraResult(Intent data){
        try{
            Bitmap bmp=null;
            if(cameraScanTempUri!=null){
                try(InputStream is=getContentResolver().openInputStream(cameraScanTempUri)){
                    bmp=BitmapFactory.decodeStream(is);
                }
            }
            if(bmp==null&&data!=null&&data.getExtras()!=null){
                bmp=(Bitmap)data.getExtras().get("data");
            }
            if(bmp!=null){
                onImageCapturedForScan(bmp);
            }else{
                Toast.makeText(this,"لم يتم التقاط الصورة بنجاح",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){
            Toast.makeText(this,"خطأ أثناء قراءة الصورة: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    void handleScanGalleryResult(Uri uri){
        try{
            Bitmap bmp=null;
            try(InputStream is=getContentResolver().openInputStream(uri)){
                bmp=BitmapFactory.decodeStream(is);
            }
            if(bmp!=null){
                onImageCapturedForScan(bmp);
            }else{
                Toast.makeText(this,"تعذر تحميل الصورة المختارة",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){
            Toast.makeText(this,"خطأ أثناء فتح الصورة: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    Bitmap scaleDownBitmap(Bitmap src,int maxDim){
        int w=src.getWidth(),h=src.getHeight();
        if(w<=maxDim&&h<=maxDim) return src;
        float ratio=Math.min((float)maxDim/w,(float)maxDim/h);
        int nw=Math.round(w*ratio),nh=Math.round(h*ratio);
        return Bitmap.createScaledBitmap(src,Math.max(1,nw),Math.max(1,nh),true);
    }

    Bitmap rotateBitmap(Bitmap src,float angle){
        if(angle==0) return src;
        Matrix m=new Matrix();
        m.postRotate(angle);
        return Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);
    }

    // التعرف الذكي التلقائي على أطراف وحدود الفاتورة واقتصاصها
    Bitmap autoCropDocument(Bitmap src){
        if(src==null) return null;
        int w=src.getWidth(),h=src.getHeight();
        if(w<60||h<60) return src;

        try{
            // تصغير الصورة للتحليل السريع للكثافة الضوئية والحدود
            int sampleW=240, sampleH=Math.max(1,(int)(240f*h/w));
            Bitmap small=Bitmap.createScaledBitmap(src,sampleW,sampleH,false);
            int[] pixels=new int[sampleW*sampleH];
            small.getPixels(pixels,0,sampleW,0,0,sampleW,sampleH);

            int[] lum=new int[sampleW*sampleH];
            for(int i=0;i<pixels.length;i++){
                int c=pixels[i];
                int r=(c>>16)&0xFF, g=(c>>8)&0xFF, b=c&0xFF;
                lum[i]=(r*77+g*150+b*29)>>8;
            }

            // حساب متوسط إضاءة الإطار الخارجي (الخلفية / الطاولة)
            int borderSum=0, borderCount=0;
            for(int x=0;x<sampleW;x++){
                borderSum+=lum[x]+lum[(sampleH-1)*sampleW+x];
                borderCount+=2;
            }
            for(int y=0;y<sampleH;y++){
                borderSum+=lum[y*sampleW]+lum[y*sampleW+(sampleW-1)];
                borderCount+=2;
            }
            int bgLum=borderCount>0?borderSum/borderCount:128;

            // كشف بداية ونهاية ورقة الفاتورة أفقياً وعمودياً
            int top=0, bottom=sampleH-1, left=0, right=sampleW-1;
            int thresholdDiff=Math.max(18,Math.abs(bgLum>128?-30:30));

            // مسح من الأعلى
            for(int y=2;y<sampleH/2;y++){
                int rowAvg=0;
                for(int x=sampleW/4;x<sampleW*3/4;x++) rowAvg+=lum[y*sampleW+x];
                rowAvg/=(sampleW/2);
                if(Math.abs(rowAvg-bgLum)>thresholdDiff){ top=Math.max(0,y-2); break; }
            }

            // مسح من الأسفل
            for(int y=sampleH-3;y>sampleH/2;y--){
                int rowAvg=0;
                for(int x=sampleW/4;x<sampleW*3/4;x++) rowAvg+=lum[y*sampleW+x];
                rowAvg/=(sampleW/2);
                if(Math.abs(rowAvg-bgLum)>thresholdDiff){ bottom=Math.min(sampleH-1,y+2); break; }
            }

            // مسح من اليمين واليسار
            for(int x=2;x<sampleW/2;x++){
                int colAvg=0;
                for(int y=sampleH/4;y<sampleH*3/4;y++) colAvg+=lum[y*sampleW+x];
                colAvg/=(sampleH/2);
                if(Math.abs(colAvg-bgLum)>thresholdDiff){ left=Math.max(0,x-2); break; }
            }

            for(int x=sampleW-3;x>sampleW/2;x--){
                int colAvg=0;
                for(int y=sampleH/4;y<sampleH*3/4;y++) colAvg+=lum[y*sampleW+x];
                colAvg/=(sampleH/2);
                if(Math.abs(colAvg-bgLum)>thresholdDiff){ right=Math.min(sampleW-1,x+2); break; }
            }

            // تحويل الإحداثيات إلى أبعاد الصورة الأصلية
            float scaleX=(float)w/sampleW;
            float scaleY=(float)h/sampleH;

            int realLeft=Math.max(0,Math.round(left*scaleX));
            int realTop=Math.max(0,Math.round(top*scaleY));
            int realRight=Math.min(w,Math.round((right+1)*scaleX));
            int realBottom=Math.min(h,Math.round((bottom+1)*scaleY));

            int cropW=realRight-realLeft;
            int cropH=realBottom-realTop;

            if(cropW>=w*0.35f && cropH>=h*0.35f){
                return Bitmap.createBitmap(src,realLeft,realTop,cropW,cropH);
            }
        }catch(Exception ignored){}

        // اقتصاص هامش أمان طفيف في حال كان التباين متقارباً
        int marginX=Math.max(0,(int)(w*0.02f));
        int marginY=Math.max(0,(int)(h*0.02f));
        int nw=w-marginX*2, nh=h-marginY*2;
        if(nw>10&&nh>10) return Bitmap.createBitmap(src,marginX,marginY,nw,nh);
        return src;
    }

    Bitmap applyCamScannerFilter(Bitmap src,String mode){
        if(src==null) return null;
        int w=src.getWidth(),h=src.getHeight();
        if("original".equals(mode)){
            return src.copy(src.getConfig(),true);
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[w*h];
        src.getPixels(pixels,0,w,0,0,w,h);

        if("bw".equals(mode)){
            // فلتر أبيض وأسود عالي الوضوح للنصوص والأرقام
            for(int i=0;i<pixels.length;i++){
                int c=pixels[i];
                int r=(c>>16)&0xFF,g=(c>>8)&0xFF,b=c&0xFF;
                int lum=(r*77+g*150+b*29)>>8;
                int val=lum>135?255:0;
                pixels[i]=0xFF000000|(val<<16)|(val<<8)|val;
            }
        }else if("gray".equals(mode)){
            // فلتر تدرج رمادي ناعم
            for(int i=0;i<pixels.length;i++){
                int c=pixels[i];
                int r=(c>>16)&0xFF,g=(c>>8)&0xFF,b=c&0xFF;
                int lum=(r*77+g*150+b*29)>>8;
                pixels[i]=0xFF000000|(lum<<16)|(lum<<8)|lum;
            }
        }else{
            // فلتر سحري "Magic Color" لتحسين التباين وتبييض الخلفية وإبراز الخطوط
            for(int i=0;i<pixels.length;i++){
                int c=pixels[i];
                int r=(c>>16)&0xFF,g=(c>>8)&0xFF,b=c&0xFF;
                int lum=(r*77+g*150+b*29)>>8;
                float factor=lum>145?1.30f:0.80f;
                int nr=Math.min(255,Math.max(0,(int)(r*factor)));
                int ng=Math.min(255,Math.max(0,(int)(g*factor)));
                int nb=Math.min(255,Math.max(0,(int)(b*factor)));
                if(lum>175){ nr=Math.min(255,nr+28); ng=Math.min(255,ng+28); nb=Math.min(255,nb+28); }
                pixels[i]=0xFF000000|(nr<<16)|(ng<<8)|nb;
            }
        }
        out.setPixels(pixels,0,w,0,0,w,h);
        return out;
    }

    void onImageCapturedForScan(Bitmap raw){
        Bitmap scaled=scaleDownBitmap(raw,1400);
        scanRawBitmap=autoCropDocument(scaled);
        scanRotation=0;
        scanFilterMode="magic";
        showScanProcessingDialog();
    }

    // نافذة الاقتصاص اليدوي الدقيق
    void showManualCropDialog(Bitmap src,Runnable onApply){
        if(src==null) return;
        Dialog dlg=new Dialog(this,android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.rgb(18,22,20));
        box.setPadding(dp(10),dp(10),dp(10),dp(10));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Header
        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button close=button("✕"); close.setTextColor(Color.WHITE); close.setBackgroundColor(RED);
        close.setOnClickListener(v->dlg.dismiss());
        head.addView(close,new LinearLayout.LayoutParams(dp(44),dp(40)));

        TextView title=tv("✂️ اقتصاص الفاتورة يدوياً",16);
        title.setTextColor(Color.WHITE); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,dp(52),1);
        tlp.setMargins(dp(8),0,0,0);
        head.addView(title,tlp);
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(48)));

        // Live Preview Image
        ImageView cropPreview=new ImageView(this);
        cropPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cropPreview.setBackground(outlined(Color.DKGRAY,1,8));
        box.addView(cropPreview,new LinearLayout.LayoutParams(-1,dp(220)));

        // Crop Margin Percentages (0 to 45)
        final int[] cropMargins=new int[]{2,2,2,2}; // top, bottom, right, left

        final Bitmap[] currentCropResult=new Bitmap[]{src};

        Runnable updateCrop=()->{
            int w=src.getWidth(), h=src.getHeight();
            int topPx=Math.round(h*(cropMargins[0]/100f));
            int bottomPx=Math.round(h*(cropMargins[1]/100f));
            int rightPx=Math.round(w*(cropMargins[2]/100f));
            int leftPx=Math.round(w*(cropMargins[3]/100f));

            int newW=Math.max(10,w-leftPx-rightPx);
            int newH=Math.max(10,h-topPx-bottomPx);
            int startX=Math.min(w-10,leftPx);
            int startY=Math.min(h-10,topPx);

            Bitmap cropped=Bitmap.createBitmap(src,startX,startY,newW,newH);
            currentCropResult[0]=cropped;
            cropPreview.setImageBitmap(cropped);
        };

        ScrollView sv=new ScrollView(this);
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0,dp(6),0,dp(6));

        // Adjusters for 4 sides
        String[] sideNames={"الأعلى (Top)","الأسفل (Bottom)","اليمين (Right)","اليسار (Left)"};
        for(int i=0;i<4;i++){
            final int sideIdx=i;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label=tv(sideNames[i]+": "+cropMargins[i]+"%",12);
            label.setTextColor(Color.WHITE);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));

            Button minus=button("- 5%"); minus.setTextColor(Color.WHITE); minus.setBackgroundColor(DARK);
            minus.setOnClickListener(v->{
                cropMargins[sideIdx]=Math.max(0,cropMargins[sideIdx]-5);
                label.setText(sideNames[sideIdx]+": "+cropMargins[sideIdx]+"%");
                updateCrop.run();
            });
            row.addView(minus,new LinearLayout.LayoutParams(dp(54),dp(36)));

            Button plus=button("+ 5%"); plus.setTextColor(Color.WHITE); plus.setBackgroundColor(GREEN);
            plus.setOnClickListener(v->{
                cropMargins[sideIdx]=Math.min(45,cropMargins[sideIdx]+5);
                label.setText(sideNames[sideIdx]+": "+cropMargins[sideIdx]+"%");
                updateCrop.run();
            });
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(54),dp(36));
            pp.setMargins(dp(4),0,0,0);
            row.addView(plus,pp);

            controls.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));
        }

        // Quick Preset Buttons
        LinearLayout presets=new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setPadding(0,dp(6),0,dp(6));

        Button resetBtn=button("📐 الصورة كاملة"); resetBtn.setTextColor(Color.WHITE); resetBtn.setBackgroundColor(Color.GRAY);
        resetBtn.setOnClickListener(v->{
            cropMargins[0]=0; cropMargins[1]=0; cropMargins[2]=0; cropMargins[3]=0;
            updateCrop.run();
        });
        presets.addView(resetBtn,new LinearLayout.LayoutParams(0,dp(50),1));

        Button autoBtn=button("🔍 اقتصاص ذكي"); autoBtn.setTextColor(Color.WHITE); autoBtn.setBackgroundColor(BLUE);
        autoBtn.setOnClickListener(v->{
            Bitmap autoBmp=autoCropDocument(src);
            currentCropResult[0]=autoBmp;
            cropPreview.setImageBitmap(autoBmp);
        });
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(50),1);
        ap.setMargins(dp(4),0,0,0);
        presets.addView(autoBtn,ap);
        controls.addView(presets,new LinearLayout.LayoutParams(-1,dp(48)));

        // Apply Button
        Button applyBtn=action("✅ اعتماد الاقتصاص وتحديث الفاتورة",GREEN);
        applyBtn.setTextSize(14);
        applyBtn.setOnClickListener(v->{
            scanRawBitmap=currentCropResult[0];
            dlg.dismiss();
            if(onApply!=null) onApply.run();
        });
        controls.addView(applyBtn,new LinearLayout.LayoutParams(-1,dp(50)));

        sv.addView(controls);
        box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        updateCrop.run();
        dlg.setContentView(box);
        dlg.show();
    }

    void showScanProcessingDialog(){
        if(scanRawBitmap==null){
            Toast.makeText(this,"لا توجد صورة لمعالجتها",Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dlg=new Dialog(this,android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);
        box.setPadding(dp(12),dp(8),dp(12),dp(8));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Header
        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button closeBtn=button("✕");
        closeBtn.setTextColor(Color.WHITE); closeBtn.setBackgroundColor(RED);
        closeBtn.setOnClickListener(v->dlg.dismiss());
        header.addView(closeBtn,new LinearLayout.LayoutParams(dp(44),dp(40)));

        TextView titleTv=tv("🪄 معالجة واقتصاص الفاتورة",16);
        titleTv.setTextColor(GREEN); titleTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,dp(52),1);
        tlp.setMargins(dp(6),0,0,0);
        header.addView(titleTv,tlp);
        box.addView(header,new LinearLayout.LayoutParams(-1,dp(46)));

        // Preview Image
        ImageView preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(outlined(Color.BLACK,1,8));
        preview.setPadding(dp(2),dp(2),dp(2),dp(2));
        box.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));

        // Filters and Crop Row
        LinearLayout filtersRow=new LinearLayout(this);
        filtersRow.setOrientation(LinearLayout.HORIZONTAL);
        filtersRow.setGravity(Gravity.CENTER);
        filtersRow.setPadding(0,dp(3),0,dp(3));

        Button btnMagic=action("🪄 سحري",GREEN);
        Button btnBw=action("📄 أبيض/أسود",DARK);
        Button btnGray=action("🔘 رمادي",BLUE);
        Button btnCrop=action("✂️ اقتصاص",Color.rgb(180,90,20));
        Button btnRotate=action("🔄 90°",GOLD);

        final Bitmap[] renderedBmp=new Bitmap[]{null};

        Runnable updatePreview=()->{
            Bitmap rot=rotateBitmap(scanRawBitmap,scanRotation);
            Bitmap proc=applyCamScannerFilter(rot,scanFilterMode);
            renderedBmp[0]=proc;
            preview.setImageBitmap(proc);
        };

        btnMagic.setOnClickListener(v->{ scanFilterMode="magic"; updatePreview.run(); });
        btnBw.setOnClickListener(v->{ scanFilterMode="bw"; updatePreview.run(); });
        btnGray.setOnClickListener(v->{ scanFilterMode="gray"; updatePreview.run(); });
        btnCrop.setOnClickListener(v->showManualCropDialog(scanRawBitmap,updatePreview));
        btnRotate.setOnClickListener(v->{ scanRotation=(scanRotation+90)%360; updatePreview.run(); });

        filtersRow.addView(btnMagic,new LinearLayout.LayoutParams(0,dp(52),1));
        filtersRow.addView(btnBw,new LinearLayout.LayoutParams(0,dp(52),1));
        filtersRow.addView(btnGray,new LinearLayout.LayoutParams(0,dp(52),1));
        filtersRow.addView(btnCrop,new LinearLayout.LayoutParams(0,dp(52),1));
        filtersRow.addView(btnRotate,new LinearLayout.LayoutParams(0,dp(52),0.9f));
        box.addView(filtersRow,new LinearLayout.LayoutParams(-1,dp(44)));

        updatePreview.run();

        // Fields Scroll
        ScrollView sv=new ScrollView(this);
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(4),dp(4),dp(4),dp(4));

        String defName="فاتورة_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date());
        EditText nameInput=field("اسم الفاتورة أو الوصف");
        nameInput.setText(defName);
        fields.addView(tv("اسم أو وصف الفاتورة:",12),new LinearLayout.LayoutParams(-1,dp(20)));
        fields.addView(nameInput,new LinearLayout.LayoutParams(-1,dp(50)));

        fields.addView(tv("تصنيف الفاتورة:",12),new LinearLayout.LayoutParams(-1,dp(20)));
        String[] categories={"فواتير مبيعات","فواتير شراء","سندات قبض","مصاريف عامة","أخرى"};
        final String[] selectedCat=new String[]{categories[0]};

        LinearLayout catRow=new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button[] catButtons=new Button[categories.length];
        for(int i=0;i<categories.length;i++){
            final String cat=categories[i];
            Button cb=button(cat);
            cb.setTextSize(10);
            catButtons[i]=cb;
            cb.setOnClickListener(v->{
                selectedCat[0]=cat;
                for(int j=0;j<categories.length;j++){
                    catButtons[j].setTextColor(categories[j].equals(cat)?Color.WHITE:TEXT);
                    catButtons[j].setBackgroundColor(categories[j].equals(cat)?GREEN:Color.rgb(235,238,235));
                }
            });
            catRow.addView(cb,new LinearLayout.LayoutParams(0,dp(46),1));
        }
        catButtons[0].setTextColor(Color.WHITE);
        catButtons[0].setBackgroundColor(GREEN);
        fields.addView(catRow,new LinearLayout.LayoutParams(-1,dp(50)));

        EditText notesInput=field("ملاحظات إضافية (اختياري)");
        fields.addView(tv("ملاحظات:",12),new LinearLayout.LayoutParams(-1,dp(20)));
        fields.addView(notesInput,new LinearLayout.LayoutParams(-1,dp(50)));

        // Save & Share Buttons
        LinearLayout actionsRow=new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0,dp(6),0,0);

        Button saveBtn=action("💾 حفظ في صور الفواتير",GREEN);
        saveBtn.setTextSize(12);
        Button shareBtn=action("📤 حفظ ومشاركة",GOLD);
        shareBtn.setTextSize(12);

        saveBtn.setOnClickListener(v->{
            String name=nameInput.getText().toString().trim();
            if(name.isEmpty()) name=defName;
            String cat=selectedCat[0];
            String notes=notesInput.getText().toString().trim();
            String date=db.now();
            String savedPath=saveBitmapToInvoicesDir(renderedBmp[0]);
            if(savedPath!=null){
                String fileName=new File(savedPath).getName();
                db.addScannedInvoice(name,fileName,cat,notes,date,savedPath);
                Toast.makeText(this,"تم الحفظ في:\nDownload/بقالة العزي للمواد الغذائية خاص/صور الفواتير",Toast.LENGTH_LONG).show();
                dlg.dismiss();
                scanner();
            }else{
                Toast.makeText(this,"فشل حفظ ملف الصورة",Toast.LENGTH_SHORT).show();
            }
        });

        shareBtn.setOnClickListener(v->{
            String name=nameInput.getText().toString().trim();
            if(name.isEmpty()) name=defName;
            String cat=selectedCat[0];
            String notes=notesInput.getText().toString().trim();
            String date=db.now();
            String savedPath=saveBitmapToInvoicesDir(renderedBmp[0]);
            if(savedPath!=null){
                String fileName=new File(savedPath).getName();
                db.addScannedInvoice(name,fileName,cat,notes,date,savedPath);
                Toast.makeText(this,"تم الحفظ في:\nDownload/بقالة العزي للمواد الغذائية خاص/صور الفواتير",Toast.LENGTH_SHORT).show();
                dlg.dismiss();
                scanner();
                shareScannedInvoice(savedPath,name);
            }
        });

        actionsRow.addView(saveBtn,new LinearLayout.LayoutParams(0,dp(48),1.2f));
        actionsRow.addView(shareBtn,new LinearLayout.LayoutParams(0,dp(48),1f));
        fields.addView(actionsRow,new LinearLayout.LayoutParams(-1,dp(54)));

        sv.addView(fields);
        box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        dlg.setContentView(box);
        dlg.show();
    }

    String saveBitmapToInvoicesDir(Bitmap bitmap){
        if(bitmap==null) return null;
        String timeStamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        String fileName="فاتورة_"+timeStamp+".jpg";
        return AppStorage.saveInvoicePhoto(this, bitmap, fileName);
    }

    void shareScannedInvoice(String filePath,String title){
        try{
            File file=new File(filePath);
            if(!file.exists()){
                // تجربة البحث بالاسم في المجلد البديل
                file=new File(getInvoicesImagesDir(),new File(filePath).getName());
            }
            if(!file.exists()){
                Toast.makeText(this,"ملف الصورة غير موجود",Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM,uri);
            intent.putExtra(Intent.EXTRA_SUBJECT,title);
            intent.putExtra(Intent.EXTRA_TEXT,"فاتورة: "+title+"\nبقالة العزي للمواد الغذائية للمواد الغذائية");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,"مشاركة الفاتورة عبر"));
        }catch(Exception e){
            Toast.makeText(this,"تعذر المشاركة: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    void showScannedInvoiceViewer(long id,String name,String fileName,String category,String notes,String date,String imagePath){
        Dialog dlg=new Dialog(this,android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);
        box.setPadding(dp(12),dp(10),dp(12),dp(10));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button close=button("✕");
        close.setTextColor(Color.WHITE); close.setBackgroundColor(DARK);
        close.setOnClickListener(v->dlg.dismiss());
        top.addView(close,new LinearLayout.LayoutParams(dp(44),dp(40)));

        TextView titleTv=tv(name,16);
        titleTv.setTextColor(GREEN); titleTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(titleTv,new LinearLayout.LayoutParams(0,dp(52),1));
        box.addView(top,new LinearLayout.LayoutParams(-1,dp(48)));

        ImageView iv=new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackground(outlined(Color.BLACK,1,8));
        File file=new File(imagePath);
        if(!file.exists()){
            file=new File(getInvoicesImagesDir(),new File(imagePath).getName());
        }
        if(file.exists()){
            Bitmap b=BitmapFactory.decodeFile(file.getAbsolutePath());
            if(b!=null) iv.setImageBitmap(b);
        }
        box.addView(iv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout details=card();
        details.setPadding(dp(8),dp(6),dp(8),dp(6));
        details.addView(tv("التصنيف: "+category+"  •  التاريخ: "+date,11),new LinearLayout.LayoutParams(-1,dp(22)));
        if(notes!=null&&!notes.trim().isEmpty()){
            details.addView(tv("ملاحظات: "+notes,11),new LinearLayout.LayoutParams(-1,dp(22)));
        }
        box.addView(details,new LinearLayout.LayoutParams(-1,dp(60)));

        LinearLayout bbar=new LinearLayout(this);
        bbar.setOrientation(LinearLayout.HORIZONTAL);
        bbar.setPadding(0,dp(4),0,0);

        final String finalPath=file.exists()?file.getAbsolutePath():imagePath;
        Button shareBtn=action("📤 مشاركة الفاتورة",GOLD);
        shareBtn.setOnClickListener(v->shareScannedInvoice(finalPath,name));

        final File toDel=file;
        Button delBtn=action("🗑️ حذف",RED);
        delBtn.setOnClickListener(v->{
            new AlertDialog.Builder(this)
                .setTitle("حذف الفاتورة")
                .setMessage("هل أنت متأكد من حذف هذه الفاتورة من الأرشيف؟")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("حذف",(d,w)->{
                    db.deleteScannedInvoice(id);
                    if(toDel.exists()) toDel.delete();
                    Toast.makeText(this,"تم حذف الفاتورة",Toast.LENGTH_SHORT).show();
                    dlg.dismiss();
                    scanner();
                }).show();
        });

        bbar.addView(shareBtn,new LinearLayout.LayoutParams(0,dp(48),1.5f));
        bbar.addView(delBtn,new LinearLayout.LayoutParams(0,dp(48),1f));
        box.addView(bbar,new LinearLayout.LayoutParams(-1,dp(52)));

        dlg.setContentView(box);
        dlg.show();
    }

    void scanner(){
        base("الماسح الضوئي");
        // 1. واجهة المعاينة والكاميرا الذكية (Camera Preview Card)
        LinearLayout cameraPreviewCard=new LinearLayout(this);
        cameraPreviewCard.setOrientation(LinearLayout.VERTICAL);
        cameraPreviewCard.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(18,32,24),Color.rgb(10,18,14)}));
        cameraPreviewCard.setPadding(dp(12),dp(12),dp(12),dp(12));
        cameraPreviewCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // إطار العدسة ومعاينة المسح
        LinearLayout viewfinder=new LinearLayout(this);
        viewfinder.setOrientation(LinearLayout.VERTICAL);
        viewfinder.setGravity(Gravity.CENTER);
        viewfinder.setBackground(outlined(Color.rgb(28,48,36),1,12));
        viewfinder.setPadding(dp(8),dp(10),dp(8),dp(10));

        TextView camIcon=tv("📷",28);
        camIcon.setGravity(Gravity.CENTER);
        viewfinder.addView(camIcon,new LinearLayout.LayoutParams(-1,dp(48)));

        TextView camHint=tv("ماسح المستندات والفواتير الذكي",13);
        camHint.setTextColor(Color.WHITE); camHint.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        camHint.setGravity(Gravity.CENTER);
        viewfinder.addView(camHint,new LinearLayout.LayoutParams(-1,dp(24)));

        TextView subHint=tv("اقتصاص تلقائي على أطراف الفاتورة + تحسين التباين + حفظ في مجلد صور الفواتير",10);
        subHint.setTextColor(Color.rgb(180,210,190)); subHint.setGravity(Gravity.CENTER);
        viewfinder.addView(subHint,new LinearLayout.LayoutParams(-1,dp(22)));

        // زر الالتقاط العائم المميّز
        Button captureBtn=action("📸 التقاط الفاتورة بالكاميرا",GREEN);
        captureBtn.setTextSize(14); captureBtn.setElevation(4);
        captureBtn.setOnClickListener(v->launchScanCamera());
        viewfinder.addView(captureBtn,new LinearLayout.LayoutParams(-1,dp(52)));

        // خيار استيراد صورة من المعرض
        Button galleryBtn=button("🖼️ أو اختيار صورة من المعرض");
        galleryBtn.setTextColor(Color.rgb(200,230,210)); galleryBtn.setTextSize(11);
        galleryBtn.setBackgroundColor(Color.TRANSPARENT);
        galleryBtn.setOnClickListener(v->launchScanGallery());
        viewfinder.addView(galleryBtn,new LinearLayout.LayoutParams(-1,dp(32)));

        cameraPreviewCard.addView(viewfinder,new LinearLayout.LayoutParams(-1,-2));
        content.addView(cameraPreviewCard,new LinearLayout.LayoutParams(-1,-2));
        addSpace(4);

        // 2. قسم الفواتير المحفوظة (Saved Invoices Section)
        section("📁 الفواتير المحفوظة ("+db.scannedInvoiceCount()+")");

        // Search Field
        EditText search=field("🔍 بحث في الفواتير المحفوظة");
        search.setText(scanSearchQuery);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                scanSearchQuery=s.toString().trim();
                refreshScannedList(content);
            }
            public void afterTextChanged(android.text.Editable s){}
        });
        content.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(3);

        // Category Filter Chips
        String[] cats={"الكل","فواتير مبيعات","فواتير شراء","سندات قبض","أخرى"};
        LinearLayout catFilterRow=new LinearLayout(this);
        catFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        catFilterRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        final Button[] chips=new Button[cats.length];
        for(int i=0;i<cats.length;i++){
            final String cat=cats[i];
            Button chip=button(cat);
            chip.setTextSize(10);
            chips[i]=chip;
            boolean active=cat.equals(scanCategoryFilter);
            chip.setTextColor(active?Color.WHITE:TEXT);
            chip.setBackgroundColor(active?DARK:Color.rgb(230,235,230));
            chip.setOnClickListener(v->{
                scanCategoryFilter=cat;
                for(int j=0;j<cats.length;j++){
                    boolean sel=cats[j].equals(cat);
                    chips[j].setTextColor(sel?Color.WHITE:TEXT);
                    chips[j].setBackgroundColor(sel?DARK:Color.rgb(230,235,230));
                }
                refreshScannedList(content);
            });
            catFilterRow.addView(chip,new LinearLayout.LayoutParams(0,dp(46),1));
        }
        content.addView(catFilterRow,new LinearLayout.LayoutParams(-1,dp(48)));
        addSpace(4);

        // Container for scanned invoices list
        LinearLayout listContainer=new LinearLayout(this);
        listContainer.setTag("scanned_list_container");
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer,new LinearLayout.LayoutParams(-1,-2));

        populateScannedInvoices(listContainer);
    }

    void refreshScannedList(LinearLayout parent){
        LinearLayout container=(LinearLayout)parent.findViewWithTag("scanned_list_container");
        if(container!=null){
            container.removeAllViews();
            populateScannedInvoices(container);
        }
    }

    void populateScannedInvoices(LinearLayout container){
        Cursor c=db.scannedInvoices(scanSearchQuery,scanCategoryFilter);
        int count=0;
        if(c!=null){
            while(c.moveToNext()){
                count++;
                long id=c.getLong(0);
                String name=c.getString(1);
                String fileName=c.getString(2);
                String cat=c.getString(3);
                String notes=c.getString(4);
                String date=c.getString(5);
                String imgPath=c.getString(6);

                LinearLayout card=card();
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setPadding(dp(8),dp(6),dp(8),dp(6));
                card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                // Thumbnail
                ImageView thumb=new ImageView(this);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackground(outlined(Color.rgb(220,225,220),1,6));
                if(imgPath!=null&&new File(imgPath).exists()){
                    Bitmap b=BitmapFactory.decodeFile(imgPath);
                    if(b!=null) thumb.setImageBitmap(b);
                }
                card.addView(thumb,new LinearLayout.LayoutParams(dp(54),dp(54)));

                // Info Column
                LinearLayout info=new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setPadding(dp(8),0,dp(8),0);

                TextView nameTv=tv(name,13);
                nameTv.setTextColor(GREEN); nameTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                nameTv.setMaxLines(3);
                info.addView(nameTv,new LinearLayout.LayoutParams(-1,dp(22)));

                TextView subTv=tv("🏷️ "+(cat==null?"عام":cat)+"  •  📅 "+date,10);
                subTv.setTextColor(MUTED); subTv.setMaxLines(3);
                info.addView(subTv,new LinearLayout.LayoutParams(-1,dp(18)));

                if(notes!=null&&!notes.trim().isEmpty()){
                    TextView noteTv=tv("📝 "+notes,10);
                    noteTv.setTextColor(TEXT); noteTv.setMaxLines(3);
                    info.addView(noteTv,new LinearLayout.LayoutParams(-1,dp(16)));
                }
                card.addView(info,new LinearLayout.LayoutParams(0,-2,1));

                // Quick Actions Column
                LinearLayout actions=new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER_VERTICAL);

                Button viewBtn=button("👁️");
                viewBtn.setTextSize(14);
                viewBtn.setContentDescription("عرض الفاتورة");
                viewBtn.setOnClickListener(v->showScannedInvoiceViewer(id,name,fileName,cat,notes,date,imgPath));
                actions.addView(viewBtn,new LinearLayout.LayoutParams(dp(38),dp(38)));

                Button shareBtn=button("📤");
                shareBtn.setTextSize(14);
                shareBtn.setContentDescription("مشاركة الفاتورة");
                shareBtn.setOnClickListener(v->shareScannedInvoice(imgPath,name));
                actions.addView(shareBtn,new LinearLayout.LayoutParams(dp(38),dp(38)));

                card.addView(actions,new LinearLayout.LayoutParams(-2,-2));

                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,0,0,dp(6));
                container.addView(card,lp);
            }
            c.close();
        }

        if(count==0){
            LinearLayout emptyBox=card();
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setPadding(dp(16),dp(20),dp(16),dp(20));
            emptyBox.setGravity(Gravity.CENTER);

            TextView emptyIcon=tv("📄",32);
            emptyIcon.setGravity(Gravity.CENTER);
            emptyBox.addView(emptyIcon,new LinearLayout.LayoutParams(-1,dp(45)));

            TextView emptyText=tv("لا توجد فواتير ممسوحة ضوئياً حتى الآن",13);
            emptyText.setTextColor(MUTED); emptyText.setGravity(Gravity.CENTER);
            emptyBox.addView(emptyText,new LinearLayout.LayoutParams(-1,dp(26)));

            TextView emptySub=tv("اضغط على 'التقاط بالكاميرا' لتصوير فاتورة واقتصاصها وتحسين وضوحها تلقائياً",11);
            emptySub.setTextColor(MUTED); emptySub.setGravity(Gravity.CENTER);
            emptyBox.addView(emptySub,new LinearLayout.LayoutParams(-1,dp(48)));

            container.addView(emptyBox,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    static class DB extends SQLiteOpenHelper{
        static final String DB_NAME="alazzi_grocery_runtime_v5.db";
        DB(Context c){super(c,DB_NAME,null,1);}
        @Override public void onConfigure(SQLiteDatabase d){
            super.onConfigure(d);
            try{d.execSQL("PRAGMA busy_timeout=1500");}catch(Exception ignored){}
        }
        public void onCreate(SQLiteDatabase d){create(d);}
        void ensureColumn(SQLiteDatabase d,String table,String column,String definition){
            Cursor c=null;
            try{
                c=d.rawQuery("PRAGMA table_info("+table+")",null);
                while(c.moveToNext()) if(column.equalsIgnoreCase(c.getString(1))) return;
            }finally{if(c!=null)c.close();}
            d.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
        }
        void create(SQLiteDatabase d){
            d.execSQL("CREATE TABLE IF NOT EXISTS customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,no TEXT,customer TEXT,total REAL,paid REAL DEFAULT 0,date TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER,amount REAL,details TEXT,type INTEGER,date TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,qty REAL,min_qty REAL,cost REAL DEFAULT 0,sale REAL DEFAULT 0)");
            d.execSQL("CREATE TABLE IF NOT EXISTS invoice_items(id INTEGER PRIMARY KEY AUTOINCREMENT,invoice_id INTEGER,name TEXT,qty REAL,total REAL,unit_cost REAL DEFAULT 0)");
            d.execSQL("CREATE TABLE IF NOT EXISTS suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS purchase_invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,no TEXT,supplier TEXT,total REAL,paid REAL DEFAULT 0,date TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS purchase_items(id INTEGER PRIMARY KEY AUTOINCREMENT,purchase_id INTEGER,name TEXT,qty REAL,cost REAL,sale REAL,total REAL)");
            d.execSQL("CREATE TABLE IF NOT EXISTS note_pages(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,date TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS note_items(id INTEGER PRIMARY KEY AUTOINCREMENT,page_id INTEGER,side INTEGER,name TEXT,qty REAL,position INTEGER)");
            d.execSQL("CREATE TABLE IF NOT EXISTS scanned_invoices(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, file_name TEXT, category TEXT, notes TEXT, date TEXT, image_path TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS stock_movements(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER,item_name TEXT,qty REAL,unit_cost REAL,source_type TEXT,source_id INTEGER,created_at TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS transfers(id INTEGER PRIMARY KEY AUTOINCREMENT,amount REAL NOT NULL,sender_name TEXT,sender_phone TEXT,receiver_name TEXT,receiver_phone TEXT,date TEXT,note TEXT,review INTEGER DEFAULT 0)");
        }
        public void onUpgrade(SQLiteDatabase d,int o,int n){ create(d); cleanupDuplicateCustomers(d); }
        @Override public void onOpen(SQLiteDatabase d){ super.onOpen(d); cleanupDuplicateCustomers(d); }
        void cleanupDuplicateCustomers(SQLiteDatabase d){
            Cursor c=null;
            try{
                c=d.rawQuery("SELECT id,name,COALESCE(phone,'') FROM customers ORDER BY id ASC",null);
                HashMap<String,Long> primary=new HashMap<>();
                HashMap<String,String> canonical=new HashMap<>();
                ArrayList<Long> duplicateIds=new ArrayList<>();
                ArrayList<String> duplicateNames=new ArrayList<>();
                while(c.moveToNext()){
                    long id=c.getLong(0);
                    String raw=c.getString(1)==null?"":c.getString(1).trim();
                    String key=raw.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
                    if(key.isEmpty()) continue;
                    String canon=raw.replaceAll("\\s+"," ").trim();
                    if(!primary.containsKey(key)){
                        primary.put(key,id); canonical.put(key,canon);
                        if(!canon.equals(raw)){
                            ContentValues v=new ContentValues();v.put("name",canon);d.update("customers",v,"id=?",new String[]{String.valueOf(id)});
                            d.update("invoices",v,"customer=?",new String[]{raw});
                        }
                    }else{
                        long keep=primary.get(key);
                        duplicateIds.add(id); duplicateNames.add(raw);
                        String keepName=canonical.get(key);
                        d.execSQL("UPDATE transactions SET customer_id=? WHERE customer_id=?",new Object[]{keep,id});
                        d.execSQL("UPDATE invoices SET customer=? WHERE customer=?",new Object[]{keepName,raw});
                        String phone=c.getString(2)==null?"":c.getString(2).trim();
                        if(!phone.isEmpty()){
                            Cursor pc=d.rawQuery("SELECT COALESCE(phone,'') FROM customers WHERE id=?",new String[]{String.valueOf(keep)});
                            String kp=pc.moveToFirst()?pc.getString(0):"";pc.close();
                            if(kp==null||kp.trim().isEmpty()){
                                ContentValues pv=new ContentValues();pv.put("phone",phone);d.update("customers",pv,"id=?",new String[]{String.valueOf(keep)});
                            }
                        }
                    }
                }
                for(Long id:duplicateIds) d.delete("customers","id=?",new String[]{String.valueOf(id)});
            }catch(Exception ignored){}finally{if(c!=null)c.close();}
        }
        Cursor scannedInvoices(String q,String category){
            String sel="";
            ArrayList<String> args=new ArrayList<>();
            if(q!=null&&!q.trim().isEmpty()){
                sel+="(name LIKE ? OR notes LIKE ?)";
                args.add("%"+q+"%");
                args.add("%"+q+"%");
            }
            if(category!=null&&!category.equals("الكل")&&!category.trim().isEmpty()){
                if(!sel.isEmpty()) sel+=" AND ";
                sel+="category=?";
                args.add(category);
            }
            return getReadableDatabase().query("scanned_invoices",
                new String[]{"id","name","file_name","category","notes","date","image_path"},
                sel.isEmpty()?null:sel,
                args.isEmpty()?null:args.toArray(new String[0]),
                null,null,"datetime(date) DESC, id DESC");
        }
        long addScannedInvoice(String name,String fileName,String category,String notes,String date,String imagePath){
            ContentValues v=new ContentValues();
            v.put("name",name==null?"":name);
            v.put("file_name",fileName==null?"":fileName);
            v.put("category",category==null?"عام":category);
            v.put("notes",notes==null?"":notes);
            v.put("date",date==null?now():date);
            v.put("image_path",imagePath==null?"":imagePath);
            return getWritableDatabase().insert("scanned_invoices",null,v);
        }
        void deleteScannedInvoice(long id){
            if(id>0) getWritableDatabase().delete("scanned_invoices","id=?",new String[]{String.valueOf(id)});
        }
        int scannedInvoiceCount(){
            Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM scanned_invoices",null);
            int x=c.moveToFirst()?c.getInt(0):0;
            c.close();
            return x;
        }
        long addTransfer(double amount,String senderName,String senderPhone,String receiverName,String receiverPhone,String note,int review){ContentValues v=new ContentValues();v.put("amount",amount);v.put("sender_name",senderName);v.put("sender_phone",senderPhone);v.put("receiver_name",receiverName);v.put("receiver_phone",receiverPhone);v.put("note",note);v.put("date",now());v.put("review",review);return getWritableDatabase().insert("transfers",null,v);}
Cursor transfers(){return getReadableDatabase().rawQuery("SELECT id,amount,sender_name,sender_phone,receiver_name,receiver_phone,date,note,review FROM transfers ORDER BY datetime(date) DESC,id DESC",null);}
int transferCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transfers",null);int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;}
boolean transferDuplicate(double amount,String senderPhone,String receiverPhone){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM transfers WHERE ABS(amount-?)<0.005 AND REPLACE(REPLACE(sender_phone,' ',''),'-','')=? AND REPLACE(REPLACE(receiver_phone,' ',''),'-','')=? LIMIT 1",new String[]{String.valueOf(amount),senderPhone.replace(" ","").replace("-",""),receiverPhone.replace(" ","").replace("-","")});boolean x=c.moveToFirst();c.close();return x;}
void markTransferReady(long id){if(id>0){ContentValues v=new ContentValues();v.put("review",0);getWritableDatabase().update("transfers",v,"id=?",new String[]{String.valueOf(id)});}}
        void deleteTransfer(long id){getWritableDatabase().delete("transfers","id=?",new String[]{String.valueOf(id)});}
long createNotePage(String title,String date){ContentValues v=new ContentValues();v.put("title",title);v.put("date",date);return getWritableDatabase().insert("note_pages",null,v);}
        void touchNotePage(long id){if(id>0){ContentValues v=new ContentValues();v.put("date",now());getWritableDatabase().update("note_pages",v,"id=?",new String[]{String.valueOf(id)});}}
        int nextNotePosition(long pageId,int side){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(position),0)+1 FROM note_items WHERE page_id=? AND side=?",new String[]{String.valueOf(pageId),String.valueOf(side)});int x=c.moveToFirst()?c.getInt(0):1;c.close();return x;}
        void addNoteItem(long pageId,String name,double qty,int side){ContentValues v=new ContentValues();v.put("page_id",pageId);v.put("side",side);v.put("name",name);v.put("qty",qty);v.put("position",nextNotePosition(pageId,side));getWritableDatabase().insert("note_items",null,v);touchNotePage(pageId);}
        void loadNoteItems(long pageId,ArrayList<NoteItem> left,ArrayList<NoteItem> right){Cursor c=getReadableDatabase().rawQuery("SELECT side,name,qty FROM note_items WHERE page_id=? ORDER BY side,position,id",new String[]{String.valueOf(pageId)});while(c.moveToNext()){NoteItem x=new NoteItem(c.getString(1),c.getDouble(2),c.getInt(0));if(x.side==1)left.add(x);else right.add(x);}c.close();}
        void clearNoteItems(long pageId){getWritableDatabase().delete("note_items","page_id=?",new String[]{String.valueOf(pageId)});touchNotePage(pageId);}
        void deleteNoteItem(long pageId,String name,double qty,int side){SQLiteDatabase d=getWritableDatabase();d.delete("note_items","id=(SELECT id FROM note_items WHERE page_id=? AND side=? AND name=? AND qty=? ORDER BY position,id LIMIT 1)",new String[]{String.valueOf(pageId),String.valueOf(side),name,String.valueOf(qty)});touchNotePage(pageId);}
        Cursor notePages(){return getReadableDatabase().rawQuery("SELECT p.id,p.title,p.date,COUNT(i.id) FROM note_pages p LEFT JOIN note_items i ON i.page_id=p.id GROUP BY p.id ORDER BY datetime(p.date) DESC,p.id DESC",null);}
        String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date());}
        long customer(String n){
            String name=n==null?"":n.trim().replaceAll("\\s+"," ");
            if(name.isEmpty()) return -1;
            Cursor c=getReadableDatabase().rawQuery("SELECT id FROM customers WHERE lower(trim(name))=lower(?) ORDER BY id ASC LIMIT 1",new String[]{name});
            if(c.moveToFirst()){long x=c.getLong(0);c.close();return x;} c.close();
            ContentValues v=new ContentValues();v.put("name",name);return getWritableDatabase().insert("customers",null,v);
        }
        void updateCustomer(long id,String oldName,String newName,String phone){
            SQLiteDatabase d=getWritableDatabase(); ContentValues v=new ContentValues();v.put("name",newName);v.put("phone",phone);
            d.update("customers",v,"id=?",new String[]{String.valueOf(id)});
            if(oldName!=null&&!oldName.equals(newName)){ContentValues iv=new ContentValues();iv.put("customer",newName);d.update("invoices",iv,"customer=?",new String[]{oldName});}
        }
        void addCustomer(String n,String p){
            String name=n==null?"":n.trim().replaceAll("\\s+"," "); if(name.isEmpty()) return;
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=d.rawQuery("SELECT id FROM customers WHERE lower(trim(name))=lower(?) ORDER BY id ASC LIMIT 1",new String[]{name});
            if(c.moveToFirst()){long id=c.getLong(0);c.close(); if(p!=null&&!p.trim().isEmpty()){ContentValues v=new ContentValues();v.put("phone",p.trim());d.update("customers",v,"id=?",new String[]{String.valueOf(id)});} return;} c.close();
            ContentValues v=new ContentValues();v.put("name",name);v.put("phone",p==null?"":p.trim());d.insert("customers",null,v);
        }
        long addInvoice(String no,String c,double t,double paid,String date){ContentValues v=new ContentValues();v.put("no",no);v.put("customer",c);v.put("total",t);v.put("paid",paid);v.put("date",date);return getWritableDatabase().insert("invoices",null,v);}
        void addTransaction(long id,double a,String d,int type,String date){if(id<1)return;ContentValues v=new ContentValues();v.put("customer_id",id);v.put("amount",a);v.put("details",d);v.put("type",type);v.put("date",date);getWritableDatabase().insert("transactions",null,v);}
        double balance(long id){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN type=1 THEN amount ELSE -amount END),0) FROM transactions WHERE customer_id=?",new String[]{String.valueOf(id)});double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double totalDebts(){
            Cursor c=getReadableDatabase().rawQuery("SELECT SUM(bal) FROM (SELECT SUM(CASE WHEN type=1 THEN amount ELSE -amount END) as bal FROM transactions GROUP BY customer_id HAVING bal > 0.005)",null);
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        double totalCredits(){
            Cursor c=getReadableDatabase().rawQuery("SELECT SUM(bal) FROM (SELECT SUM(CASE WHEN type=1 THEN -amount ELSE amount END) as bal FROM transactions GROUP BY customer_id HAVING bal > 0.005)",null);
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        int debtorCustomersCount(){
            Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM (SELECT customer_id, SUM(CASE WHEN type=1 THEN amount ELSE -amount END) as bal FROM transactions GROUP BY customer_id HAVING bal > 0.005)",null);
            int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;
        }
        double customerDebitTotal(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE customer_id=? AND type=1",new String[]{String.valueOf(id)});
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        double customerCreditTotal(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE customer_id=? AND type=0",new String[]{String.valueOf(id)});
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        Cursor customers(String q){return getReadableDatabase().rawQuery("SELECT id,name,COALESCE(phone,'') FROM customers WHERE name LIKE ? OR phone LIKE ? ORDER BY name",new String[]{"%"+q+"%","%"+q+"%"});}
        Cursor transactions(long id){return getReadableDatabase().rawQuery("SELECT id,date,details,amount,type FROM transactions WHERE customer_id=? ORDER BY datetime(date) DESC, id DESC",new String[]{String.valueOf(id)});}
        int nextPurchaseNo(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(CAST(no AS INTEGER)),0)+1 FROM purchase_invoices",null);int x=c.moveToFirst()?c.getInt(0):1;c.close();return x;}
        String[] supplierNames(){Cursor c=getReadableDatabase().rawQuery("SELECT name FROM suppliers ORDER BY name",null);ArrayList<String>a=new ArrayList<>();while(c.moveToNext())a.add(c.getString(0));c.close();return a.toArray(new String[0]);}
        long supplier(String n,String p){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM suppliers WHERE name=? LIMIT 1",new String[]{n});if(c.moveToFirst()){long x=c.getLong(0);c.close();return x;}c.close();ContentValues v=new ContentValues();v.put("name",n);v.put("phone",p);return getWritableDatabase().insert("suppliers",null,v);}
        long addPurchase(String no,String supplier,double total,String date){ContentValues v=new ContentValues();v.put("no",no);v.put("supplier",supplier);v.put("total",total);v.put("date",date);return getWritableDatabase().insert("purchase_invoices",null,v);}
        void replacePurchaseLines(long id,ArrayList<PurchaseLine> ls){SQLiteDatabase d=getWritableDatabase();for(PurchaseLine l:ls){ContentValues v=new ContentValues();v.put("purchase_id",id);v.put("name",l.name);v.put("qty",l.qty);v.put("cost",l.cost);v.put("sale",l.sale);v.put("total",l.total);d.insert("purchase_items",null,v);}}
        void updateStockFromPurchase(ArrayList<PurchaseLine> ls){SQLiteDatabase d=getWritableDatabase();for(PurchaseLine l:ls){Cursor c=d.rawQuery("SELECT id,qty FROM items WHERE name=? LIMIT 1",new String[]{l.name});if(c.moveToFirst()){long id=c.getLong(0);double q=c.getDouble(1);c.close();ContentValues v=new ContentValues();v.put("qty",q+l.qty);v.put("cost",l.cost);v.put("sale",l.sale);d.update("items",v,"id=?",new String[]{String.valueOf(id)});}else{c.close();ContentValues v=new ContentValues();v.put("name",l.name);v.put("qty",l.qty);v.put("min_qty",0);v.put("cost",l.cost);v.put("sale",l.sale);d.insert("items",null,v);}}}
        boolean canApplySaleStock(ArrayList<Line> ls,long oldInvoiceId){
            // البيع مسموح حتى عند نفاد المخزون؛ يتم تسجيل العجز في المخزون بالسالب.
            return true;
        }
        String saleStockWarning(ArrayList<Line> ls,long oldInvoiceId){
            SQLiteDatabase d=getReadableDatabase();
            HashMap<String,Double> needed=new HashMap<>();
            HashMap<String,String> labels=new HashMap<>();
            if(oldInvoiceId>0){
                Cursor old=d.rawQuery("SELECT name,qty FROM invoice_items WHERE invoice_id=?",new String[]{String.valueOf(oldInvoiceId)});
                while(old.moveToNext()){
                    String raw=old.getString(0)==null?"":old.getString(0).trim();
                    String n=raw.toLowerCase(Locale.ROOT);
                    if(!n.isEmpty()) needed.put(n,needed.getOrDefault(n,0.0)-old.getDouble(1));
                }
                old.close();
            }
            if(ls!=null) for(Line l:ls){
                String raw=l.name==null?"":l.name.trim();
                String n=raw.toLowerCase(Locale.ROOT);
                if(!n.isEmpty()){
                    needed.put(n,needed.getOrDefault(n,0.0)+Math.max(0,l.qty));
                    labels.put(n,raw);
                }
            }
            StringBuilder w=new StringBuilder();
            for(Map.Entry<String,Double> e:needed.entrySet()){
                if(e.getValue()<=0) continue;
                Cursor q=d.rawQuery("SELECT COALESCE(qty,0) FROM items WHERE lower(trim(name))=? LIMIT 1",new String[]{e.getKey()});
                boolean exists=q.moveToFirst();
                double stock=exists?q.getDouble(0):0;
                q.close();
                if(!exists || stock+0.0001<e.getValue()){
                    if(w.length()>0) w.append("\n");
                    w.append(labels.getOrDefault(e.getKey(),e.getKey()))
                     .append(": المتوفر ").append(fmt(stock))
                     .append("، المطلوب ").append(fmt(e.getValue()))
                     .append(" — سيتم تسجيل العجز بالسالب.");
                }
            }
            return w.toString();
        }
        void revertStockFromInvoice(long invoiceId){
            if(invoiceId<=0)return;
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=null;
            try{
                c=d.rawQuery("SELECT name,qty FROM invoice_items WHERE invoice_id=?",new String[]{String.valueOf(invoiceId)});
                while(c.moveToNext()){
                    String name=c.getString(0)==null?"":c.getString(0).trim();
                    double q=c.getDouble(1);
                    if(name.isEmpty()||q<=0)continue;
                    Cursor ic=d.rawQuery("SELECT id,qty FROM items WHERE lower(trim(name))=? LIMIT 1",new String[]{name.toLowerCase(Locale.ROOT)});
                    if(ic.moveToFirst()){
                        long iid=ic.getLong(0); double current=ic.getDouble(1);
                        ContentValues v=new ContentValues(); v.put("qty",current+q);
                        d.update("items",v,"id=?",new String[]{String.valueOf(iid)});
                        ContentValues mv=new ContentValues(); mv.put("item_id",iid); mv.put("item_name",name); mv.put("qty",q); mv.put("source_type","sale_reversal"); mv.put("source_id",invoiceId); mv.put("created_at",now());
                        d.insert("stock_movements",null,mv);
                    }
                    ic.close();
                }
            }finally{
                if(c!=null)c.close();
            }
        }
        boolean applyStockFromSale(ArrayList<Line> ls,long invoiceId){
            if(ls==null||ls.isEmpty())return true;
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=null;
            try{
                for(Line l:ls){
                    String name=l.name==null?"":l.name.trim(); double q=Math.max(0,l.qty);
                    if(name.isEmpty()||q<=0)continue;
                    c=d.rawQuery("SELECT id,qty FROM items WHERE lower(trim(name))=? LIMIT 1",new String[]{name.toLowerCase(Locale.ROOT)});
                    if(!c.moveToFirst()){
                        c.close(); c=null;
                        ContentValues nv=new ContentValues();
                        nv.put("name",name); nv.put("qty",-q); nv.put("min_qty",0); nv.put("cost",0); nv.put("sale",0);
                        long iid=d.insert("items",null,nv);
                        if(iid>0){
                            ContentValues mv=new ContentValues(); mv.put("item_id",iid); mv.put("item_name",name); mv.put("qty",-q); mv.put("unit_cost",0); mv.put("source_type","sale"); mv.put("source_id",invoiceId); mv.put("created_at",now());
                            d.insert("stock_movements",null,mv);
                        }
                        continue;
                    }
                    long iid=c.getLong(0); double current=c.getDouble(1); c.close(); c=null;
                    ContentValues v=new ContentValues(); v.put("qty",current-q);
                    d.update("items",v,"id=?",new String[]{String.valueOf(iid)});
                    ContentValues mv=new ContentValues(); mv.put("item_id",iid); mv.put("item_name",name); mv.put("qty",-q); mv.put("unit_cost",itemCostPrice(name)); mv.put("source_type","sale"); mv.put("source_id",invoiceId); mv.put("created_at",now());
                    d.insert("stock_movements",null,mv);
                }
                return true;
            }finally{
                if(c!=null)c.close();
            }
        }
        String[] itemNames(){Cursor c=getReadableDatabase().rawQuery("SELECT name FROM items ORDER BY name",null);ArrayList<String>a=new ArrayList<>();while(c.moveToNext())a.add(c.getString(0));c.close();return a.toArray(new String[0]);}
        double itemSalePrice(String n){if(n==null||n.trim().isEmpty())return 0;Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(sale,0) FROM items WHERE name=? LIMIT 1",new String[]{n.trim()});double p=c.moveToFirst()?c.getDouble(0):0;c.close();return p;}
        double itemCostPrice(String n){if(n==null||n.trim().isEmpty())return 0;Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(cost,0) FROM items WHERE name=? LIMIT 1",new String[]{n.trim()});double p=c.moveToFirst()?c.getDouble(0):0;c.close();return p;}
        double itemQty(String n){if(n==null||n.trim().isEmpty())return 0;Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(qty,0) FROM items WHERE name=? LIMIT 1",new String[]{n.trim()});double q=c.moveToFirst()?c.getDouble(0):0;c.close();return q;}
        void learnItemPrice(String name, double sale, double cost){
            if(name==null||name.trim().isEmpty())return;
            String n=name.trim();
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=d.rawQuery("SELECT id FROM items WHERE name=? LIMIT 1",new String[]{n});
            if(c.moveToFirst()){
                long id=c.getLong(0);c.close();
                ContentValues v=new ContentValues();
                if(sale>0) v.put("sale",sale);
                if(cost>0) v.put("cost",cost);
                if(v.size()>0) d.update("items",v,"id=?",new String[]{String.valueOf(id)});
            }else{
                c.close();
                ContentValues v=new ContentValues();
                v.put("name",n);v.put("qty",0);v.put("min_qty",0);v.put("cost",cost);v.put("sale",sale);
                d.insert("items",null,v);
            }
        }
        Cursor items(){return getReadableDatabase().rawQuery("SELECT id,name,qty,min_qty,cost,sale FROM items ORDER BY name",null);}
        boolean itemExists(String n){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM items WHERE name=? LIMIT 1",new String[]{n});boolean x=c.moveToFirst();c.close();return x;}
        void addItem(String n,double q,double m){if(n.isEmpty()||q<0||m<0)throw new IllegalArgumentException();SQLiteDatabase d=getWritableDatabase();Cursor c=d.rawQuery("SELECT id FROM items WHERE name=? LIMIT 1",new String[]{n});if(c.moveToFirst()){long id=c.getLong(0);c.close();ContentValues v=new ContentValues();v.put("qty",q);v.put("min_qty",m);d.update("items",v,"id=?",new String[]{String.valueOf(id)});return;}c.close();ContentValues v=new ContentValues();v.put("name",n);v.put("qty",q);v.put("min_qty",m);d.insert("items",null,v);}
        void updateItem(long id,String n,double q,double m){if(id<1||n==null||n.trim().isEmpty()||q<0||m<0)throw new IllegalArgumentException();ContentValues v=new ContentValues();v.put("name",n.trim());v.put("qty",q);v.put("min_qty",m);getWritableDatabase().update("items",v,"id=?",new String[]{String.valueOf(id)});}
        void deleteItem(long id){if(id>0)getWritableDatabase().delete("items","id=?",new String[]{String.valueOf(id)});}
        int transactionCount(long id){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transactions WHERE customer_id=?",new String[]{String.valueOf(id)});int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;}
        String invoiceNoFromTransaction(String details){if(details==null)return "";String p="فاتورة مبيعات رقم ";return details.startsWith(p)?details.substring(p.length()).trim():"";}
        String invoiceCompactDetails(String no){Cursor c=getReadableDatabase().rawQuery("SELECT name,qty,total FROM invoice_items WHERE invoice_id=(SELECT id FROM invoices WHERE no=? ORDER BY id DESC LIMIT 1) ORDER BY id",new String[]{no});StringBuilder s=new StringBuilder("تفاصيل: ");int n=0;while(c.moveToNext()&&n<6){if(n>0)s.append(" • ");s.append(c.getString(0)).append(" × ").append(fmt(c.getDouble(1))).append(" = ").append(fmt(c.getDouble(2)));n++;}c.close();return n==0?"تفاصيل الفاتورة غير متاحة":s.toString();}
        Cursor purchaseLines(long id){return getReadableDatabase().rawQuery("SELECT id,name,qty,cost,sale,total FROM purchase_items WHERE purchase_id=? ORDER BY id",new String[]{String.valueOf(id)});}
        String purchaseSupplier(long id){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(supplier,'') FROM purchase_invoices WHERE id=?",new String[]{String.valueOf(id)});String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
        String purchaseNo(long id){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(no,'') FROM purchase_invoices WHERE id=?",new String[]{String.valueOf(id)});String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
        void updatePurchase(long id,String no,String supplier,double total){ContentValues v=new ContentValues();v.put("no",no);v.put("supplier",supplier);v.put("total",total);getWritableDatabase().update("purchase_invoices",v,"id=?",new String[]{String.valueOf(id)});}
        void revertStockFromPurchase(long purchaseId){
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=d.rawQuery("SELECT name,qty FROM purchase_items WHERE purchase_id=?",new String[]{String.valueOf(purchaseId)});
            while(c.moveToNext()){
                String name=c.getString(0);
                double q=c.getDouble(1);
                Cursor ic=d.rawQuery("SELECT id,qty FROM items WHERE name=? LIMIT 1",new String[]{name});
                if(ic.moveToFirst()){
                    long iid=ic.getLong(0);
                    double currentQ=ic.getDouble(1);
                    ContentValues v=new ContentValues();
                    v.put("qty",Math.max(0,currentQ-q));
                    d.update("items",v,"id=?",new String[]{String.valueOf(iid)});
                }
                ic.close();
            }
            c.close();
            d.delete("purchase_items","purchase_id=?",new String[]{String.valueOf(purchaseId)});
        }
        void deletePurchase(long id){if(id<=0)return;revertStockFromPurchase(id);getWritableDatabase().delete("purchase_invoices","id=?",new String[]{String.valueOf(id)});}
        String supplierPhoneByName(String n){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(phone,'') FROM suppliers WHERE name=? ORDER BY id DESC LIMIT 1",new String[]{n==null?"":n});String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
        Cursor invoices(){return getReadableDatabase().rawQuery("SELECT id,no,customer,total,date FROM invoices ORDER BY datetime(date) DESC, id DESC LIMIT 100",null);}
        Cursor recentActivity(){
            android.database.MatrixCursor out=new android.database.MatrixCursor(
                new String[]{"kind","ref","title","amount","date","sort_id","operation_type"});
            ArrayList<Object[]> rows=new ArrayList<>();
            SQLiteDatabase d=getReadableDatabase();
            Cursor inv=null,tr=null;
            try{
                inv=d.rawQuery("SELECT id,no,customer,COALESCE(total,0),COALESCE(date,''),id FROM invoices",null);
                while(inv.moveToNext()){
                    long id=inv.getLong(0);
                    String no=inv.getString(1)==null?"":inv.getString(1);
                    String customer=inv.getString(2);
                    if(customer==null||customer.trim().isEmpty())customer="نقدي";
                    rows.add(new Object[]{1,no,"فاتورة "+no+" • "+customer,inv.getDouble(3),inv.getString(4),id,0});
                }
            }finally{if(inv!=null)inv.close();}
            try{
                tr=d.rawQuery("SELECT t.id,t.details,COALESCE(t.amount,0),COALESCE(t.date,''),t.customer_id,c.name,t.type FROM transactions t LEFT JOIN customers c ON c.id=t.customer_id",null);
                while(tr.moveToNext()){
                    String details=tr.getString(1);
                    // قيد الفاتورة يُعرض مرة واحدة كفاتورة، وليس كحركة إضافية.
                    if(details!=null && (details.startsWith("فاتورة مبيعات رقم ") || details.startsWith("دفعة فاتورة رقم "))) continue;
                    String customer=tr.getString(5);
                    if(customer==null)customer="";
                    String title=(details==null||details.trim().isEmpty()?"عملية مالية":details.trim());
                    if(!customer.trim().isEmpty()) title+=" • "+customer.trim();
                    rows.add(new Object[]{2,String.valueOf(tr.getLong(0)),title,tr.getDouble(2),tr.getString(3),tr.getLong(0),tr.getInt(6)});
                }
            }finally{if(tr!=null)tr.close();}
            Cursor pinv=null;
            try{
                pinv=d.rawQuery("SELECT id,no,supplier,COALESCE(total,0),COALESCE(date,''),id FROM purchase_invoices",null);
                while(pinv.moveToNext()){
                    long id=pinv.getLong(0);String no=pinv.getString(1)==null?"":pinv.getString(1);String supplier=pinv.getString(2);
                    if(supplier==null||supplier.trim().isEmpty())supplier="بدون مورد";
                    rows.add(new Object[]{3,no,"فاتورة شراء "+no+" • "+supplier,pinv.getDouble(3),pinv.getString(4),id,0});
                }
            }finally{if(pinv!=null)pinv.close();}
            Collections.sort(rows,(a,b)->{
                String da=(String)a[4], dbb=(String)b[4];
                int x=dbb.compareTo(da);
                if(x!=0)return x;
                return Long.compare((Long)b[5],(Long)a[5]);
            });
            int n=Math.min(200,rows.size());
            for(int i=0;i<n;i++) out.addRow(rows.get(i));
            return out;
        }
        long transactionIdForInvoice(String no){
            Cursor c=getReadableDatabase().rawQuery("SELECT id FROM transactions WHERE details=? ORDER BY id DESC LIMIT 1",new String[]{"فاتورة مبيعات رقم "+no});
            long x=c.moveToFirst()?c.getLong(0):-1;c.close();return x;
        }
        double balanceAfterTransaction(long tid){
            Cursor c=getReadableDatabase().rawQuery("SELECT customer_id,type,amount FROM transactions WHERE id=?",new String[]{String.valueOf(tid)});
            if(!c.moveToFirst()){c.close();return 0;}
            long cid=c.getLong(0);int type=c.getInt(1);double amount=c.getDouble(2);c.close();
            double current=balance(cid);
            // الحساب الحالي = الرصيد بعد الحركة. نعيد طرح/إضافة الحركات الأحدث حتى لحظة العملية.
            Cursor newer=getReadableDatabase().rawQuery("SELECT type,amount FROM transactions WHERE customer_id=? AND id>?",new String[]{String.valueOf(cid),String.valueOf(tid)});
            while(newer.moveToNext()) current-=(newer.getInt(0)==1?newer.getDouble(1):-newer.getDouble(1));
            newer.close();
            return current;
        }
        String customerNameById(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT name FROM customers WHERE id=?",new String[]{String.valueOf(id)});
            String x=c.moveToFirst()?c.getString(0):"";c.close();return x;
        }
        double invoicePaid(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(paid,0) FROM invoices WHERE id=?",new String[]{String.valueOf(id)});
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        String invoiceDate(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(date,'') FROM invoices WHERE id=?",new String[]{String.valueOf(id)});
            String x=c.moveToFirst()?c.getString(0):"";c.close();return x;
        }
        int invoiceCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM invoices",null);int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;}
        int customerCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM customers",null);int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;}
        double sales(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM invoices",null);double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double todaySales(){
            String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+"%";
            Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM invoices WHERE date LIKE ?",new String[]{today});
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        int todayInvoiceCount(){
            String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+"%";
            Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM invoices WHERE date LIKE ?",new String[]{today});
            int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;
        }
        int lowStockCount(){
            Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE qty<=min_qty AND min_qty>0",null);
            int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;
        }
        Cursor lowStockItems(){
            return getReadableDatabase().rawQuery("SELECT id,name,qty,min_qty,sale,cost FROM items WHERE qty<=min_qty AND min_qty>0 ORDER BY qty ASC",null);
        }
        String[] customerNames(){Cursor c=getReadableDatabase().rawQuery("SELECT name FROM customers ORDER BY name",null);ArrayList<String>a=new ArrayList<>();while(c.moveToNext())a.add(c.getString(0));c.close();return a.toArray(new String[0]);}
        long customerIdByName(String n){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM customers WHERE name=? ORDER BY id DESC LIMIT 1",new String[]{n});long x=c.moveToFirst()?c.getLong(0):-1;c.close();return x;}
        double invoiceTotal(long id){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(total,0) FROM invoices WHERE id=?",new String[]{String.valueOf(id)});double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double invoiceTotalByNo(String no){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(total,0) FROM invoices WHERE no=? ORDER BY id DESC LIMIT 1",new String[]{no});double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double invoicePaidByNo(String no){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(paid,0) FROM invoices WHERE no=? ORDER BY id DESC LIMIT 1",new String[]{no});double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        String phoneByName(String n){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(phone,'') FROM customers WHERE name=? LIMIT 1",new String[]{n});String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
        long customer(String n,String p){
            String name=n==null?"":n.trim().replaceAll("\\s+"," "); if(name.isEmpty()) return -1;
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=d.rawQuery("SELECT id FROM customers WHERE lower(trim(name))=lower(?) ORDER BY id ASC LIMIT 1",new String[]{name});
            if(c.moveToFirst()){long x=c.getLong(0);c.close();if(p!=null&&!p.trim().isEmpty()){ContentValues v=new ContentValues();v.put("phone",p.trim());d.update("customers",v,"id=?",new String[]{String.valueOf(x)});}return x;}c.close();
            ContentValues v=new ContentValues();v.put("name",name);v.put("phone",p==null?"":p.trim());return d.insert("customers",null,v);
        }
        double balanceByName(String n){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM customers WHERE name=? ORDER BY id DESC LIMIT 1",new String[]{n});if(!c.moveToFirst()){c.close();return 0;}long id=c.getLong(0);c.close();return balance(id);}
        String invoiceNo(long id){Cursor c=getReadableDatabase().rawQuery("SELECT no FROM invoices WHERE id=?",new String[]{String.valueOf(id)});String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
        String invoiceCustomer(long id){Cursor c=getReadableDatabase().rawQuery("SELECT customer FROM invoices WHERE id=?",new String[]{String.valueOf(id)});String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
        void updateInvoice(long id,String no,String customer,double total,double paid,String date){ContentValues v=new ContentValues();v.put("no",no);v.put("customer",customer);v.put("total",total);v.put("paid",paid);v.put("date",date);getWritableDatabase().update("invoices",v,"id=?",new String[]{String.valueOf(id)});}
        Cursor invoiceLines(long id){return getReadableDatabase().rawQuery("SELECT id,name,qty,total FROM invoice_items WHERE invoice_id=? ORDER BY id",new String[]{String.valueOf(id)});}
        void replaceInvoiceLines(long id,ArrayList<Line> ls){SQLiteDatabase d=getWritableDatabase();d.delete("invoice_items","invoice_id=?",new String[]{String.valueOf(id)});for(Line l:ls){ContentValues v=new ContentValues();v.put("invoice_id",id);v.put("name",l.name);v.put("qty",l.qty);v.put("total",l.total);v.put("unit_cost",itemCostPrice(l.name));d.insert("invoice_items",null,v);}}
        void deleteInvoice(long id){
            if(id<=0)return;
            String no=invoiceNo(id);
            revertStockFromInvoice(id);
            deleteInvoiceTransactions(no);
            SQLiteDatabase d=getWritableDatabase();
            d.delete("invoice_items","invoice_id=?",new String[]{String.valueOf(id)});
            d.delete("invoices","id=?",new String[]{String.valueOf(id)});
            d.delete("stock_movements","source_id=? AND source_type IN ('sale','sale_reversal')",new String[]{String.valueOf(id)});
        }
        void deleteInvoiceTransaction(String no){deleteInvoiceTransactions(no);}
        void deleteInvoiceTransactions(String no){
            SQLiteDatabase d=getWritableDatabase();
            String clean=no==null?"":no.trim();
            d.delete("transactions","details=? OR details=? OR details=? OR details=?",
                new String[]{"فاتورة مبيعات رقم "+clean,"دفعة فاتورة رقم "+clean,
                             "فاتورة مبيعات رقم "+no,"دفعة فاتورة رقم "+no});
        }
        void addPaymentTransaction(long id,double a,String details,String date){if(id<1||a<=0)return;addTransaction(id,a,details,0,date);}
        void addTransactionOnce(long id,double a,String details,String date){if(id>0)addTransaction(id,a,details,1,date);}
        int nextInvoice(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(CAST(no AS INTEGER)),0)+1 FROM invoices",null);int x=c.moveToFirst()?c.getInt(0):1;c.close();return x;}
        long invoiceIdByNo(String no){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM invoices WHERE no=? ORDER BY id DESC LIMIT 1",new String[]{no});long x=c.moveToFirst()?c.getLong(0):-1;c.close();return x;}
        Cursor transactionById(long id){return getReadableDatabase().rawQuery("SELECT id,customer_id,date,details,amount,type FROM transactions WHERE id=?",new String[]{String.valueOf(id)});}
        void updateTransaction(long id,double amount,String details,int type,String date){ContentValues v=new ContentValues();v.put("amount",amount);v.put("details",details);v.put("type",type);v.put("date",date);getWritableDatabase().update("transactions",v,"id=?",new String[]{String.valueOf(id)});}
        void deleteTransaction(long id){getWritableDatabase().delete("transactions","id=?",new String[]{String.valueOf(id)});}
        void deleteCustomer(long id){
            SQLiteDatabase d=getWritableDatabase();
            Cursor c=d.rawQuery("SELECT id FROM invoices WHERE customer=(SELECT name FROM customers WHERE id=?)",new String[]{String.valueOf(id)});
            ArrayList<Long> invoiceIds=new ArrayList<>();while(c.moveToNext())invoiceIds.add(c.getLong(0));c.close();
            d.delete("transactions","customer_id=?",new String[]{String.valueOf(id)});
            // حذف فواتير العميل عبر المسار الكامل حتى يُعاد المخزون قبل حذفها.
            for(Long iid:invoiceIds)deleteInvoice(iid);
            d.delete("customers","id=?",new String[]{String.valueOf(id)});
        }
    }

// Restored existing helpers and account/invoice actions.

    void sectionInside(LinearLayout box,String title){
        TextView v=tv(title,11);v.setTextColor(GREEN);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(v,new LinearLayout.LayoutParams(-1,dp(26)));
    }


    void invoiceHistory(){
        base("سجل الفواتير");

        // زر عائم مدور لإضافة فاتورة بيع جديدة
        if(root.getChildCount()>1){
            View sv=root.getChildAt(1);
            int svIdx=root.indexOfChild(sv);
            if(svIdx>=0){
                root.removeViewAt(svIdx);

                FrameLayout frame=new FrameLayout(this);
                frame.addView(sv,new FrameLayout.LayoutParams(-1,-1));

                Button fab=new Button(this);
                fab.setText("＋");
                fab.setTextSize(26);
                fab.setTextColor(Color.WHITE);
                fab.setGravity(Gravity.CENTER);
                fab.setIncludeFontPadding(false);
                GradientDrawable fabBg=new GradientDrawable();
                fabBg.setShape(GradientDrawable.OVAL);
                fabBg.setColor(GREEN);
                if(Build.VERSION.SDK_INT>=21){
                    fab.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(180,240,200)),fabBg,null));
                }else{
                    fab.setBackground(fabBg);
                }
                fab.setElevation(dp(8));
                fab.setContentDescription("إضافة فاتورة بيع جديدة");
                fab.setOnClickListener(v->invoice());

                FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(54),dp(54));
                fp.gravity=Gravity.BOTTOM|Gravity.LEFT;
                fp.setMargins(dp(16),0,dp(16),dp(14));
                frame.addView(fab,fp);

                root.addView(frame,svIdx,new LinearLayout.LayoutParams(-1,0,1));
                content.setPadding(dp(5),dp(4),dp(5),dp(74));
            }
        }

        // شريط الإحصائيات السريع
        int totalInvoices=db.invoiceCount();
        double totalSales=db.sales();

        LinearLayout statsCard=new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.HORIZONTAL);
        statsCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        statsCard.setPadding(dp(12),dp(8),dp(12),dp(8));
        statsCard.setBackground(outlined(CARD,1,12));

        TextView cntTv=tv("🧾 عدد الفواتير:\n"+totalInvoices+" فاتورة",11.5f);
        cntTv.setTextColor(GREEN); cntTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); cntTv.setGravity(Gravity.CENTER);
        statsCard.addView(cntTv,new LinearLayout.LayoutParams(0,-2,1));

        TextView sumTv=tv("💰 إجمالي المبيعات:\n"+fmt(totalSales)+" ريال",11.5f);
        sumTv.setTextColor(TEXT); sumTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); sumTv.setGravity(Gravity.CENTER);
        statsCard.addView(sumTv,new LinearLayout.LayoutParams(0,-2,1));

        content.addView(statsCard,new LinearLayout.LayoutParams(-1,-2));
        addSpace(6);

        // شريط البحث في الفواتير
        EditText search=field("🔍 بحث برقم الفاتورة أو اسم العميل...");
        content.addView(search,new LinearLayout.LayoutParams(-1,dp(42)));
        addSpace(6);

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list,new LinearLayout.LayoutParams(-1,-2));

        final Runnable[] refreshList=new Runnable[1];
        refreshList[0]=()->{
            list.removeAllViews();
            String query=search.getText().toString().trim().toLowerCase();
            Cursor c=db.invoices();
            int displayedCount=0;
            while(c.moveToNext()){
                long id=c.getLong(0);
                String no=c.getString(1);
                String cn=c.getString(2);
                double total=c.getDouble(3);
                String date=c.getString(4);

                String customerName=cn==null||cn.isEmpty()?"نقدي":cn;
                if(!query.isEmpty() && !no.toLowerCase().contains(query) && !customerName.toLowerCase().contains(query)){
                    continue;
                }
                displayedCount++;

                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(10),dp(8),dp(10),dp(8));
                card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                GradientDrawable cBg=new GradientDrawable();
                cBg.setColor(CARD);
                cBg.setCornerRadius(dp(12));
                cBg.setStroke(dp(1),Color.rgb(222,230,224));
                card.setBackground(cBg);
                card.setElevation(dp(2));

                // السطر الأول: رقم الفاتورة + العميل + المبلغ
                LinearLayout topRow=new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                topRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView badge=tv("#"+no,11.5f);
                badge.setTextColor(GREEN); badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD); badge.setGravity(Gravity.CENTER);
                GradientDrawable bBg=new GradientDrawable();
                bBg.setColor(Color.rgb(240,248,242));
                bBg.setCornerRadius(dp(8));
                bBg.setStroke(dp(1),Color.rgb(190,225,200));
                badge.setBackground(bBg);
                topRow.addView(badge,new LinearLayout.LayoutParams(dp(54),dp(28)));

                LinearLayout infoCol=new LinearLayout(this);
                infoCol.setOrientation(LinearLayout.VERTICAL);
                infoCol.setPadding(dp(8),0,dp(8),0);

                TextView nameTv=tv("العميل: "+customerName,13);
                nameTv.setTextColor(TEXT); nameTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                nameTv.setMaxLines(1);
                infoCol.addView(nameTv,new LinearLayout.LayoutParams(-1,dp(20)));

                TextView dateTv=tv("📅 "+date,10);
                dateTv.setTextColor(MUTED); dateTv.setMaxLines(1);
                infoCol.addView(dateTv,new LinearLayout.LayoutParams(-1,dp(16)));

                topRow.addView(infoCol,new LinearLayout.LayoutParams(0,-2,1));

                TextView totalTv=tv(fmt(total)+" ر.ي",14);
                totalTv.setTextColor(GREEN); totalTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                topRow.addView(totalTv,new LinearLayout.LayoutParams(-2,-2));

                card.addView(topRow,new LinearLayout.LayoutParams(-1,-2));
                addSpaceTo(card,6);

                // السطر الثاني: أزرار الإجراءات
                LinearLayout actionRow=new LinearLayout(this);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                Button viewBtn=button("👁️ عرض");
                viewBtn.setTextSize(11.5f); viewBtn.setTextColor(GREEN); viewBtn.setBackground(outline(CARD,8));
                viewBtn.setOnClickListener(v->showInvoiceDialog(id,no,cn,total,date));

                Button editBtn=button("✏️ تعديل");
                editBtn.setTextSize(11.5f); editBtn.setTextColor(BLUE); editBtn.setBackground(outline(CARD,8));
                editBtn.setOnClickListener(v->invoice(true,id));

                Button delBtn=button("🗑️ حذف");
                delBtn.setTextSize(11.5f); delBtn.setTextColor(RED); delBtn.setBackground(outline(CARD,8));
                delBtn.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("حذف الفاتورة رقم "+no)
                    .setMessage("سيتم حذف الفاتورة وجميع قيودها المرتبطة بحساب العميل.")
                    .setPositiveButton("حذف",(d,w)->{db.deleteInvoice(id); refreshList[0].run();})
                    .setNegativeButton("إلغاء",null).show());

                actionRow.addView(viewBtn,new LinearLayout.LayoutParams(0,dp(32),1));
                LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(0,dp(32),1); elp.setMargins(dp(4),0,0,0);
                actionRow.addView(editBtn,elp);
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(32),1); dlp.setMargins(dp(4),0,0,0);
                actionRow.addView(delBtn,dlp);

                card.addView(actionRow,new LinearLayout.LayoutParams(-1,dp(46)));

                card.setOnClickListener(v->showInvoiceDialog(id,no,cn,total,date));

                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,0,0,dp(6));
                list.addView(card,lp);
            }
            c.close();

            if(displayedCount==0){
                LinearLayout emptyBox=card();
                emptyBox.setOrientation(LinearLayout.VERTICAL);
                emptyBox.setPadding(dp(16),dp(16),dp(16),dp(16));
                emptyBox.setGravity(Gravity.CENTER);
                TextView ei=tv("🧾",28); ei.setGravity(Gravity.CENTER);
                emptyBox.addView(ei,new LinearLayout.LayoutParams(-1,dp(48)));
                TextView em=tv(query.isEmpty()?"لا توجد فواتير مبيعات مسجلة حتى الآن":"لا توجد نتائج مطابقة للبحث",12.5f);
                em.setTextColor(MUTED); em.setGravity(Gravity.CENTER);
                emptyBox.addView(em,new LinearLayout.LayoutParams(-1,dp(24)));
                list.addView(emptyBox,new LinearLayout.LayoutParams(-1,-2));
            }
        };

        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){refreshList[0].run();}
            public void afterTextChanged(android.text.Editable e){}
        });

        refreshList[0].run();
    }


    void showInvoiceDialog(long id,String no,String customer,double total,String date){
        final Dialog dlg=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(12));
        box.setBackground(rounded(CARD,dp(18)));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Header Banner
        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView iconBadge=tv("🧾",20);
        iconBadge.setGravity(Gravity.CENTER);
        head.addView(iconBadge,new LinearLayout.LayoutParams(dp(36),dp(36)));

        LinearLayout headTitles=new LinearLayout(this);
        headTitles.setOrientation(LinearLayout.VERTICAL);
        headTitles.setPadding(dp(6),0,dp(6),0);

        TextView tTitle=tv("فاتورة مبيعات #"+no,15);
        tTitle.setTextColor(GREEN); tTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView tCustomer=tv("العميل: "+(customer==null||customer.trim().isEmpty()?"نقدي":customer)+"  •  "+date,11);
        tCustomer.setTextColor(TEXT);
        headTitles.addView(tTitle,new LinearLayout.LayoutParams(-1,dp(22)));
        headTitles.addView(tCustomer,new LinearLayout.LayoutParams(-1,dp(18)));
        head.addView(headTitles,new LinearLayout.LayoutParams(0,dp(52),1));

        Button closeBtn=button("✕");
        closeBtn.setTextColor(MUTED); closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v->dlg.dismiss());
        head.addView(closeBtn,new LinearLayout.LayoutParams(dp(36),dp(36)));
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(44)));
        addSpaceTo(box,6);

        // Total Amount Banner
        LinearLayout amountCard=new LinearLayout(this);
        amountCard.setOrientation(LinearLayout.VERTICAL);
        amountCard.setGravity(Gravity.CENTER);
        amountCard.setPadding(dp(10),dp(6),dp(10),dp(6));
        GradientDrawable acBg=new GradientDrawable();
        acBg.setColor(Color.rgb(240,249,242));
        acBg.setCornerRadius(dp(12));
        acBg.setStroke(dp(1),Color.rgb(190,235,205));
        amountCard.setBackground(acBg);

        TextView amtVal=tv("الإجمالي: "+fmt(total)+" ريال",16);
        amtVal.setTextColor(GREEN); amtVal.setTypeface(Typeface.DEFAULT,Typeface.BOLD); amtVal.setGravity(Gravity.CENTER);
        amountCard.addView(amtVal,new LinearLayout.LayoutParams(-1,dp(26)));
        box.addView(amountCard,new LinearLayout.LayoutParams(-1,dp(52)));
        addSpaceTo(box,6);

        // Items List
        ScrollView scrollBody=new ScrollView(this);
        LinearLayout itemsBody=new LinearLayout(this);
        itemsBody.setOrientation(LinearLayout.VERTICAL);
        itemsBody.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView itemsTitle=tv("أصناف الفاتورة:",12);
        itemsTitle.setTextColor(GREEN); itemsTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        itemsBody.addView(itemsTitle,new LinearLayout.LayoutParams(-1,dp(22)));

        Cursor lines=db.invoiceLines(id);
        int count=0;
        final ArrayList<Line> lineList=new ArrayList<>();
        while(lines.moveToNext()){
            String n=lines.getString(1);
            double q=lines.getDouble(2), t=lines.getDouble(3);
            lineList.add(new Line(n,q,t));

            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setPadding(dp(6),dp(4),dp(6),dp(4));
            row.setBackground(outline(Color.rgb(248,250,248),8));

            TextView nTv=tv(n,12); nTv.setTextColor(TEXT);            row.addView(nTv,new LinearLayout.LayoutParams(0,-2,1.2f));

            TextView qTv=tv("× "+fmt(q),11); qTv.setTextColor(MUTED); qTv.setGravity(Gravity.CENTER);
            row.addView(qTv,new LinearLayout.LayoutParams(0,-2,0.6f));

            TextView tTv=tv(fmt(t)+" ر.ي",12); tTv.setTextColor(GREEN); tTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); tTv.setGravity(Gravity.LEFT);
            row.addView(tTv,new LinearLayout.LayoutParams(0,-2,0.8f));

            itemsBody.addView(row,new LinearLayout.LayoutParams(-1,-2));
            addSpaceTo(itemsBody,3);
            count++;
        }
        lines.close();

        if(count==0){
            TextView emptyTv=tv("لا توجد تفاصيل أصناف محفوظة لهذه الفاتورة",11);
            emptyTv.setTextColor(MUTED); emptyTv.setGravity(Gravity.CENTER);
            itemsBody.addView(emptyTv,new LinearLayout.LayoutParams(-1,dp(30)));
        }

        scrollBody.addView(itemsBody);
        box.addView(scrollBody,new LinearLayout.LayoutParams(-1,0,1));
        addSpaceTo(box,8);

        final double paid=db.invoicePaid(id);
        long cid=customer==null||customer.trim().isEmpty()?-1:db.customerIdByName(customer.trim());
        final double currentBal=cid>0?db.balance(cid):0;

        // Action Buttons: Share, PDF, Image, SMS, Print, Edit, Delete
        LinearLayout actionsGrid=new LinearLayout(this);
        actionsGrid.setOrientation(LinearLayout.VERTICAL);
        actionsGrid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Row 1: WhatsApp, PDF, Image, SMS
        LinearLayout row1=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button shareBtn=button("📲 واتساب");
        shareBtn.setTextColor(Color.WHITE); shareBtn.setBackground(rounded(GREEN,dp(8)));
        shareBtn.setTextSize(11f);
        shareBtn.setOnClickListener(v->{
            dlg.dismiss();
            shareReceiptImageAndText(no,customer,lineList,total,paid);
        });

        Button pdfBtn=button("📄 PDF");
        pdfBtn.setTextColor(Color.rgb(180,40,40)); pdfBtn.setBackground(outline(CARD,8));
        pdfBtn.setTextSize(11f);
        pdfBtn.setOnClickListener(v->{
            dlg.dismiss();
            shareInvoicePdf(no,customer,lineList,total,paid,currentBal,date);
        });

        Button imgBtn=button("🖼️ صورة");
        imgBtn.setTextColor(Color.rgb(30,100,200)); imgBtn.setBackground(outline(CARD,8));
        imgBtn.setTextSize(11f);
        imgBtn.setOnClickListener(v->{
            dlg.dismiss();
            shareInvoiceImage(no,customer,lineList,total,paid,currentBal,date);
        });

        Button smsBtn=button("✉️ SMS");
        smsBtn.setTextColor(Color.rgb(20,100,50)); smsBtn.setBackground(outline(CARD,8));
        smsBtn.setTextSize(11f);
        smsBtn.setOnClickListener(v->{
            dlg.dismiss();
            shareInvoiceSms(no,customer,lineList,total,paid);
        });

        row1.addView(shareBtn,new LinearLayout.LayoutParams(0,dp(52),1f));
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(0,dp(52),1f);plp.setMargins(dp(4),0,0,0);
        row1.addView(pdfBtn,plp);
        actionsGrid.addView(row1,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout row1b=new LinearLayout(this);row1b.setOrientation(LinearLayout.HORIZONTAL);row1b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row1b.addView(imgBtn,new LinearLayout.LayoutParams(0,dp(52),1f));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(52),1f);slp.setMargins(dp(4),0,0,0);row1b.addView(smsBtn,slp);
        actionsGrid.addView(row1b,new LinearLayout.LayoutParams(-1,dp(42)));

        addSpaceTo(actionsGrid,4);

        // Row 2: Print, Edit, Delete
        LinearLayout row2=new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button printBtn=button("🖨️ طباعة");
        printBtn.setTextColor(GREEN); printBtn.setBackground(outline(CARD,8));
        printBtn.setTextSize(11.5f);
        printBtn.setOnClickListener(v->{
            dlg.dismiss();
            printInvoiceBluetooth(no,customer,lineList,total);
        });

        Button editBtn=button("✏️ تعديل");
        editBtn.setTextColor(BLUE); editBtn.setBackground(outline(CARD,8));
        editBtn.setTextSize(11.5f);
        editBtn.setOnClickListener(v->{
            dlg.dismiss();
            invoice(true,id);
        });

        Button delBtn=button("🗑️ حذف");
        delBtn.setTextColor(RED); delBtn.setBackground(outline(CARD,8));
        delBtn.setTextSize(11.5f);
        delBtn.setOnClickListener(v->{
            dlg.dismiss();
            confirmDeleteInvoice(id,no);
        });

        row2.addView(printBtn,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(0,dp(50),1f); elp.setMargins(dp(3),0,0,0);
        row2.addView(editBtn,elp);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(50),1f); dlp.setMargins(dp(3),0,0,0);
        row2.addView(delBtn,dlp);
        actionsGrid.addView(row2,new LinearLayout.LayoutParams(-1,dp(52)));

        box.addView(actionsGrid,new LinearLayout.LayoutParams(-1,-2));

        dlg.setContentView(box);
        if(dlg.getWindow()!=null){
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlg.getWindow().setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(20)),WindowManager.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setGravity(Gravity.CENTER);
        }
        dlg.show();
    }

    void showPurchaseInvoiceDialog(long id,String no,String supplier,double total,String date){
        final Dialog dlg=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(12));
        box.setBackground(rounded(CARD,dp(18)));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Header Banner
        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView iconBadge=tv("📦",20);
        iconBadge.setGravity(Gravity.CENTER);
        head.addView(iconBadge,new LinearLayout.LayoutParams(dp(36),dp(36)));

        LinearLayout headTitles=new LinearLayout(this);
        headTitles.setOrientation(LinearLayout.VERTICAL);
        headTitles.setPadding(dp(6),0,dp(6),0);

        TextView tTitle=tv("فاتورة شراء #"+no,15);
        tTitle.setTextColor(GOLD); tTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView tSupplier=tv("المورد: "+(supplier==null||supplier.trim().isEmpty()?"بدون مورد":supplier)+"  •  "+date,11);
        tSupplier.setTextColor(TEXT);
        headTitles.addView(tTitle,new LinearLayout.LayoutParams(-1,dp(22)));
        headTitles.addView(tSupplier,new LinearLayout.LayoutParams(-1,dp(18)));
        head.addView(headTitles,new LinearLayout.LayoutParams(0,dp(52),1));

        Button closeBtn=button("✕");
        closeBtn.setTextColor(MUTED); closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v->dlg.dismiss());
        head.addView(closeBtn,new LinearLayout.LayoutParams(dp(36),dp(36)));
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(44)));
        addSpaceTo(box,6);

        // Total Banner
        LinearLayout amountCard=new LinearLayout(this);
        amountCard.setOrientation(LinearLayout.VERTICAL);
        amountCard.setGravity(Gravity.CENTER);
        amountCard.setPadding(dp(10),dp(6),dp(10),dp(6));
        GradientDrawable acBg=new GradientDrawable();
        acBg.setColor(Color.rgb(255,250,242));
        acBg.setCornerRadius(dp(12));
        acBg.setStroke(dp(1),Color.rgb(240,215,160));
        amountCard.setBackground(acBg);

        TextView amtVal=tv("إجمالي المشتريات: "+fmt(total)+" ريال",16);
        amtVal.setTextColor(GOLD); amtVal.setTypeface(Typeface.DEFAULT,Typeface.BOLD); amtVal.setGravity(Gravity.CENTER);
        amountCard.addView(amtVal,new LinearLayout.LayoutParams(-1,dp(26)));
        box.addView(amountCard,new LinearLayout.LayoutParams(-1,dp(52)));
        addSpaceTo(box,6);

        // Items List Scroll
        ScrollView scrollBody=new ScrollView(this);
        LinearLayout itemsBody=new LinearLayout(this);
        itemsBody.setOrientation(LinearLayout.VERTICAL);
        itemsBody.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView itemsTitle=tv("أصناف فاتورة الشراء:",12);
        itemsTitle.setTextColor(GOLD); itemsTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        itemsBody.addView(itemsTitle,new LinearLayout.LayoutParams(-1,dp(22)));

        Cursor c=db.purchaseLines(id);
        int count=0;
        while(c.moveToNext()){
            String n=c.getString(1);
            double q=c.getDouble(2),cost=c.getDouble(3),sale=c.getDouble(4),t=c.getDouble(5);

            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setPadding(dp(8),dp(5),dp(8),dp(5));
            row.setBackground(outline(Color.rgb(248,250,248),8));

            LinearLayout rowTop=new LinearLayout(this);
            rowTop.setOrientation(LinearLayout.HORIZONTAL);
            rowTop.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TextView nTv=tv(n,12); nTv.setTextColor(TEXT); nTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            rowTop.addView(nTv,new LinearLayout.LayoutParams(0,-2,1.2f));

            TextView qTv=tv("× "+fmt(q),11.5f); qTv.setTextColor(MUTED); qTv.setGravity(Gravity.CENTER);
            rowTop.addView(qTv,new LinearLayout.LayoutParams(0,-2,0.6f));

            TextView tTv=tv(fmt(t)+" ر.ي",12.5f); tTv.setTextColor(GOLD); tTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); tTv.setGravity(Gravity.LEFT);
            rowTop.addView(tTv,new LinearLayout.LayoutParams(0,-2,0.8f));
            row.addView(rowTop,new LinearLayout.LayoutParams(-1,-2));

            TextView pInfo=tv("تكلفة الوحدة: "+fmt(cost)+" ر.ي  •  سعر البيع المقترح: "+fmt(sale)+" ر.ي",10);
            pInfo.setTextColor(MUTED);
            row.addView(pInfo,new LinearLayout.LayoutParams(-1,-2));

            itemsBody.addView(row,new LinearLayout.LayoutParams(-1,-2));
            addSpaceTo(itemsBody,4);
            count++;
        }
        c.close();

        if(count==0){
            TextView emptyTv=tv("لا توجد تفاصيل أصناف محفوظة لهذه الفاتورة",11);
            emptyTv.setTextColor(MUTED); emptyTv.setGravity(Gravity.CENTER);
            itemsBody.addView(emptyTv,new LinearLayout.LayoutParams(-1,dp(30)));
        }

        scrollBody.addView(itemsBody);
        box.addView(scrollBody,new LinearLayout.LayoutParams(-1,0,1));
        addSpaceTo(box,8);

        // Action Buttons: WhatsApp, PDF, Image, SMS, Print, Edit, Delete
        LinearLayout actionsGrid=new LinearLayout(this);
        actionsGrid.setOrientation(LinearLayout.VERTICAL);
        actionsGrid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Row 1: WhatsApp, PDF, Image, SMS
        LinearLayout row1=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button shareBtn=button("📲 واتساب");
        shareBtn.setTextColor(Color.WHITE); shareBtn.setBackground(rounded(GREEN,dp(8)));
        shareBtn.setTextSize(11f);
        shareBtn.setOnClickListener(v->{
            dlg.dismiss();
            sharePurchaseInvoice(id,no,supplier,total,date);
        });

        Button pdfBtn=button("📄 PDF");
        pdfBtn.setTextColor(Color.rgb(180,40,40)); pdfBtn.setBackground(outline(CARD,8));
        pdfBtn.setTextSize(11f);
        pdfBtn.setOnClickListener(v->{
            dlg.dismiss();
            sharePurchaseInvoicePdf(id,no,supplier,total,date);
        });

        Button imgBtn=button("🖼️ صورة");
        imgBtn.setTextColor(Color.rgb(30,100,200)); imgBtn.setBackground(outline(CARD,8));
        imgBtn.setTextSize(11f);
        imgBtn.setOnClickListener(v->{
            dlg.dismiss();
            sharePurchaseInvoiceImage(id,no,supplier,total,date);
        });

        Button smsBtn=button("✉️ SMS");
        smsBtn.setTextColor(Color.rgb(180,120,20)); smsBtn.setBackground(outline(CARD,8));
        smsBtn.setTextSize(11f);
        smsBtn.setOnClickListener(v->{
            dlg.dismiss();
            sharePurchaseInvoiceSms(id,no,supplier,total,date);
        });

                row1.addView(shareBtn,new LinearLayout.LayoutParams(0,dp(52),1f));
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(0,dp(52),1f);plp.setMargins(dp(4),0,0,0);
        row1.addView(pdfBtn,plp);
        actionsGrid.addView(row1,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout row1b=new LinearLayout(this);row1b.setOrientation(LinearLayout.HORIZONTAL);row1b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row1b.addView(imgBtn,new LinearLayout.LayoutParams(0,dp(52),1f));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(52),1f);slp.setMargins(dp(4),0,0,0);row1b.addView(smsBtn,slp);
        actionsGrid.addView(row1b,new LinearLayout.LayoutParams(-1,dp(42)));

        addSpaceTo(actionsGrid,4);

        // Row 2: Print, Edit, Delete
        LinearLayout row2=new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button printBtn=button("🖨️ طباعة");
        printBtn.setTextColor(GREEN); printBtn.setBackground(outline(CARD,8));
        printBtn.setTextSize(11.5f);
        printBtn.setOnClickListener(v->{
            dlg.dismiss();
            printPurchaseInvoice(id,no,supplier,total,date);
        });

        Button editBtn=button("✏️ تعديل");
        editBtn.setTextColor(GOLD); editBtn.setBackground(outline(CARD,8));
        editBtn.setTextSize(11.5f);
        editBtn.setOnClickListener(v->{
            dlg.dismiss();
            purchaseInvoiceForm(true,id);
        });

        Button delBtn=button("🗑️ حذف");
        delBtn.setTextColor(RED); delBtn.setBackground(outline(CARD,8));
        delBtn.setTextSize(11.5f);
        delBtn.setOnClickListener(v->{
            dlg.dismiss();
            confirmDeletePurchaseInvoice(id,no);
        });

        row2.addView(printBtn,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(0,dp(50),1f); elp.setMargins(dp(3),0,0,0);
        row2.addView(editBtn,elp);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(50),1f); dlp.setMargins(dp(3),0,0,0);
        row2.addView(delBtn,dlp);
        actionsGrid.addView(row2,new LinearLayout.LayoutParams(-1,dp(52)));

        box.addView(actionsGrid,new LinearLayout.LayoutParams(-1,-2));

        dlg.setContentView(box);
        if(dlg.getWindow()!=null){
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlg.getWindow().setLayout(dp(340),dp(480));
            dlg.getWindow().setGravity(Gravity.CENTER);
        }
        dlg.show();
    }


    void confirmDeleteInvoice(long id,String no){
        new AlertDialog.Builder(this)
            .setTitle("حذف فاتورة المبيعات")
            .setMessage("هل تريد حذف فاتورة المبيعات رقم #"+no+"؟ سيتم حذف تفاصيلها والحركات المحاسبية المرتبطة بها.")
            .setPositiveButton("حذف",(d,w)->{
                db.deleteInvoice(id);
                Toast.makeText(this,"تم حذف الفاتورة بنجاح",Toast.LENGTH_SHORT).show();
                invoiceHistory();
            })
            .setNegativeButton("إلغاء",null)
            .show();
    }


    void confirmDeletePurchaseInvoice(long id,String no){
        new AlertDialog.Builder(this)
            .setTitle("حذف فاتورة الشراء")
            .setMessage("هل أنت تأكد من حذف فاتورة الشراء رقم #"+no+"؟ سيتم خصم الكميات المضافة من المخزون.")
            .setPositiveButton("حذف",(d,w)->{
                db.deletePurchase(id);
                Toast.makeText(this,"تم حذف فاتورة الشراء وتعديل المخزون",Toast.LENGTH_SHORT).show();
                purchaseInvoices();
            })
            .setNegativeButton("إلغاء",null)
            .show();
    }


    void sharePurchaseInvoiceSms(long id,String no,String supplier,double total,String date){
        try{
            ArrayList<PurchaseLine> lines=loadPurchaseLines(id);
            String text=purchaseReceiptText(no,supplier,lines,total,date);
            String phone=db.supplierPhoneByName(supplier);
            shareSmsToCustomer(phone,text);
        }catch(Exception e){Toast.makeText(this,"تعذر إرسال الرسالة",Toast.LENGTH_SHORT).show();}
    }


    void showPostSavePurchaseActions(long id,String no,String supplier,ArrayList<PurchaseLine> lines,double total,String date){
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(14),dp(16),dp(14));
        box.setBackground(rounded(CARD,dp(18)));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView icon=tv("📦",32);icon.setGravity(Gravity.CENTER);
        box.addView(icon,new LinearLayout.LayoutParams(-1,dp(42)));

        TextView title=tv("تم حفظ فاتورة الشراء بنجاح",16);
        title.setTextColor(GOLD);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setGravity(Gravity.CENTER);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(26)));

        TextView sub=tv("فاتورة شراء #"+no+" • "+(supplier==null||supplier.isEmpty()?"بدون مورد":supplier)+" • "+lines.size()+" أصناف",11.5f);
        sub.setTextColor(MUTED);sub.setGravity(Gravity.CENTER);
        box.addView(sub,new LinearLayout.LayoutParams(-1,dp(20)));
        addSpaceTo(box,8);

        LinearLayout totBox=new LinearLayout(this);
        totBox.setOrientation(LinearLayout.VERTICAL);
        totBox.setGravity(Gravity.CENTER);
        totBox.setPadding(dp(10),dp(6),dp(10),dp(6));
        totBox.setBackground(outline(Color.rgb(255,250,240),10));
        TextView amtTv=tv("إجمالي المشتريات: "+fmt(total)+" ريال",14);
        amtTv.setTextColor(GOLD);amtTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);amtTv.setGravity(Gravity.CENTER);
        totBox.addView(amtTv,new LinearLayout.LayoutParams(-1,-2));
        box.addView(totBox,new LinearLayout.LayoutParams(-1,dp(48)));
        addSpaceTo(box,10);

        LinearLayout actions1=new LinearLayout(this);
        actions1.setOrientation(LinearLayout.HORIZONTAL);
        actions1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button share=button("📤 واتساب");share.setTextColor(Color.WHITE);share.setBackground(rounded(GREEN,dp(10)));
        Button smsBtn=button("✉️ SMS");smsBtn.setTextColor(Color.rgb(180,120,20));smsBtn.setBackground(outline(CARD,10));
        Button printBtn=button("🖨️ طباعة");printBtn.setTextColor(Color.WHITE);printBtn.setBackground(rounded(DARK,dp(10)));

        actions1.addView(share,new LinearLayout.LayoutParams(0,dp(42),1.1f));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(42),0.9f); slp.setMargins(dp(5),0,0,0);
        actions1.addView(smsBtn,slp);
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(0,dp(42),1f); plp.setMargins(dp(5),0,0,0);
        actions1.addView(printBtn,plp);
        box.addView(actions1);
        addSpaceTo(box,6);

        LinearLayout actions2=new LinearLayout(this);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        actions2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button newP=button("＋ فاتورة جديدة");newP.setTextColor(GOLD);newP.setBackground(outline(Color.rgb(255,252,245),10));
        Button close=button("✓ إقفال والعودة للسجل");close.setTextColor(MUTED);close.setBackground(outline(CARD,10));

        actions2.addView(newP,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(50),1); clp.setMargins(dp(6),0,0,0);
        actions2.addView(close,clp);
        box.addView(actions2);

        share.setOnClickListener(v->{
            dialog.dismiss();
            purchaseInvoices();
            sharePurchaseInvoice(id,no,supplier,total,date);
        });
        smsBtn.setOnClickListener(v->{
            dialog.dismiss();
            purchaseInvoices();
            sharePurchaseInvoiceSms(id,no,supplier,total,date);
        });
        printBtn.setOnClickListener(v->{
            printPurchaseInvoice(id,no,supplier,total,date);
        });
        newP.setOnClickListener(v->{dialog.dismiss();newPurchaseInvoice();});
        close.setOnClickListener(v->{dialog.dismiss();purchaseInvoices();});

        dialog.setContentView(box);dialog.setCanceledOnTouchOutside(false);dialog.setCancelable(false);dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(dp(340),WindowManager.LayoutParams.WRAP_CONTENT);dialog.getWindow().setGravity(Gravity.CENTER);}
    }


    ArrayList<PurchaseLine> loadPurchaseLines(long id){
        ArrayList<PurchaseLine> ls=new ArrayList<>();Cursor c=db.purchaseLines(id);
        while(c.moveToNext())ls.add(new PurchaseLine(c.getString(1),c.getDouble(2),c.getDouble(3),c.getDouble(4),c.getDouble(5)));
        c.close();return ls;
    }

    String purchaseReceiptText(String no,String supplier,ArrayList<PurchaseLine> lines,double total,String date){
        StringBuilder s=new StringBuilder();
        s.append("🛒 *بقالة العزي للمواد الغذائية*\n");
        s.append("━━━━━━━━━━━━━━━━━━\n");
        s.append("📦 *فاتورة شراء رقم:* #").append(no).append("\n");
        s.append("👤 *المورد:* ").append(supplier==null||supplier.trim().isEmpty()?"بدون مورد":supplier.trim()).append("\n");
        s.append("📅 *التاريخ:* ").append(date==null||date.isEmpty()?db.now():date).append("\n");
        s.append("━━━━━━━━━━━━━━━━━━\n");
        s.append("📋 *الأصناف والكميات:*\n");
        for(PurchaseLine l:lines){
            String n=l.name==null?"":l.name.trim();
            s.append("▪️ ").append(n).append(" × ").append(fmt(l.qty)).append(" = ").append(fmt(l.total)).append(" ر.ي\n");
            s.append("   سعر الشراء للوحدة: ").append(fmt(l.cost)).append(" ر.ي\n");
            s.append("   سعر البيع: ").append(fmt(l.sale)).append(" ر.ي\n");
        }
        s.append("━━━━━━━━━━━━━━━━━━\n");
        s.append("💰 *إجمالي فاتورة الشراء:* ").append(fmt(total)).append(" ريال\n");
        s.append("━━━━━━━━━━━━━━━━━━\n");
        s.append("✨ *بقالة العزي للمواد الغذائية - إدارة المشتريات والمخزون* ✨");
        return s.toString();
    }

    Bitmap purchaseReceiptBitmap(String no,String supplier,ArrayList<PurchaseLine> lines,double total,String date){
        final int width=480;
        final int margin=18;
        final int lineH=28;
        int rowCount=lines==null?0:lines.size();
        int baseHeight=320+rowCount*lineH+160;
        Bitmap b=Bitmap.createBitmap(width,baseHeight,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokeP=new Paint(Paint.ANTI_ALIAS_FLAG);strokeP.setStyle(Paint.Style.STROKE);strokeP.setStrokeWidth(2);strokeP.setColor(Color.rgb(240,225,190));
        Paint fillP=new Paint(Paint.ANTI_ALIAS_FLAG);

        canvas.drawRoundRect(8,8,width-8,b.getHeight()-8,14,14,strokeP);

        fillP.setColor(Color.rgb(255,250,240));
        canvas.drawRoundRect(12,12,width-12,82,10,10,fillP);

        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(22);p.setColor(GOLD);p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("بقالة العزي للمواد الغذائية",width-margin-10,40,p);
        p.setTextSize(11.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        canvas.drawText("مستقبل تجارتك يبدأ من هنا",width-margin-10,58,p);

        p.setTextSize(12f);p.setColor(DARK);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        canvas.drawText("فاتورة شراء #"+no+"  •  "+(date==null||date.isEmpty()?db.now():date),width-margin-10,68,p);

        int y=106;
        String suppName=supplier==null||supplier.trim().isEmpty()?"مورد عام":supplier.trim();
        fillP.setColor(Color.rgb(255,252,245));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,fillP);
        strokeP.setColor(Color.rgb(245,230,200));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,strokeP);

        p.setTextSize(13);p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setColor(DARK);p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("المورد: "+suppName,width-margin-12,y+24,p);
        y+=48;

        fillP.setColor(GOLD);
        canvas.drawRoundRect(margin,y,width-margin,y+28,5,5,fillP);
        p.setColor(Color.WHITE);p.setTextSize(12.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("الصنف",width-margin-12,y+19,p);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("الكمية",width-margin-210,y+19,p);
        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("الإجمالي",margin+12,y+19,p);
        y+=32;

        p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(12);
        if(lines!=null){
            for(int i=0;i<lines.size();i++){
                PurchaseLine l=lines.get(i);
                fillP.setColor(i%2==0?Color.rgb(255,254,250):Color.WHITE);
                canvas.drawRect(margin,y,width-margin,y+lineH,fillP);
                strokeP.setColor(Color.rgb(245,240,230));
                canvas.drawLine(margin,y+lineH,width-margin,y+lineH,strokeP);

                p.setColor(TEXT);p.setTextAlign(Paint.Align.RIGHT);
                String iname=l.name==null?"":l.name.trim();
                if(iname.length()>22) iname=iname.substring(0,22)+"…";
                canvas.drawText(iname,width-margin-12,y+18,p);

                p.setTextAlign(Paint.Align.CENTER);p.setColor(MUTED);
                canvas.drawText("× "+fmt(l.qty),width-margin-210,y+18,p);

                p.setTextAlign(Paint.Align.LEFT);p.setColor(GOLD);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
                canvas.drawText(fmt(l.total)+" ر.ي",margin+12,y+18,p);
                p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
                y+=lineH;
            }
        }

        y+=10;
        fillP.setColor(Color.rgb(255,250,240));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,fillP);
        strokeP.setColor(Color.rgb(240,220,180));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,strokeP);

        p.setColor(GOLD);p.setTextSize(15);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("إجمالي فاتورة الشراء:",width-margin-12,y+24,p);
        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fmt(total)+" ريال",margin+12,y+24,p);
        y+=48;

        p.setTextAlign(Paint.Align.CENTER);p.setColor(MUTED);p.setTextSize(11);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        canvas.drawText("✨ بقالة العزي للمواد الغذائية • سجل المشتريات والمخزون ✨",width/2,y+12,p);
        y+=22;

        return Bitmap.createBitmap(b,0,0,width,Math.min(y+16,b.getHeight()));
    }


    File createPurchaseInvoicePdf(String no,String supplier,ArrayList<PurchaseLine> lines,double total,String date){
        File dir=new File(getCacheDir(),"pdf");if(!dir.exists())dir.mkdirs();
        File file=new File(dir,"فاتورة_شراء_"+no+"_"+System.currentTimeMillis()+".pdf");
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        final int pageW=420,margin=24;
        int rowCount=lines==null?0:lines.size();
        int pageH=Math.max(480,220+rowCount*24+180);
        
        android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,1).create());
        Canvas canvas=page.getCanvas();

        Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokePaint=new Paint(Paint.ANTI_ALIAS_FLAG);strokePaint.setStyle(Paint.Style.STROKE);strokePaint.setStrokeWidth(1);strokePaint.setColor(Color.rgb(240,225,190));
        TextPaint titleP=new TextPaint(Paint.ANTI_ALIAS_FLAG);titleP.setColor(GOLD);titleP.setTextSize(18);titleP.setTypeface(Typeface.create("sans",Typeface.BOLD));
        TextPaint subP=new TextPaint(Paint.ANTI_ALIAS_FLAG);subP.setColor(DARK);subP.setTextSize(10.5f);subP.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        TextPaint headerP=new TextPaint(Paint.ANTI_ALIAS_FLAG);headerP.setColor(Color.WHITE);headerP.setTextSize(11);headerP.setTypeface(Typeface.create("sans",Typeface.BOLD));
        TextPaint cellP=new TextPaint(Paint.ANTI_ALIAS_FLAG);cellP.setColor(TEXT);cellP.setTextSize(10);cellP.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        TextPaint boldCellP=new TextPaint(Paint.ANTI_ALIAS_FLAG);boldCellP.setColor(TEXT);boldCellP.setTextSize(10);boldCellP.setTypeface(Typeface.create("sans",Typeface.BOLD));

        int y=margin;

        fillPaint.setColor(Color.rgb(255,250,240));
        canvas.drawRoundRect(margin,y,pageW-margin,y+52,8,8,fillPaint);
        canvas.drawRoundRect(margin,y,pageW-margin,y+52,8,8,strokePaint);

        titleP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("بقالة العزي للمواد الغذائية",pageW-margin-12,y+24,titleP);
        subP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("فاتورة شراء #"+no+"  •  "+(date==null||date.isEmpty()?db.now():date),pageW-margin-12,y+42,subP);
        y+=60;

        String suppName=supplier==null||supplier.trim().isEmpty()?"مورد عام":supplier.trim();
        String phone=db.supplierPhoneByName(suppName);
        fillPaint.setColor(Color.rgb(255,252,245));
        canvas.drawRoundRect(margin,y,pageW-margin,y+34,6,6,fillPaint);
        canvas.drawRoundRect(margin,y,pageW-margin,y+34,6,6,strokePaint);

        boldCellP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("المورد: "+suppName+(phone.isEmpty()?"":" ("+phone+")"),pageW-margin-10,y+22,boldCellP);
        y+=40;

        int[] colW={180,80,112};
        fillPaint.setColor(GOLD);
        canvas.drawRoundRect(margin,y,pageW-margin,y+24,4,4,fillPaint);
        headerP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("الصنف",pageW-margin-10,y+16,headerP);
        headerP.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("الكمية",pageW-margin-colW[0]-colW[1]/2,y+16,headerP);
        headerP.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("الإجمالي",margin+10,y+16,headerP);
        y+=26;

        int rowH=22;
        boldCellP.setTextSize(10);
        if(lines!=null){
            for(int i=0;i<lines.size();i++){
                PurchaseLine l=lines.get(i);
                fillPaint.setColor(i%2==0?Color.rgb(255,254,250):Color.WHITE);
                canvas.drawRect(margin,y,pageW-margin,y+rowH,fillPaint);
                strokePaint.setColor(Color.rgb(245,240,230));
                canvas.drawLine(margin,y+rowH,pageW-margin,y+rowH,strokePaint);

                cellP.setTextAlign(Paint.Align.RIGHT);
                String iname=l.name==null?"":l.name.trim();
                if(iname.length()>22) iname=iname.substring(0,22)+"…";
                canvas.drawText(iname,pageW-margin-10,y+15,cellP);

                cellP.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(fmt(l.qty),pageW-margin-colW[0]-colW[1]/2,y+15,cellP);

                boldCellP.setTextAlign(Paint.Align.LEFT);
                boldCellP.setColor(GOLD);
                canvas.drawText(fmt(l.total)+" ر.ي",margin+10,y+15,boldCellP);
                boldCellP.setColor(TEXT);
                y+=rowH;
            }
        }

        y+=8;
        fillPaint.setColor(Color.rgb(255,250,240));
        strokePaint.setColor(Color.rgb(240,220,180));
        canvas.drawRoundRect(margin,y,pageW-margin,y+36,6,6,fillPaint);
        canvas.drawRoundRect(margin,y,pageW-margin,y+36,6,6,strokePaint);

        boldCellP.setTextSize(12);
        boldCellP.setColor(GOLD);
        boldCellP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("إجمالي فاتورة الشراء:",pageW-margin-12,y+23,boldCellP);
        boldCellP.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fmt(total)+" ريال",margin+12,y+23,boldCellP);
        y+=42;

        subP.setTextAlign(Paint.Align.CENTER);
        subP.setColor(MUTED);
        canvas.drawText("✨ بقالة العزي للمواد الغذائية • إدارة المشتريات والمخزون ✨",pageW/2,y+16,subP);

        pdf.finishPage(page);
        try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}
        catch(Exception e){throw new RuntimeException(e);}
        finally{pdf.close();}
        return file;
    }


    void sharePurchaseInvoice(long id,String no,String supplier,double total,String date){
        try{
            ArrayList<PurchaseLine> lines=loadPurchaseLines(id);
            String text=purchaseReceiptText(no,supplier,lines,total,date);
            Bitmap b=purchaseReceiptBitmap(no,supplier,lines,total,date);
            Uri uri=saveReceiptBitmap(b,"purchase_"+no);
            String phone=db.supplierPhoneByName(supplier);
            shareWhatsAppToCustomer(phone,text,uri);
        }catch(Exception e){shareText(purchaseReceiptText(no,supplier,loadPurchaseLines(id),total,date));}
    }


    void sharePurchaseInvoicePdf(long id,String no,String supplier,double total,String date){
        try{
            ArrayList<PurchaseLine> lines=loadPurchaseLines(id);
            File file=createPurchaseInvoicePdf(no,supplier,lines,total,date);
            String text=purchaseReceiptText(no,supplier,lines,total,date);
            DocumentCenter.sharePdf(this,file,text,"مشاركة فاتورة شراء PDF");
        }catch(Exception e){
            Toast.makeText(this,"تعذر مشاركة فاتورة شراء PDF",Toast.LENGTH_SHORT).show();
        }
    }


    void sharePurchaseInvoiceImage(long id,String no,String supplier,double total,String date){
        try{
            ArrayList<PurchaseLine> lines=loadPurchaseLines(id);
            Bitmap b=purchaseReceiptBitmap(no,supplier,lines,total,date);
            Uri uri=saveReceiptBitmap(b,"purchase_"+no);
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM,uri);
            String text=purchaseReceiptText(no,supplier,lines,total,date);
            i.putExtra(Intent.EXTRA_TEXT,text);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"مشاركة صورة فاتورة الشراء"));
        }catch(Exception e){Toast.makeText(this,"تعذر مشاركة صورة فاتورة الشراء",Toast.LENGTH_SHORT).show();}
    }

    void printPurchaseInvoice(long id,String no,String supplier,double total,String date){
        try{
            ArrayList<PurchaseLine> lines=loadPurchaseLines(id);
            StringBuilder s=new StringBuilder();
            s.append("بقالة العزي للمواد الغذائية\nفاتورة شراء: ").append(no).append("\nالمورد: ").append(supplier==null||supplier.isEmpty()?"بدون مورد":supplier).append("\nالتاريخ: ").append(date).append("\n");
            s.append("------------------------------\nالصنف | الكمية | الإجمالي\n");
            for(PurchaseLine l:lines){
                s.append(l.name==null?"":l.name.trim()).append(" | ").append(fmt(l.qty)).append(" | ").append(fmt(l.total)).append("\n");
                s.append("سعر الشراء للوحدة: ").append(fmt(l.cost)).append(" ريال  •  سعر البيع: ").append(fmt(l.sale)).append(" ريال\n");
            }
            s.append("------------------------------\nالإجمالي: ").append(fmt(total)).append(" ريال\nشكراً لتعاملكم معنا");
            printTextBluetooth(s.toString());
        }catch(Exception e){Toast.makeText(this,"تعذر تجهيز فاتورة الشراء للطباعة",Toast.LENGTH_LONG).show();}
    }


    String receiptText(String no,String customer,LinearLayout rows,double total,long cid){
        StringBuilder s=new StringBuilder("بقالة العزي للمواد الغذائية\nفاتورة رقم: ").append(no).append("\nالتاريخ: ").append(db.now()).append("\n");
        if(customer!=null&&!customer.trim().isEmpty())s.append("العميل: ").append(customer.trim()).append("\n");
        s.append("------------------------------\n");
        for(int i=0;i<rows.getChildCount();i++){
            View ch=rows.getChildAt(i);
            if(ch instanceof LinearLayout){
                LinearLayout r=(LinearLayout)ch;
                StringBuilder q=new StringBuilder();
                for(int j=0;j<r.getChildCount();j++){
                    View x=r.getChildAt(j);
                    if(x instanceof TextView){
                        String z=((TextView)x).getText().toString().trim();
                        if(!z.isEmpty()){if(q.length()>0)q.append(" | ");q.append(z);}
                    }
                }
                if(q.length()>0)s.append(q).append("\n");
            }
        }
        s.append("------------------------------\nالإجمالي: ").append(fmt(total)).append(" ريال\n");
        if(cid>0)s.append(balanceText(db.balance(cid))).append("\n");
        s.append("شكراً لتعاملكم معنا");
        return s.toString();
    }

    int balanceColor(double balance){if(balance>0.005)return RED;if(balance<-0.005)return BLUE;return GREEN;}

    String balanceText(double b){double x=Math.abs(b)<0.005?0:b;if(x>0)return "رصيدكم عليكم: "+fmt(x)+" ريال";if(x<0)return "رصيدكم لكم: "+fmt(Math.abs(x))+" ريال";return "رصيدكم مسدد بالكامل (0 ريال)";}


    File createCustomerStatementPdf(long id,String name){
        File dir=new File(getCacheDir(),"pdf");if(!dir.exists())dir.mkdirs();
        File file=new File(dir,"كشف_حساب_"+name.replaceAll("[\\/:*?\"<>|]","_")+"_"+System.currentTimeMillis()+".pdf");
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        final int pageW=595,pageH=842,margin=36,contentW=pageW-(margin*2);

        TextPaint titlePaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);titlePaint.setColor(GREEN);titlePaint.setTextSize(18);titlePaint.setTypeface(Typeface.create("sans",Typeface.BOLD));
        TextPaint subPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);subPaint.setColor(DARK);subPaint.setTextSize(11);subPaint.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        TextPaint headerPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);headerPaint.setColor(Color.WHITE);headerPaint.setTextSize(10.5f);headerPaint.setTypeface(Typeface.create("sans",Typeface.BOLD));
        TextPaint cellPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);cellPaint.setColor(TEXT);cellPaint.setTextSize(9.5f);cellPaint.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        TextPaint boldCellPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);boldCellPaint.setColor(TEXT);boldCellPaint.setTextSize(9.5f);boldCellPaint.setTypeface(Typeface.create("sans",Typeface.BOLD));
        Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokePaint=new Paint(Paint.ANTI_ALIAS_FLAG);strokePaint.setStyle(Paint.Style.STROKE);strokePaint.setStrokeWidth(1);strokePaint.setColor(Color.rgb(220,225,220));

        double currentBalance=db.balance(id);
        double totalDebit=db.customerDebitTotal(id);
        double totalCredit=db.customerCreditTotal(id);
        String phone=db.phoneByName(name);
        String nowDate=db.now();

        // Collect transactions
        ArrayList<String[]> txList=new ArrayList<>();
        Cursor c=db.transactions(id);
        double running=currentBalance;
        while(c.moveToNext()){
            String d=c.getString(1), det=c.getString(2);
            double amt=c.getDouble(3); int type=c.getInt(4);
            String inv=db.invoiceNoFromTransaction(det);
            String detClean=det==null?"":det.trim();
            if(!inv.isEmpty()&&!detClean.contains("فاتورة")) detClean="فاتورة #"+inv+" - "+detClean;
            String debitStr=type==1?fmt(amt):"-";
            String creditStr=type!=1?fmt(amt):"-";
            String balStr=balanceText(running);
            txList.add(new String[]{d,detClean,inv.isEmpty()?"-":"#"+inv,debitStr,creditStr,balStr});
            running-=(type==1?amt:-amt);
        }
        c.close();

        int pageNo=1;
        android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());
        Canvas canvas=page.getCanvas();
        int y=margin;

        // Top Banner
        fillPaint.setColor(Color.rgb(238,247,240));
        canvas.drawRoundRect(margin,y,pageW-margin,y+50,8,8,fillPaint);
        strokePaint.setColor(Color.rgb(195,230,205));
        canvas.drawRoundRect(margin,y,pageW-margin,y+50,8,8,strokePaint);

        titlePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("بقالة العزي للمواد الغذائية  •  كشف حساب تفصيلي",pageW-margin-14,y+24,titlePaint);
        subPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("تاريخ الاستخراج: "+nowDate+"  |  العميل: "+name+(phone.isEmpty()?"":"  |  الهاتف: "+phone),pageW-margin-14,y+42,subPaint);
        y+=60;

        // 3 KPI Cards
        int kpiW=(contentW-16)/3;
        // 1. Debits (عليه)
        fillPaint.setColor(Color.rgb(255,243,243));
        canvas.drawRoundRect(margin,y,margin+kpiW,y+42,6,6,fillPaint);
        cellPaint.setTextAlign(Paint.Align.CENTER);cellPaint.setColor(RED);
        canvas.drawText("إجمالي ما عليه (مسحوبات)",margin+kpiW/2,y+16,cellPaint);
        boldCellPaint.setTextAlign(Paint.Align.CENTER);boldCellPaint.setColor(RED);boldCellPaint.setTextSize(11);
        canvas.drawText(fmt(totalDebit)+" ريال",margin+kpiW/2,y+33,boldCellPaint);

        // 2. Credits (له)
        fillPaint.setColor(Color.rgb(240,248,255));
        canvas.drawRoundRect(margin+kpiW+8,y,margin+kpiW*2+8,y+42,6,6,fillPaint);
        cellPaint.setColor(BLUE);
        canvas.drawText("إجمالي ما له (مدفوعات)",margin+kpiW+8+kpiW/2,y+16,cellPaint);
        boldCellPaint.setColor(BLUE);
        canvas.drawText(fmt(totalCredit)+" ريال",margin+kpiW+8+kpiW/2,y+33,boldCellPaint);

        // 3. Final Balance
        fillPaint.setColor(currentBalance>0.005?Color.rgb(255,240,240):Color.rgb(240,250,242));
        canvas.drawRoundRect(margin+kpiW*2+16,y,pageW-margin,y+42,6,6,fillPaint);
        cellPaint.setColor(balanceColor(currentBalance));
        canvas.drawText(currentBalance>0.005?"الرصيد المتبقي عليه":(currentBalance<-0.005?"الرصيد الفائض له":"الحساب خالص"),margin+kpiW*2+16+kpiW/2,y+16,cellPaint);
        boldCellPaint.setColor(balanceColor(currentBalance));
        canvas.drawText(fmt(Math.abs(currentBalance))+" ريال",margin+kpiW*2+16+kpiW/2,y+33,boldCellPaint);
        y+=52;

        // Table Header
        int[] colW={105,170,60,60,60,68}; // Date, Details, Ref, Debit, Credit, Balance
        fillPaint.setColor(GREEN);
        canvas.drawRoundRect(margin,y,pageW-margin,y+24,4,4,fillPaint);
        headerPaint.setTextAlign(Paint.Align.CENTER);
        
        int hx=pageW-margin;
        canvas.drawText("التاريخ",hx-colW[0]/2,y+16,headerPaint); hx-=colW[0];
        canvas.drawText("البيان والتفاصيل",hx-colW[1]/2,y+16,headerPaint); hx-=colW[1];
        canvas.drawText("المرجع",hx-colW[2]/2,y+16,headerPaint); hx-=colW[2];
        canvas.drawText("عليه (مدين)",hx-colW[3]/2,y+16,headerPaint); hx-=colW[3];
        canvas.drawText("له (دائن)",hx-colW[4]/2,y+16,headerPaint); hx-=colW[4];
        canvas.drawText("الرصيد بعده",hx-colW[5]/2,y+16,headerPaint);
        y+=26;

        // Rows
        boldCellPaint.setTextSize(9f);
        cellPaint.setTextSize(9f);
        int rowH=22;
        for(int idx=0;idx<txList.size();idx++){
            String[] r=txList.get(idx);
            if(y+rowH>pageH-margin-30){
                // Footer of page
                cellPaint.setColor(MUTED);cellPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("صفحة "+pageNo+"  •  بقالة العزي للمواد الغذائية",pageW/2,pageH-margin+10,cellPaint);
                pdf.finishPage(page);
                pageNo++;
                page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());
                canvas=page.getCanvas();
                y=margin;
                // Redraw table header on new page
                fillPaint.setColor(GREEN);
                canvas.drawRoundRect(margin,y,pageW-margin,y+24,4,4,fillPaint);
                hx=pageW-margin;
                canvas.drawText("التاريخ",hx-colW[0]/2,y+16,headerPaint); hx-=colW[0];
                canvas.drawText("البيان والتفاصيل",hx-colW[1]/2,y+16,headerPaint); hx-=colW[1];
                canvas.drawText("المرجع",hx-colW[2]/2,y+16,headerPaint); hx-=colW[2];
                canvas.drawText("عليه (مدين)",hx-colW[3]/2,y+16,headerPaint); hx-=colW[3];
                canvas.drawText("له (دائن)",hx-colW[4]/2,y+16,headerPaint); hx-=colW[4];
                canvas.drawText("الرصيد بعده",hx-colW[5]/2,y+16,headerPaint);
                y+=26;
            }

            fillPaint.setColor(idx%2==0?Color.rgb(252,253,252):Color.WHITE);
            canvas.drawRect(margin,y,pageW-margin,y+rowH,fillPaint);
            strokePaint.setColor(Color.rgb(235,240,235));
            canvas.drawLine(margin,y+rowH,pageW-margin,y+rowH,strokePaint);

            int rx=pageW-margin;
            cellPaint.setColor(TEXT);cellPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(r[0],rx-colW[0]/2,y+15,cellPaint); rx-=colW[0];

            cellPaint.setTextAlign(Paint.Align.RIGHT);
            String rowDet=r[1].length()>30?r[1].substring(0,30)+"…":r[1];
            canvas.drawText(rowDet,rx-8,y+15,cellPaint); rx-=colW[1];

            cellPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(r[2],rx-colW[2]/2,y+15,cellPaint); rx-=colW[2];

            boldCellPaint.setColor(!r[3].equals("-")?RED:MUTED);
            boldCellPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(r[3],rx-colW[3]/2,y+15,boldCellPaint); rx-=colW[3];

            boldCellPaint.setColor(!r[4].equals("-")?BLUE:MUTED);
            canvas.drawText(r[4],rx-colW[4]/2,y+15,boldCellPaint); rx-=colW[4];

            cellPaint.setColor(TEXT);
            canvas.drawText(r[5].replace("رصيدكم ",""),rx-colW[5]/2,y+15,cellPaint);

            y+=rowH;
        }

        // Summary footer on last page
        y+=12;
        if(y+40<pageH-margin){
            fillPaint.setColor(Color.rgb(243,248,244));
            canvas.drawRoundRect(margin,y,pageW-margin,y+32,6,6,fillPaint);
            boldCellPaint.setColor(GREEN);boldCellPaint.setTextSize(11);boldCellPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("شكراً لتعاملكم مع بقالة العزي للمواد الغذائية  •  الرصيد النهائي: "+balanceText(currentBalance),pageW/2,y+20,boldCellPaint);
        }

        cellPaint.setColor(MUTED);cellPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("صفحة "+pageNo+"  •  تم استخراج هذا الكشف آلياً من تطبيق بقالة العزي للمواد الغذائية",pageW/2,pageH-margin+10,cellPaint);
        pdf.finishPage(page);

        try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}catch(Exception e){throw new RuntimeException(e);}
        finally{pdf.close();}
        return file;
    }


    File createA4Pdf(String text,String prefix){
        File dir=new File(getCacheDir(),"pdf");if(!dir.exists())dir.mkdirs();File file=new File(dir,prefix+"_"+System.currentTimeMillis()+".pdf");
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        final int pageW=595,pageH=842,margin=42,contentW=pageW-(margin*2);
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(12);paint.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        int pageNo=1;android.graphics.pdf.PdfDocument.Page page=null;Canvas canvas=null;int y=margin;
        try{
            for(String line:text.split("\n",-1)){
                String safe=line==null?"":line;
                if(page==null){page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());canvas=page.getCanvas();y=margin;}
                StaticLayout layout;
                if(Build.VERSION.SDK_INT>=23) layout=StaticLayout.Builder.obtain(safe,0,safe.length(),paint,contentW).setAlignment(Layout.Alignment.ALIGN_OPPOSITE).setIncludePad(false).setLineSpacing(1.0f,0.0f).setTextDirection(android.text.TextDirectionHeuristics.RTL).build();
                else layout=new StaticLayout(safe,paint,contentW,Layout.Alignment.ALIGN_OPPOSITE,1.0f,2,true);
                if(y+layout.getHeight()>pageH-margin){pdf.finishPage(page);pageNo++;page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());canvas=page.getCanvas();y=margin;}
                canvas.save();canvas.translate(margin,y);layout.draw(canvas);canvas.restore();y+=layout.getHeight()+6;
            }
            if(page!=null)pdf.finishPage(page);try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}catch(java.io.IOException e){throw new RuntimeException(e);}return file;
        }finally{pdf.close();}
    }


    void shareAccountPdfToWhatsApp(long id,String name){
        try{
            File file=createCustomerStatementPdf(id,name);
            String caption="كشف حساب تفصيلي - "+name+"\nبقالة العزي للمواد الغذائية\nرصيدكم الحالي: "+balanceText(db.balance(id));
            DocumentCenter.sharePdf(this,file,caption,"مشاركة كشف الحساب PDF");
        }catch(Exception e){
            Toast.makeText(this,"تعذر مشاركة كشف الحساب PDF",Toast.LENGTH_SHORT).show();
        }
    }


    File createInvoicePdf(String no,String customer,ArrayList<Line> lines,double total,double paid,double balanceAfter,String date){
        File dir=new File(getCacheDir(),"pdf");if(!dir.exists())dir.mkdirs();
        File file=new File(dir,"فاتورة_"+no+"_"+System.currentTimeMillis()+".pdf");
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        final int pageW=420,margin=24;
        int rowCount=lines==null?0:lines.size();
        int pageH=Math.max(480,240+rowCount*24+180);
        android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,1).create());
        Canvas canvas=page.getCanvas();

        Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokePaint=new Paint(Paint.ANTI_ALIAS_FLAG);strokePaint.setStyle(Paint.Style.STROKE);strokePaint.setStrokeWidth(1);strokePaint.setColor(Color.rgb(210,225,215));
        TextPaint titleP=new TextPaint(Paint.ANTI_ALIAS_FLAG);titleP.setColor(GREEN);titleP.setTextSize(18);titleP.setTypeface(Typeface.create("sans",Typeface.BOLD));
        TextPaint subP=new TextPaint(Paint.ANTI_ALIAS_FLAG);subP.setColor(DARK);subP.setTextSize(10.5f);subP.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        TextPaint headerP=new TextPaint(Paint.ANTI_ALIAS_FLAG);headerP.setColor(Color.WHITE);headerP.setTextSize(11);headerP.setTypeface(Typeface.create("sans",Typeface.BOLD));
        TextPaint cellP=new TextPaint(Paint.ANTI_ALIAS_FLAG);cellP.setColor(TEXT);cellP.setTextSize(10);cellP.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        TextPaint boldCellP=new TextPaint(Paint.ANTI_ALIAS_FLAG);boldCellP.setColor(TEXT);boldCellP.setTextSize(10);boldCellP.setTypeface(Typeface.create("sans",Typeface.BOLD));

        int y=margin;

        // Header Card
        fillPaint.setColor(Color.rgb(238,247,240));
        canvas.drawRoundRect(margin,y,pageW-margin,y+52,8,8,fillPaint);
        canvas.drawRoundRect(margin,y,pageW-margin,y+52,8,8,strokePaint);

        titleP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("بقالة العزي للمواد الغذائية",pageW-margin-12,y+24,titleP);
        subP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("فاتورة مبيعات #"+no+"  •  "+(date==null||date.isEmpty()?db.now():date),pageW-margin-12,y+42,subP);
        y+=60;

        // Customer Info
        String custName=customer==null||customer.trim().isEmpty()?"عميل نقدي":customer.trim();
        String phone=db.phoneByName(custName);
        fillPaint.setColor(Color.rgb(250,252,250));
        canvas.drawRoundRect(margin,y,pageW-margin,y+34,6,6,fillPaint);        canvas.drawRoundRect(margin,y,pageW-margin,y+34,6,6,strokePaint);

        boldCellP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("العميل: "+custName+(phone.isEmpty()?"":" ("+phone+")"),pageW-margin-10,y+22,boldCellP);
        y+=40;

        // Current Operation / Amount Bar
        double remaining=total-paid;
        boolean isCash=paid>=total && total>0;
        fillPaint.setColor(isCash?Color.rgb(240,250,242):(remaining>0?Color.rgb(255,243,243):Color.rgb(240,248,255)));
        strokePaint.setColor(isCash?Color.rgb(190,235,205):(remaining>0?Color.rgb(250,195,195):Color.rgb(195,225,250)));
        canvas.drawRoundRect(margin,y,pageW-margin,y+36,6,6,fillPaint);
        canvas.drawRoundRect(margin,y,pageW-margin,y+36,6,6,strokePaint);

        boldCellP.setTextAlign(Paint.Align.CENTER);
        boldCellP.setTextSize(12.5f);
        boldCellP.setColor(isCash?GREEN:(remaining>0?RED:BLUE));
        String opText=isCash?"مسدد نقداً: "+fmt(total)+" يمني":(paid>0?"عليك: "+fmt(remaining)+" يمني (مدفوع: "+fmt(paid)+")":"عليك: "+fmt(total)+" يمني");
        canvas.drawText(opText,pageW/2,y+23,boldCellP);
        y+=42;

        // Table Header
        int[] colW={180,80,112};
        fillPaint.setColor(GREEN);
        canvas.drawRoundRect(margin,y,pageW-margin,y+24,4,4,fillPaint);
        headerP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("الصنف (حق)",pageW-margin-10,y+16,headerP);
        headerP.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("الكمية",pageW-margin-colW[0]-colW[1]/2,y+16,headerP);
        headerP.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("الإجمالي",margin+10,y+16,headerP);
        y+=26;

        // Table Rows
        int rowH=22;
        if(lines!=null){
            for(int i=0;i<lines.size();i++){
                Line l=lines.get(i);
                fillPaint.setColor(i%2==0?Color.rgb(252,254,252):Color.WHITE);
                canvas.drawRect(margin,y,pageW-margin,y+rowH,fillPaint);
                strokePaint.setColor(Color.rgb(240,244,240));
                canvas.drawLine(margin,y+rowH,pageW-margin,y+rowH,strokePaint);

                cellP.setTextAlign(Paint.Align.RIGHT);
                String iname=l.name==null?"":l.name.trim();
                if(iname.length()>22) iname=iname.substring(0,22)+"…";
                canvas.drawText(iname,pageW-margin-10,y+15,cellP);

                cellP.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(fmt(l.qty),pageW-margin-colW[0]-colW[1]/2,y+15,cellP);

                boldCellP.setTextAlign(Paint.Align.LEFT);
                boldCellP.setColor(GREEN);
                canvas.drawText(fmt(l.total)+" ر.ي",margin+10,y+15,boldCellP);
                boldCellP.setColor(TEXT);
                y+=rowH;
            }
        }

        y+=8;
        // Total Box
        fillPaint.setColor(Color.rgb(240,249,242));
        strokePaint.setColor(Color.rgb(190,230,205));
        canvas.drawRoundRect(margin,y,pageW-margin,y+36,6,6,fillPaint);
        canvas.drawRoundRect(margin,y,pageW-margin,y+36,6,6,strokePaint);

        boldCellP.setTextSize(12);
        boldCellP.setColor(GREEN);
        boldCellP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("إجمالي الفاتورة:",pageW-margin-12,y+23,boldCellP);
        boldCellP.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fmt(total)+" ريال",margin+12,y+23,boldCellP);
        y+=42;

        // Final Balance Box
        if(customer!=null&&!customer.trim().isEmpty()&&Math.abs(balanceAfter)>=0.005){
            fillPaint.setColor(balanceAfter>0.005?Color.rgb(255,243,243):Color.rgb(240,248,255));
            strokePaint.setColor(balanceAfter>0.005?Color.rgb(245,200,200):Color.rgb(200,225,250));
            canvas.drawRoundRect(margin,y,pageW-margin,y+32,6,6,fillPaint);
            canvas.drawRoundRect(margin,y,pageW-margin,y+32,6,6,strokePaint);
            boldCellP.setTextSize(11.5f);
            boldCellP.setColor(balanceColor(balanceAfter));
            boldCellP.setTextAlign(Paint.Align.CENTER);
            String bTxt=balanceAfter>0?"الإجمالي - عليك "+fmt(balanceAfter)+" يمني":"الإجمالي - له "+fmt(Math.abs(balanceAfter))+" يمني";
            canvas.drawText(bTxt,pageW/2,y+20,boldCellP);
            y+=38;
        }

        subP.setTextAlign(Paint.Align.CENTER);
        subP.setColor(MUTED);
        canvas.drawText("✨ شكراً لتعاملكم معنا ونسعد بخدمتكم دائماً • بقالة العزي للمواد الغذائية ✨",pageW/2,y+16,subP);

        pdf.finishPage(page);
        try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}
        catch(Exception e){throw new RuntimeException(e);}
        finally{pdf.close();}
        return file;
    }


    void shareInvoicePdf(String no,String customer,ArrayList<Line> lines,double total,double paid,double balanceAfter,String date){
        try{
            File file=createInvoicePdf(no,customer,lines,total,paid,balanceAfter,date);
            String text=invoiceWhatsAppText(no,customer,lines,total,paid,balanceAfter,date);
            DocumentCenter.sharePdf(this,file,text,"مشاركة فاتورة PDF");
        }catch(Exception e){
            Toast.makeText(this,"تعذر مشاركة فاتورة PDF",Toast.LENGTH_SHORT).show();
        }
    }

    void shareInvoiceImage(String no,String customer,ArrayList<Line> lines,double total,double paid,double balanceAfter,String date){
        try{
            Bitmap b=invoiceReceiptBitmap(no,customer,lines,total,paid,balanceAfter,date);
            Uri uri=saveReceiptBitmap(b,no);
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM,uri);
            String text=invoiceWhatsAppText(no,customer,lines,total,paid,balanceAfter,date);
            i.putExtra(Intent.EXTRA_TEXT,text);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"مشاركة صورة الإيصال"));
        }catch(Exception e){Toast.makeText(this,"تعذر مشاركة صورة الإيصال",Toast.LENGTH_SHORT).show();}
    }


    void shareSmsToCustomer(String phone,String text){
        try{
            String p=phone==null?"":phone.replaceAll("[^0-9+]","");
            Intent i=new Intent(Intent.ACTION_SENDTO);
            if(!p.isEmpty()){
                i.setData(Uri.parse("smsto:"+Uri.encode(p)));
            }else{
                i.setData(Uri.parse("smsto:"));
            }
            i.putExtra("sms_body",text);
            i.putExtra(Intent.EXTRA_TEXT,text);
            startActivity(i);
        }catch(Exception e){
            try{
                Intent i=new Intent(Intent.ACTION_VIEW);
                i.setType("vnd.android-dir/mms-sms");
                i.putExtra("sms_body",text);
                startActivity(i);
            }catch(Exception ex){
                shareText(text);
            }
        }
    }


    void shareInvoiceSms(String no,String customer,ArrayList<Line> lines,double total,double paid){
        long cid=customer==null||customer.trim().isEmpty()?-1:db.customerIdByName(customer.trim());
        double bal=cid>0?db.balance(cid):0;
        String text=invoiceWhatsAppText(no,customer,lines,total,paid,bal,db.now());
        String phone=db.phoneByName(customer);
        shareSmsToCustomer(phone,text);
    }


    void shareOperationSms(String customer,String details,double amount,int type,String invNo){
        String text=compactOperationText(customer,details,amount,type,invNo);
        String phone=db.phoneByName(customer);
        shareSmsToCustomer(phone,text);
    }


    void shareCustomerBalanceSms(String customer){
        double bal=db.balanceByName(customer);
        String phone=db.phoneByName(customer);
        StringBuilder s=new StringBuilder();
        s.append("بقالة العزي للمواد الغذائية :\n");
        if(customer!=null&&!customer.trim().isEmpty()) s.append(customer.trim()).append("\n");
        if(Math.abs(bal)<0.005){
            s.append("الإجمالي - خالص (0 يمني)");
        }else if(bal>0){
            s.append("الإجمالي - عليك ").append(fmt(bal)).append(" يمني");
        }else{
            s.append("الإجمالي - له ").append(fmt(Math.abs(bal))).append(" يمني");
        }
        shareSmsToCustomer(phone,s.toString());
    }


    void shareText(String s){ try{ DocumentCenter.shareText(this,s); }catch(Exception e){ Toast.makeText(this,"تعذر فتح شاشة المشاركة",Toast.LENGTH_SHORT).show(); } }

    String normalizeWhatsAppPhone(String phone){
        String p=phone==null?"":phone.replaceAll("[^0-9+]","");
        if(p.startsWith("+"))p=p.substring(1);
        if(p.startsWith("00"))p=p.substring(2);
        if(p.startsWith("0")&&p.length()>=8)p="967"+p.substring(1);
        else if(p.matches("\\d{9}"))p="967"+p;
        return p;
    }

    void shareWhatsAppToCustomer(String phone,String text,Uri image){
        String p=normalizeWhatsAppPhone(phone);
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType(image!=null?"image/png":"text/plain");
        i.putExtra(Intent.EXTRA_TEXT,text);
        if(image!=null){i.putExtra(Intent.EXTRA_STREAM,image);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        if(!p.isEmpty())i.putExtra("jid",p+"@s.whatsapp.net");
        try{
            i.setPackage("com.whatsapp");startActivity(i);
        }catch(Exception e1){
            try{
                i.setPackage("com.whatsapp.w4b");startActivity(i);
            }catch(Exception e2){
                try{
                    i.removeExtra("jid");
                    i.setPackage("com.whatsapp");startActivity(i);
                }catch(Exception e3){
                    try{
                        i.setPackage(null);startActivity(Intent.createChooser(i,"مشاركة عبر"));
                    }catch(Exception e4){
                        shareText(text);
                    }
                }
            }
        }
    }


    void shareTransferDirectToWhatsApp(String phone,String text){
        String p=normalizeWhatsAppPhone(phone);
        if(p.isEmpty()){ Toast.makeText(this,"رقم واتساب غير صالح",Toast.LENGTH_SHORT).show(); return; }
        Uri uri=Uri.parse("https://wa.me/"+p+"?text="+Uri.encode(text));
        try{
            Intent i=new Intent(Intent.ACTION_VIEW,uri);
            i.setPackage("com.whatsapp");
            startActivity(i);
        }catch(Exception e1){
            try{
                Intent i=new Intent(Intent.ACTION_VIEW,uri);
                i.setPackage("com.whatsapp.w4b");
                startActivity(i);
            }catch(Exception e2){
                try{
                    Intent i=new Intent(Intent.ACTION_VIEW,uri);
                    startActivity(i);
                }catch(Exception e3){
                    shareText(text);
                }
            }
        }
    }


    void showQuickCalculator(double initialTotal){
        final Dialog dlg=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(12));
        box.setBackground(rounded(CARD,dp(18)));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView iconBadge=tv("🧮",20);
        iconBadge.setGravity(Gravity.CENTER);
        head.addView(iconBadge,new LinearLayout.LayoutParams(dp(36),dp(36)));

        LinearLayout headTitles=new LinearLayout(this);
        headTitles.setOrientation(LinearLayout.VERTICAL);
        headTitles.setPadding(dp(6),0,dp(6),0);
        TextView tTitle=tv("حاسبة الصرف والنقد السريعة",15);
        tTitle.setTextColor(GREEN); tTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView tSub=tv("حساب الباقي للزبون والفئات النقدية فوراً",11);
        tSub.setTextColor(MUTED);
        headTitles.addView(tTitle,new LinearLayout.LayoutParams(-1,dp(22)));
        headTitles.addView(tSub,new LinearLayout.LayoutParams(-1,dp(18)));
        head.addView(headTitles,new LinearLayout.LayoutParams(0,dp(52),1));

        Button closeBtn=button("✕");
        closeBtn.setTextColor(MUTED); closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v->dlg.dismiss());
        head.addView(closeBtn,new LinearLayout.LayoutParams(dp(36),dp(36)));
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(44)));
        addSpaceTo(box,8);

        LinearLayout reqRow=new LinearLayout(this);
        reqRow.setOrientation(LinearLayout.HORIZONTAL);
        reqRow.setGravity(Gravity.CENTER_VERTICAL);
        reqRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView reqLabel=tv("المطلوب دفعه:",12);
        reqLabel.setTextColor(TEXT);
        reqRow.addView(reqLabel,new LinearLayout.LayoutParams(-2,-2));
        EditText reqEt=numberField("0");
        if(initialTotal>0) reqEt.setText(fmt(initialTotal));
        reqEt.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        reqEt.setTextSize(15);
        LinearLayout.LayoutParams reqLp=new LinearLayout.LayoutParams(0,dp(42),1);
        reqLp.setMargins(dp(8),0,0,0);
        reqRow.addView(reqEt,reqLp);
        box.addView(reqRow,new LinearLayout.LayoutParams(-1,dp(44)));
        addSpaceTo(box,6);

        LinearLayout paidRow=new LinearLayout(this);
        paidRow.setOrientation(LinearLayout.HORIZONTAL);
        paidRow.setGravity(Gravity.CENTER_VERTICAL);
        paidRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView paidLabel=tv("المستلم من الزبون:",12);
        paidLabel.setTextColor(TEXT);
        paidRow.addView(paidLabel,new LinearLayout.LayoutParams(-2,-2));
        EditText paidEt=numberField("");
        paidEt.setHint("أدخل أو اختر فئة");
        paidEt.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        paidEt.setTextSize(15);
        LinearLayout.LayoutParams paidLp=new LinearLayout.LayoutParams(0,dp(42),1);
        paidLp.setMargins(dp(8),0,0,0);
        paidRow.addView(paidEt,paidLp);
        box.addView(paidRow,new LinearLayout.LayoutParams(-1,dp(44)));
        addSpaceTo(box,6);

        TextView chipsLabel=tv("فئات النقد السريعة:",11);
        chipsLabel.setTextColor(MUTED);
        box.addView(chipsLabel,new LinearLayout.LayoutParams(-1,dp(20)));
        addSpaceTo(box,2);

        LinearLayout chipsRow=new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int[] denoms={5000, 1000, 500, 200, 100, 50};
        for(int d:denoms){
            Button cb=new Button(this);
            cb.setText(String.valueOf(d));
            cb.setTextSize(11);
            cb.setTextColor(GREEN);
            GradientDrawable cbg=new GradientDrawable();
            cbg.setColor(Color.rgb(240,248,242));
            cbg.setCornerRadius(dp(8));
            cbg.setStroke(dp(1),Color.rgb(200,230,210));
            cb.setBackground(cbg);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);
            cp.setMargins(dp(2),0,dp(2),0);
            cb.setOnClickListener(v->paidEt.setText(String.valueOf(d)));
            chipsRow.addView(cb,cp);
        }
        box.addView(chipsRow,new LinearLayout.LayoutParams(-1,dp(48)));
        addSpaceTo(box,8);

        LinearLayout changeCard=new LinearLayout(this);
        changeCard.setOrientation(LinearLayout.VERTICAL);
        changeCard.setGravity(Gravity.CENTER);
        changeCard.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable chBg=new GradientDrawable();
        chBg.setColor(Color.rgb(240,248,255));
        chBg.setCornerRadius(dp(12));
        chBg.setStroke(dp(1.5f),Color.rgb(180,215,245));
        changeCard.setBackground(chBg);

        TextView chTitle=tv("الباقي للزبون",12);
        chTitle.setTextColor(Color.rgb(20,80,160));
        TextView chVal=tv("0.00 ريال",20);
        chVal.setTextColor(Color.rgb(15,70,180));
        chVal.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        chVal.setGravity(Gravity.CENTER);

        changeCard.addView(chTitle,new LinearLayout.LayoutParams(-2,-2));
        changeCard.addView(chVal,new LinearLayout.LayoutParams(-2,-2));
        box.addView(changeCard,new LinearLayout.LayoutParams(-1,dp(64)));
        addSpaceTo(box,8);

        Runnable calcChange=()->{
            double r=0, p=0;
            try{r=Double.parseDouble(reqEt.getText().toString().trim());}catch(Exception ignored){}
            try{p=Double.parseDouble(paidEt.getText().toString().trim());}catch(Exception ignored){}
            double change=p-r;
            if(p<=0){
                chVal.setText("0.00 ريال");
                chVal.setTextColor(Color.rgb(15,70,180));
                chTitle.setText("الباقي للزبون");
            }else if(change>=0){
                chVal.setText(fmt(change)+" ريال");
                chVal.setTextColor(GREEN);
                chTitle.setText("🟢 الباقي للزبون (المتبقي لصالحه)");
                chBg.setColor(Color.rgb(240,249,242));
                chBg.setStroke(dp(1.5f),Color.rgb(180,230,195));
            }else{
                chVal.setText(fmt(Math.abs(change))+" ريال");
                chVal.setTextColor(RED);
                chTitle.setText("🔴 عجز / متبقي عليه (لم يكتمل السداد)");
                chBg.setColor(Color.rgb(255,245,245));
                chBg.setStroke(dp(1.5f),Color.rgb(250,200,200));
            }
        };

        android.text.TextWatcher tw=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){calcChange.run();}
            public void afterTextChanged(android.text.Editable e){}
        };
        reqEt.addTextChangedListener(tw);
        paidEt.addTextChangedListener(tw);
        calcChange.run();

        Button okBtn=button("إغلاق");
        okBtn.setBackground(rounded(GREEN,dp(10)));
        okBtn.setOnClickListener(v->dlg.dismiss());
        box.addView(okBtn,new LinearLayout.LayoutParams(-1,dp(52)));

        dlg.setContentView(box);
        if(dlg.getWindow()!=null){
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlg.getWindow().setLayout(dp(350),WindowManager.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setGravity(Gravity.CENTER);
        }
        dlg.show();
    }


    void showLowStockDialog(){
        final Dialog dlg=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(12));
        box.setBackground(rounded(CARD,dp(18)));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView iconBadge=tv("⚠️",20);
        iconBadge.setGravity(Gravity.CENTER);
        head.addView(iconBadge,new LinearLayout.LayoutParams(dp(36),dp(36)));

        LinearLayout headTitles=new LinearLayout(this);
        headTitles.setOrientation(LinearLayout.VERTICAL);
        headTitles.setPadding(dp(6),0,dp(6),0);
        TextView tTitle=tv("نواقص المخزون والتنبيهات",15);
        tTitle.setTextColor(RED); tTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView tSub=tv("الأصناف التي وصلت للحد الأدنى وتتطلب إعادة طلب",11);
        tSub.setTextColor(MUTED);
        headTitles.addView(tTitle,new LinearLayout.LayoutParams(-1,dp(22)));
        headTitles.addView(tSub,new LinearLayout.LayoutParams(-1,dp(18)));
        head.addView(headTitles,new LinearLayout.LayoutParams(0,dp(52),1));

        Button closeBtn=button("✕");
        closeBtn.setTextColor(MUTED); closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v->dlg.dismiss());
        head.addView(closeBtn,new LinearLayout.LayoutParams(dp(36),dp(36)));
        box.addView(head,new LinearLayout.LayoutParams(-1,dp(44)));
        addSpaceTo(box,8);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Cursor c=db.lowStockItems();
        final StringBuilder shareSb=new StringBuilder();
        shareSb.append("📋 *طلبية نواقص مواد غذائية - بقالة العزي للمواد الغذائية*\n");
        shareSb.append("📅 التاريخ: ").append(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())).append("\n\n");
        int count=0;
        while(c.moveToNext()){
            count++;
            String name=c.getString(1);
            double qty=c.getDouble(2);
            double min=c.getDouble(3);

            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setPadding(dp(10),dp(6),dp(10),dp(6));
            GradientDrawable rbg=new GradientDrawable();
            rbg.setColor(Color.rgb(255,248,248));
            rbg.setCornerRadius(dp(10));
            rbg.setStroke(dp(1),Color.rgb(250,215,215));
            row.setBackground(rbg);

            TextView nTv=tv(count+". "+name,13);
            nTv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            nTv.setTextColor(TEXT);
            row.addView(nTv,new LinearLayout.LayoutParams(0,-2,1));

            TextView qTv=tv("المتوفر: "+fmt(qty)+" (الحد: "+fmt(min)+")",11);
            qTv.setTextColor(RED);
            row.addView(qTv,new LinearLayout.LayoutParams(-2,-2));

            list.addView(row,new LinearLayout.LayoutParams(-1,-2));
            addSpaceTo(list,4);

            shareSb.append("▫️ *").append(name).append("* | الكمية المتبقية: ").append(fmt(qty)).append("\n");
        }
        c.close();

        if(count==0){
            TextView empty=tv("✅ لا توجد أصناف ناقصة حالياً، المخزون مكتمل وجميع الأصناف أعلى من الحد الأدنى.",12.5f);
            empty.setTextColor(GREEN);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12),dp(20),dp(12),dp(20));
            list.addView(empty,new LinearLayout.LayoutParams(-1,-2));
        }

        scroll.addView(list,new LinearLayout.LayoutParams(-1,-2));
        box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(220)));
        addSpaceTo(box,8);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if(count>0){
            Button shareBtn=button("📲 مشاركة في واتساب");
            shareBtn.setBackground(rounded(Color.rgb(22,145,75),dp(10)));
            shareBtn.setTextColor(Color.WHITE);
            shareBtn.setTextSize(12);
            shareBtn.setOnClickListener(v->{
                shareSb.append("\n_تم الإرسال عبر نظام بقالة العزي للمواد الغذائية_");
                shareText(shareSb.toString());
            });
            actions.addView(shareBtn,new LinearLayout.LayoutParams(0,dp(42),1));
            addSpaceTo(actions,6);
        }

        Button closeA=button("إغلاق");
        closeA.setBackground(outline(CARD,10));
        closeA.setTextColor(TEXT);
        closeA.setTextSize(12);
        closeA.setOnClickListener(v->dlg.dismiss());
        actions.addView(closeA,new LinearLayout.LayoutParams(count>0?dp(80):-1,dp(42)));

        box.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));

        dlg.setContentView(box);
        if(dlg.getWindow()!=null){
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlg.getWindow().setLayout(dp(350),WindowManager.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setGravity(Gravity.CENTER);
        }
        dlg.show();
    }


    String invoiceWhatsAppText(String no,String customer,ArrayList<Line> lines,double total,double paid,double balanceAfter,String date){
        StringBuilder s=new StringBuilder();
        s.append("بقالة العزي للمواد الغذائية :\n");
        if(no!=null&&!no.trim().isEmpty()){
            s.append("#").append(no.trim()).append("\n");
        }
        String cust=customer==null?"":customer.trim();
        if(!cust.isEmpty()){
            s.append(cust).append("\n");
        }
        double remaining=total-paid;
        if(paid>=total && total>0){
            s.append("مسدد نقداً ").append(fmt(total)).append(" يمني\n");
        }else if(paid>0){
            s.append("عليك ").append(fmt(remaining)).append(" يمني (مدفوع: ").append(fmt(paid)).append(" يمني)\n");
        }else{
            s.append("عليك ").append(fmt(total)).append(" يمني\n");
        }
        s.append("حق ");
        if(lines!=null&&!lines.isEmpty()){
            StringBuilder items=new StringBuilder();
            for(int i=0;i<lines.size();i++){
                Line l=lines.get(i);
                if(i>0) items.append("، ");
                String iname=l.name==null?"":l.name.trim();
                items.append(iname);
                if(l.qty!=1){
                    items.append(" (").append(fmt(l.qty)).append(")");
                }
            }
            s.append(items.toString());
        }else{
            s.append(!cust.isEmpty()?cust:"مشتريات");
        }
        s.append("\n\n");
        if(!cust.isEmpty()&&Math.abs(balanceAfter)>=0.005){
            if(balanceAfter>0.005){
                s.append("الإجمالي - عليك ").append(fmt(balanceAfter)).append(" يمني");
            }else{
                s.append("الإجمالي - له ").append(fmt(Math.abs(balanceAfter))).append(" يمني");
            }
        }else{
            s.append("الإجمالي - خالص (0 يمني)");
        }
        return s.toString();
    }


    Bitmap invoiceReceiptBitmap(String no,String customer,ArrayList<Line> lines,double total,double paid,double balanceAfter,String date){
        final int width=480;
        final int margin=18;
        final int lineH=28;
        int rowCount=lines.size();
        int baseHeight=270+rowCount*lineH+(paid>0?48:0)+(customer!=null&&!customer.isEmpty()&&Math.abs(balanceAfter)>=0.005?50:0);
        Bitmap b=Bitmap.createBitmap(width,Math.max(380,baseHeight),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokeP=new Paint(Paint.ANTI_ALIAS_FLAG);strokeP.setStyle(Paint.Style.STROKE);strokeP.setStrokeWidth(2);strokeP.setColor(Color.rgb(220,230,222));
        Paint fillP=new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background Box
        canvas.drawRoundRect(8,8,width-8,b.getHeight()-8,14,14,strokeP);

        // Header Background Ribbon
        fillP.setColor(Color.rgb(240,248,242));
        canvas.drawRoundRect(12,12,width-12,82,10,10,fillP);

        // Store Icon & Title

        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(23);p.setColor(GREEN);p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("بقالة العزي للمواد الغذائية",width-margin-10,44,p);

        p.setTextSize(12.5f);p.setColor(DARK);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        canvas.drawText("فاتورة #"+no+"  •  "+(date==null||date.isEmpty()?db.now():date),width-margin-10,68,p);

        int y=106;
        String custName=customer==null||customer.trim().isEmpty()?"عميل نقدي":customer.trim();
        fillP.setColor(Color.rgb(250,252,250));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,fillP);
        strokeP.setColor(Color.rgb(230,238,232));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,strokeP);

        p.setTextSize(13);p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setColor(DARK);p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("العميل: "+custName,width-margin-12,y+24,p);
        y+=48;

        // Current Operation / Amount Card
        double rem=total-paid;
        boolean isCash=paid>=total && total>0;
        int opBg=isCash?Color.rgb(240,250,242):(rem>0?Color.rgb(255,243,243):Color.rgb(240,248,255));
        int opStroke=isCash?Color.rgb(190,235,205):(rem>0?Color.rgb(250,195,195):Color.rgb(195,225,250));
        int opColor=isCash?GREEN:(rem>0?RED:BLUE);
        fillP.setColor(opBg);
        canvas.drawRoundRect(margin,y,width-margin,y+42,8,8,fillP);
        strokeP.setColor(opStroke);
        canvas.drawRoundRect(margin,y,width-margin,y+42,8,8,strokeP);

        p.setColor(opColor);p.setTextSize(15);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        String opText=isCash?"مسدد نقداً: "+fmt(total)+" يمني":(paid>0?"عليك: "+fmt(rem)+" يمني (مدفوع: "+fmt(paid)+")":"عليك: "+fmt(total)+" يمني");
        canvas.drawText(opText,width/2,y+26,p);
        y+=50;

        // Table Header
        fillP.setColor(GREEN);
        canvas.drawRoundRect(margin,y,width-margin,y+28,5,5,fillP);
        p.setColor(Color.WHITE);p.setTextSize(12.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("الصنف (حق)",width-margin-12,y+19,p);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("الكمية",width-margin-210,y+19,p);
        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("الإجمالي",margin+12,y+19,p);
        y+=32;

        // Table Rows
        p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(12);
        for(int i=0;i<lines.size();i++){
            Line l=lines.get(i);
            fillP.setColor(i%2==0?Color.rgb(252,254,252):Color.WHITE);
            canvas.drawRect(margin,y,width-margin,y+lineH,fillP);
            strokeP.setColor(Color.rgb(240,244,240));
            canvas.drawLine(margin,y+lineH,width-margin,y+lineH,strokeP);

            p.setColor(TEXT);p.setTextAlign(Paint.Align.RIGHT);
            String iname=l.name==null?"":l.name.trim();
            if(iname.length()>22)iname=iname.substring(0,22)+"…";
            canvas.drawText(iname,width-margin-12,y+18,p);

            p.setTextAlign(Paint.Align.CENTER);p.setColor(MUTED);
            canvas.drawText("× "+fmt(l.qty),width-margin-210,y+18,p);

            p.setTextAlign(Paint.Align.LEFT);p.setColor(GREEN);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            canvas.drawText(fmt(l.total)+" ر.ي",margin+12,y+18,p);
            p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
            y+=lineH;
        }

        y+=10;
        // Total Bar
        fillP.setColor(Color.rgb(240,249,242));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,fillP);
        strokeP.setColor(Color.rgb(190,230,205));
        canvas.drawRoundRect(margin,y,width-margin,y+38,8,8,strokeP);

        p.setColor(GREEN);p.setTextSize(15);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("إجمالي الفاتورة:",width-margin-12,y+24,p);
        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fmt(total)+" ريال",margin+12,y+24,p);
        y+=44;

        // Balance Card (الإجمالي - حساب العميل)
        if(customer!=null&&!customer.trim().isEmpty()&&Math.abs(balanceAfter)>=0.005){
            fillP.setColor(balanceAfter>0.005?Color.rgb(255,243,243):Color.rgb(240,248,255));
            strokeP.setColor(balanceAfter>0.005?Color.rgb(245,200,200):Color.rgb(200,225,250));
            canvas.drawRoundRect(margin,y,width-margin,y+36,8,8,fillP);
            canvas.drawRoundRect(margin,y,width-margin,y+36,8,8,strokeP);

            p.setColor(balanceColor(balanceAfter));p.setTextSize(13.5f);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            String bText=balanceAfter>0?"الإجمالي - عليك "+fmt(balanceAfter)+" يمني":"الإجمالي - له "+fmt(Math.abs(balanceAfter))+" يمني";
            canvas.drawText(bText,width/2,y+23,p);
            y+=42;
        }

        y+=8;
        p.setTextAlign(Paint.Align.CENTER);p.setColor(MUTED);p.setTextSize(11);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        canvas.drawText("✨ شكراً لتعاملكم معنا • بقالة العزي للمواد الغذائية ✨",width/2,y+12,p);
        y+=22;

        return Bitmap.createBitmap(b,0,0,width,Math.min(y+10,b.getHeight()));
    }


    Bitmap receiptBitmap(String text){ return receiptBitmap(text,384); }


Bitmap receiptBitmap(String text,int targetWidth){
        final int width=384;
        final int margin=14;
        final int black=Color.BLACK;
        final int gray=Color.rgb(80,80,80);
        final int green=Color.rgb(20,105,55);
        final int lineH=24;
        String[] ls=text.split("\n",-1);
        int rows=0;
        for(String s:ls) if(s.contains(" | ") || s.contains(" × ")) rows++;
        int height=160+rows*lineH+ls.length*18;
        Bitmap b=Bitmap.createBitmap(width,Math.max(260,height),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        p.setColor(black);
        p.setTextAlign(Paint.Align.CENTER);

        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(19);p.setColor(green);canvas.drawText("بقالة العزي للمواد الغذائية",width/2,80,p);

        int y=108;
        for(String line:ls){
            if(line==null||line.trim().isEmpty()){y+=8;continue;}
            String l=line.trim();
            if(l.equals("بقالة العزي للمواد الغذائية")||l.equals("🛒 *بقالة العزي للمواد الغذائية*")||l.equals("🧾 *بقالة العزي للمواد الغذائية*")) continue;
            if(l.startsWith("━━")||l.startsWith("──")||l.equals("------------------------------")){
                p.setColor(Color.LTGRAY);canvas.drawLine(margin,y,width-margin,y,p);y+=12;continue;
            }
            if(l.contains(" | ")){
                String[] q=l.split(" \\| ",-1);
                if(q.length>=3){
                    p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(11.5f);p.setColor(black);
                    String item=q[0].trim().replace("▪️","").trim();
                    if(item.length()>17)item=item.substring(0,17)+"…";
                    p.setTextAlign(Paint.Align.RIGHT);canvas.drawText(item,width-margin,y,p);
                    p.setTextAlign(Paint.Align.CENTER);canvas.drawText(q[1].trim(),width/2,y,p);
                    p.setTextAlign(Paint.Align.LEFT);canvas.drawText(q[2].trim(),margin,y,p);
                    y+=lineH;continue;
                }
            }
            if(l.startsWith("▪️")&&l.contains("=")){
                p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(11.5f);p.setColor(black);
                p.setTextAlign(Paint.Align.RIGHT);canvas.drawText(l,width-margin,y,p);y+=20;continue;
            }
            if(l.startsWith("الإجمالي")||l.startsWith("💰 *إجمالي")||l.startsWith("رصيدكم")){
                p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(14);p.setColor(green);
                p.setTextAlign(Paint.Align.CENTER);canvas.drawText(l.replace("*",""),width/2,y+4,p);y+=24;continue;
            }
            p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(11);p.setColor(gray);
            p.setTextAlign(Paint.Align.CENTER);canvas.drawText(l.replace("*",""),width/2,y,p);y+=18;
        }
        Bitmap out=Bitmap.createBitmap(b,0,0,width,Math.min(y+16,b.getHeight()));
        if(targetWidth!=width) out=Bitmap.createScaledBitmap(out,targetWidth,Math.max(1,Math.round(out.getHeight()*targetWidth/(float)width)),true);
        return out;
    }

Uri saveReceiptBitmap(Bitmap bitmap,String no)throws Exception{
        File dir=new File(getCacheDir(),"receipts");if(!dir.exists())dir.mkdirs();
        File file=new File(dir,"invoice_"+no+"_"+System.currentTimeMillis()+".png");
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.flush();}
        return FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
    }

    void shareReceiptImageAndText(String no,String customer,ArrayList<Line> lines,double total,double paid){
        try{
            long cid=customer==null||customer.trim().isEmpty()?-1:db.customerIdByName(customer.trim());
            double bal=cid>0?db.balance(cid):0;
            String text=invoiceWhatsAppText(no,customer,lines,total,paid,bal,db.now());
            Bitmap b=invoiceReceiptBitmap(no,customer,lines,total,paid,bal,db.now());
            Uri uri=saveReceiptBitmap(b,no);
            String phone=db.phoneByName(customer);
            shareWhatsAppToCustomer(phone,text,uri);
        }catch(Exception e){
            long cid=customer==null||customer.trim().isEmpty()?-1:db.customerIdByName(customer.trim());
            shareText(invoiceWhatsAppText(no,customer,lines,total,paid,cid>0?db.balance(cid):0,db.now()));
        }
    }
    String pendingPrintNo="",pendingPrintCustomer="";ArrayList<Line> pendingPrintLines;double pendingPrintTotal;String pendingPrintText="";int pendingPrintWidth=384;


    void printTextBluetooth(String text){ printTextBluetooth(text,384); }

void printTextBluetooth(String text,int requestedWidth){
        final int printWidth=(requestedWidth>=576?576:384);
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){
            pendingPrintText=text; pendingPrintWidth=printWidth; requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},5102);return;
        }
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null){Toast.makeText(this,"هذا الجهاز لا يدعم البلوتوث",Toast.LENGTH_LONG).show();return;}
        if(!adapter.isEnabled()){Toast.makeText(this,"فعّل البلوتوث ثم أعد الضغط على الطباعة",Toast.LENGTH_LONG).show();return;}
        Set<BluetoothDevice> paired=adapter.getBondedDevices();
        if(paired==null||paired.isEmpty()){Toast.makeText(this,"لا توجد طابعة مقترنة. اقترن بالطابعة من إعدادات البلوتوث أولاً.",Toast.LENGTH_LONG).show();return;}
        BluetoothDevice[] devices=paired.toArray(new BluetoothDevice[0]);String[] names=new String[devices.length];
        for(int i=0;i<devices.length;i++)names[i]=(devices[i].getName()==null?"طابعة بلوتوث":devices[i].getName())+"\n"+devices[i].getAddress();
        new AlertDialog.Builder(this).setTitle("اختر عرض الطباعة")
            .setItems(new String[]{"58mm — إيصال مضغوط","80mm — إيصال عريض"},(d,choice)->{
                int width=choice==1?576:384;
                new AlertDialog.Builder(this).setTitle("اختر طابعة البلوتوث").setItems(names,(d2,w)->{
                    Bitmap bitmap=receiptBitmap(text,width);
                    new Thread(()->sendBitmapToBluetooth(devices[w],bitmap)).start();
                }).setNegativeButton("إلغاء",null).show();
            }).setNegativeButton("إلغاء",null).show();
    }

    void sendBitmapToBluetooth(BluetoothDevice device,Bitmap bitmap){
        BluetoothSocket socket=null;OutputStream out=null;
        try{
            UUID spp=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            try{
                socket=device.createRfcommSocketToServiceRecord(spp);
                socket.connect();
            }catch(Exception first){
                try{if(socket!=null)socket.close();}catch(Exception ignored){}
                socket=device.createInsecureRfcommSocketToServiceRecord(spp);
                socket.connect();
            }
            out=socket.getOutputStream();
            out.write(new byte[]{0x1B,0x40});                 // تهيئة الطابعة
            out.write(rasterBytes(bitmap));                  // 384px = 58mm على طابعات 203dpi
            out.write(new byte[]{0x1B,0x64,0x04});            // تغذية الورق 4 أسطر
            out.write(new byte[]{0x1D,0x56,0x00});            // قص
            out.flush();
            runOnUiThread(()->Toast.makeText(this,"تمت الطباعة بنجاح على طابعة 58mm",Toast.LENGTH_SHORT).show());
        }catch(Exception e){
            runOnUiThread(()->Toast.makeText(this,"تعذر إتمام الطباعة. تأكد من تشغيل الطابعة وتوفر الورق.",Toast.LENGTH_LONG).show());
        }finally{
            try{if(out!=null)out.close();}catch(Exception ignored){}
            try{if(socket!=null)socket.close();}catch(Exception ignored){}
        }
    }

    void printInvoiceBluetooth(String no,String customer,ArrayList<Line> lines,double total){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){
            pendingPrintNo=no;pendingPrintCustomer=customer;pendingPrintLines=new ArrayList<>(lines);pendingPrintTotal=total;
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},5101);return;
        }
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null){Toast.makeText(this,"هذا الجهاز لا يدعم البلوتوث",Toast.LENGTH_LONG).show();return;}
        if(!adapter.isEnabled()){Toast.makeText(this,"فعّل البلوتوث ثم أعد الضغط على الطباعة",Toast.LENGTH_LONG).show();return;}
        Set<BluetoothDevice> paired=adapter.getBondedDevices();
        if(paired==null||paired.isEmpty()){Toast.makeText(this,"لا توجد طابعة مقترنة. اقترن بالطابعة من إعدادات البلوتوث أولاً.",Toast.LENGTH_LONG).show();return;}
        BluetoothDevice[] devices=paired.toArray(new BluetoothDevice[0]);String[] names=new String[devices.length];
        for(int i=0;i<devices.length;i++)names[i]=(devices[i].getName()==null?"طابعة بلوتوث":devices[i].getName())+"\n"+devices[i].getAddress();
        new AlertDialog.Builder(this).setTitle("اختر طابعة 58mm").setItems(names,(d,w)->printToBluetooth(devices[w],no,customer,lines,total)).setNegativeButton("إلغاء",null).show();
    }

    void printToBluetooth(BluetoothDevice device,String no,String customer,ArrayList<Line> lines,double total){
        long cid=customer==null||customer.trim().isEmpty()?-1:db.customerIdByName(customer.trim());
        String text=receiptTextFromLines(no,customer,lines,total,cid);
        Bitmap bitmap=receiptBitmap(text);
        new Thread(()->{
            sendBitmapToBluetooth(device,bitmap);
        }).start();
    }

    byte[] rasterBytes(Bitmap bitmap){
        int width=bitmap.getWidth(),height=bitmap.getHeight(),bpr=(width+7)/8;
        byte[] out=new byte[8+bpr*height];
        out[0]=0x1D;out[1]=0x76;out[2]=0x30;out[3]=0;
        out[4]=(byte)(bpr&255);out[5]=(byte)((bpr>>8)&255);
        out[6]=(byte)(height&255);out[7]=(byte)((height>>8)&255);
        int p=8;
        for(int y=0;y<height;y++){
            for(int xb=0;xb<bpr;xb++){
                int v=0;
                for(int bit=0;bit<8;bit++){
                    int x=xb*8+bit;
                    if(x<width){
                        int px=bitmap.getPixel(x,y);
                        int g=(Color.red(px)+Color.green(px)+Color.blue(px))/3;
                        if(g<180)v|=1<<(7-bit);
                    }
                }
                out[p++]=(byte)v;
            }
        }
        return out;
    }

    void thermalPreview(String no,String customer,LinearLayout rows,double total){preview(no,customer,new ArrayList<Line>(),total,false,-1);}

    static String fmt(double x){String v=String.format(Locale.US,"%,.2f",x);return v.endsWith(".00")?v.substring(0,v.length()-3):v;}


    void showCustomerCreatePopup(){
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(16),dp(12),dp(16),dp(14));
        box.setBackground(rounded(CARD,dp(18)));

        TextView title=tv("👤 إنشاء عميل جديد",18);
        title.setTextColor(GREEN);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView hint=tv("أدخل بيانات العميل ثم اضغط حفظ. ستغلق النافذة تلقائياً بعد الحفظ.",11);
        hint.setTextColor(MUTED);
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(30)));

        EditText name=field("اسم العميل *");
        EditText phone=phoneField("رقم الهاتف (اختياري)");
        customerNameInput=name; customerPhoneInput=phone;
        box.addView(name,new LinearLayout.LayoutParams(-1,dp(48)));
        spaceInside(box,6);
        box.addView(phone,new LinearLayout.LayoutParams(-1,dp(48)));
        spaceInside(box,10);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button save=button("✓ حفظ وإغلاق");
        save.setTextColor(Color.WHITE);
        save.setBackground(rounded(GREEN,dp(11)));

        Button contact=button("👥 جهات الاتصال");
        contact.setTextColor(GREEN);
        contact.setBackground(outline(Color.rgb(241,247,242),10));
        contact.setOnClickListener(v->importContact());

        actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams acp=new LinearLayout.LayoutParams(0,dp(44),1);
        acp.setMargins(dp(5),0,0,0);
        actions.addView(contact,acp);
        box.addView(actions,new LinearLayout.LayoutParams(-1,dp(46)));

        save.setOnClickListener(v->{
            String n=name.getText().toString().trim();
            String p=phone.getText().toString().trim();
            if(n.isEmpty()){
                name.requestFocus();
                Toast.makeText(this,"اكتب اسم العميل أولاً",Toast.LENGTH_SHORT).show();
                return;
            }
            try{
                db.addCustomer(n,p);
                dialog.dismiss();
                customers();
                Toast.makeText(this,"تم حفظ العميل وإغلاق النافذة",Toast.LENGTH_SHORT).show();
            }catch(Throwable e){
                Toast.makeText(this,"تعذر حفظ العميل: "+e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });

        dialog.setContentView(box);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(x->{
            Window ww=dialog.getWindow();
            if(ww!=null){
                int width=(int)(getResources().getDisplayMetrics().widthPixels*0.92f);
                ww.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
            }
            name.requestFocus();
        });
        dialog.show();
    }


    void addSpaceTo(LinearLayout p,int h){Space s=new Space(this);p.addView(s,new LinearLayout.LayoutParams(1,h));}

    

    void applyDenseGlassPage(){
        try{
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8,72,78),Color.rgb(18,105,105),Color.rgb(82,66,38)}));
            if(root.getChildCount()>0) root.getChildAt(0).setVisibility(View.GONE);
            if(content!=null){
                if(content.getChildCount()>0) content.removeViewAt(0);
                content.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.argb(215,5,65,72),Color.argb(205,18,95,96),Color.argb(190,76,61,35)}));
                content.setPadding(dp(5),dp(5),dp(5),dp(7));
            }
            if(bottom!=null&&bottom.getChildCount()>0){
                View nav=bottom.getChildAt(0);
                nav.setBackground(outlined(Color.argb(150,255,255,255),dp(1),dp(18)));
            }
        }catch(Throwable ignored){}
    }
    TextView denseText(String s,float max,float min,int color){
        TextView t=tv(s,max);
        t.setTextColor(color);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setEllipsize(null);
        t.setHorizontallyScrolling(false);
        t.setIncludeFontPadding(true);
        if(Build.VERSION.SDK_INT>=28){
            try{t.setFallbackLineSpacing(true);}catch(Throwable ignored){}
            try{t.setElegantTextHeight(true);}catch(Throwable ignored){}
        }
        if(Build.VERSION.SDK_INT>=26){
            try{
                int lo=Math.max(8,Math.round(min));
                int hi=Math.max(lo+1,Math.round(max));
                t.setAutoSizeTextTypeUniformWithConfiguration(lo,hi,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            }catch(Throwable ignored){}
        }
        return t;
    }
    GradientDrawable glassFill(int color){
        return outlined(color,dp(1),dp(14));
    }

void account(long id,String name){
        base("حساب العميل");
        applyDenseGlassPage();

        final String customerPhone=db.phoneByName(name);
        final ArrayList<Long> selectedIds=new ArrayList<>();
        double currentBal=db.balance(id);
        double totalDebit=db.customerDebitTotal(id);
        double totalCredit=db.customerCreditTotal(id);
        int totalOps=db.transactionCount(id);

        // شريط علوي صغير مطابق للواجهة المرجعية.
        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button back=button("‹"); back.setTextSize(24); back.setTextColor(Color.WHITE); back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v->goBack());
        head.addView(back,new LinearLayout.LayoutParams(dp(32),dp(34)));
        TextView hname=denseText(name,11,8.5f,Color.WHITE);
        hname.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        head.addView(hname,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView balHead=denseText(balanceText(currentBal),9,8,Color.WHITE);
        balHead.setGravity(Gravity.CENTER);
        balHead.setBackground(glassFill(Color.argb(80,255,255,255)));
        head.addView(balHead,new LinearLayout.LayoutParams(dp(118),dp(30)));
        content.addView(head,new LinearLayout.LayoutParams(-1,dp(48)));

        // بطاقة العميل المختصرة.
        LinearLayout profile=new LinearLayout(this);
        profile.setOrientation(LinearLayout.HORIZONTAL); profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); profile.setPadding(dp(4),dp(3),dp(4),dp(3));
        profile.setBackground(glassFill(Color.argb(165,255,255,255)));

        TextView avatar=denseText(name==null||name.trim().isEmpty()?"ب":name.trim().substring(0,1),12,8.5f,Color.WHITE);
        avatar.setGravity(Gravity.CENTER); avatar.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        avatar.setBackground(rounded(Color.rgb(22,195,112),dp(13)));
        profile.addView(avatar,new LinearLayout.LayoutParams(dp(26),dp(26)));

        LinearLayout pn=new LinearLayout(this); pn.setOrientation(LinearLayout.VERTICAL); pn.setGravity(Gravity.CENTER_VERTICAL);
        pn.setPadding(dp(5),0,dp(4),0);
        TextView nm=denseText(name,10.5f,8f,DARK); nm.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        pn.addView(nm,new LinearLayout.LayoutParams(-1,dp(18)));
        TextView ph=denseText(customerPhone.isEmpty()?"بدون رقم":"📱 "+customerPhone,8.5f,7.5f,MUTED);
        pn.addView(ph,new LinearLayout.LayoutParams(-1,dp(14)));
        profile.addView(pn,new LinearLayout.LayoutParams(0,dp(32),1));

        if(!customerPhone.isEmpty()){
            Button call=button("☎"); call.setTextSize(11); call.setPadding(0,0,0,0); call.setBackgroundColor(Color.TRANSPARENT);
            String clean=customerPhone.replaceAll("[^0-9+]","");
            call.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+clean)));}catch(Exception ignored){}});
            profile.addView(call,new LinearLayout.LayoutParams(dp(30),dp(30)));
            Button wa=button("●"); wa.setTextSize(9); wa.setTextColor(Color.rgb(25,180,100)); wa.setPadding(0,0,0,0); wa.setBackgroundColor(Color.TRANSPARENT);
            wa.setOnClickListener(v->shareWhatsAppToCustomer(customerPhone,"السلام عليكم أخي "+name+"\nرصيد حسابكم الحالي: "+balanceText(currentBal),null));
            profile.addView(wa,new LinearLayout.LayoutParams(dp(30),dp(30)));
        }
        Button edit=button("⋮"); edit.setTextSize(15); edit.setPadding(0,0,0,0); edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setOnClickListener(v->customerActions(id,name));
        profile.addView(edit,new LinearLayout.LayoutParams(dp(28),dp(30)));
        content.addView(profile,new LinearLayout.LayoutParams(-1,dp(50)));

        // إحصاءات مصغرة في صف واحد.
        LinearLayout stats=new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView s1=denseText("عليه "+fmt(totalDebit)+" ر.ي",8.5f,7.5f,RED);
        TextView s2=denseText("له "+fmt(totalCredit)+" ر.ي",8.5f,7.5f,GREEN);
        TextView s3=denseText(totalOps+" حركة",8.5f,7.5f,BLUE);
        for(TextView s:new TextView[]{s1,s2,s3}){s.setGravity(Gravity.CENTER);s.setBackground(glassFill(Color.argb(155,255,255,255)));}
        stats.addView(s1,new LinearLayout.LayoutParams(0,dp(28),1));
        LinearLayout.LayoutParams s2p=new LinearLayout.LayoutParams(0,dp(28),1);s2p.setMargins(dp(3),0,0,0);stats.addView(s2,s2p);
        LinearLayout.LayoutParams s3p=new LinearLayout.LayoutParams(0,dp(28),1);s3p.setMargins(dp(3),0,0,0);stats.addView(s3,s3p);
        content.addView(stats,new LinearLayout.LayoutParams(-1,dp(30)));

        // الرصيد الحالي كحبة صغيرة.
        TextView current=denseText(currentBal>0.005?"رصيدكم عليكم: "+fmt(currentBal)+" ريال":
            currentBal<-0.005?"رصيد للعميل: "+fmt(Math.abs(currentBal))+" ريال":"رصيدكم عليكم: 0 ريال",10,8f,
            currentBal>0.005?RED:(currentBal<-0.005?BLUE:GREEN));
        current.setGravity(Gravity.CENTER);
        current.setBackground(glassFill(currentBal>0.005?Color.argb(205,255,105,115):Color.argb(185,255,255,255)));
        content.addView(current,new LinearLayout.LayoutParams(-1,dp(30)));

        // إدخال حركة مختصر.
        LinearLayout add=new LinearLayout(this); add.setOrientation(LinearLayout.HORIZONTAL); add.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText amount=numberField("المبلغ");
        EditText detail=field("البيان / تفاصيل العملية");
        amount.setTextSize(10); detail.setTextSize(11);
        add.addView(amount,new LinearLayout.LayoutParams(0,dp(52),.8f));
        LinearLayout detailBox=new LinearLayout(this);detailBox.setOrientation(LinearLayout.HORIZONTAL);detailBox.setGravity(Gravity.CENTER_VERTICAL);
        detailBox.addView(detail,new LinearLayout.LayoutParams(0,dp(52),1));
        addVoiceButton(detailBox,detail,REQ_VOICE_DETAIL,"تحدث بالبيان أو تفاصيل العملية");
        LinearLayout.LayoutParams dd=new LinearLayout.LayoutParams(0,dp(42),1.7f);dd.setMargins(dp(4),0,0,0);add.addView(detailBox,dd);
        content.addView(add,new LinearLayout.LayoutParams(-1,dp(35)));

        LinearLayout addBtns=new LinearLayout(this); addBtns.setOrientation(LinearLayout.HORIZONTAL); addBtns.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button debit=button("عليه +"); debit.setTextSize(9); debit.setTextColor(Color.WHITE); debit.setBackground(rounded(RED,dp(11)));
        Button credit=button("سداد ✓"); credit.setTextSize(9); credit.setTextColor(Color.WHITE); credit.setBackground(rounded(GREEN,dp(11)));
        addBtns.addView(debit,new LinearLayout.LayoutParams(0,dp(32),1));
        LinearLayout.LayoutParams cbp=new LinearLayout.LayoutParams(0,dp(32),1);cbp.setMargins(dp(3),0,0,0);addBtns.addView(credit,cbp);
        content.addView(addBtns,new LinearLayout.LayoutParams(-1,dp(33)));

        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);tools.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button pdf=button("PDF"), img=button("صورة"), waShare=button("واتساب"), shareSel=button("مشاركة"), printSel=button("58mm");
        Button[] toolBtns={pdf,img,waShare,shareSel,printSel};
        for(Button b:toolBtns){b.setTextSize(8.5f);b.setPadding(0,0,0,0);b.setBackground(glassFill(Color.argb(160,255,255,255)));b.setTextColor(DARK);}
        pdf.setOnClickListener(v->shareAccountPdfToWhatsApp(id,name));
        img.setOnClickListener(v->saveAccountStatementImage(id,name));
        waShare.setOnClickListener(v->shareAccountPdfToWhatsApp(id,name));
        shareSel.setOnClickListener(v->{if(selectedIds.isEmpty())Toast.makeText(this,"حدد عملية أولاً",Toast.LENGTH_SHORT).show();else shareSelectedTransactions(id,name,new ArrayList<>(selectedIds));});
        printSel.setOnClickListener(v->{if(selectedIds.isEmpty())Toast.makeText(this,"حدد عملية أولاً",Toast.LENGTH_SHORT).show();else printSelectedTransactions(id,name,new ArrayList<>(selectedIds));});
        for(int i=0;i<toolBtns.length;i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(29),1);if(i>0)p.setMargins(dp(3),0,0,0);tools.addView(toolBtns[i],p);}
        content.addView(tools,new LinearLayout.LayoutParams(-1,dp(30)));

        TextView title=denseText("عمليات العميل",10,8f,Color.WHITE);title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(22)));

        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list,new LinearLayout.LayoutParams(-1,-2));
        final ArrayList<CheckBox> checks=new ArrayList<>();

        Runnable refresh=()->{
            list.removeAllViews();checks.clear();selectedIds.clear();
            Cursor c=db.transactions(id);double running=db.balance(id);int count=0;
            while(c.moveToNext()){
                long tid=c.getLong(0);String date=c.getString(1),d=c.getString(2);double a=c.getDouble(3);int type=c.getInt(4);
                count++;String inv=db.invoiceNoFromTransaction(d);boolean isInv=!inv.isEmpty();
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setPadding(dp(3),dp(1),dp(3),dp(1));row.setBackground(glassFill(Color.argb(170,255,255,255)));
                CheckBox ck=new CheckBox(this);ck.setPadding(0,0,0,0);checks.add(ck);
                ck.setOnCheckedChangeListener((v,on)->{if(on){if(!selectedIds.contains(tid))selectedIds.add(tid);}else selectedIds.remove(tid);});
                row.addView(ck,new LinearLayout.LayoutParams(dp(25),dp(34)));
                TextView ico=denseText(isInv?"🧾":(type==1?"🔴":"🟢"),10,8f,type==1?RED:GREEN);ico.setGravity(Gravity.CENTER);
                row.addView(ico,new LinearLayout.LayoutParams(dp(24),dp(34)));
                LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(3),0,dp(3),0);
                TextView main=denseText(isInv?"فاتورة #"+inv:(d==null||d.trim().isEmpty()?(type==1?"قيد سحب":"دفعة سداد"):d.trim()),9.5f,8f,DARK);main.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                TextView sub=denseText(date+" • الرصيد بعد "+balanceText(running),7.8f,7f,MUTED);
                info.addView(main,new LinearLayout.LayoutParams(-1,dp(17)));info.addView(sub,new LinearLayout.LayoutParams(-1,dp(14)));
                row.addView(info,new LinearLayout.LayoutParams(0,dp(46),1));
                TextView amt=denseText((type==1?"عليه ":"له ")+fmt(a)+" ر.ي",9,8f,type==1?RED:GREEN);amt.setGravity(Gravity.CENTER);
                amt.setBackground(glassFill(type==1?Color.argb(205,255,220,223):Color.argb(185,220,250,230)));
                row.addView(amt,new LinearLayout.LayoutParams(dp(96),dp(25)));
                row.setOnClickListener(v->showOperationDetails(name,tid,d,a,type));
                row.setOnLongClickListener(v->{operationActions(id,name,tid,d,a,type);return true;});
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.setMargins(0,0,0,dp(2));list.addView(row,rp);
                running-=(type==1?a:-a);
            }
            c.close();
            if(count==0){TextView e=denseText("لا توجد عمليات مسجلة",10,8f,Color.WHITE);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));}
        };
        Runnable save=( )->{};
        View.OnClickListener addTx=v->{
            try{
                double a=Double.parseDouble(amount.getText().toString().trim());if(a<=0)throw new Exception();
                String d=detail.getText().toString().trim();int type=v==debit?1:0;
                db.addTransaction(id,a,d,type,db.now());
                account(id,name);
                showPostSaveOperation("تم حفظ العملية","العميل: "+name+"\nالمبلغ: "+(type==1?"عليه ":"له ")+fmt(a)+" ريال\nالرصيد: "+balanceText(db.balance(id)),
                    ()->shareOperationImage(name,d,a,type,""),()->{});
            }catch(Exception e){Toast.makeText(this,"أدخل المبلغ بشكل صحيح",Toast.LENGTH_SHORT).show();}
        };
        debit.setOnClickListener(addTx);credit.setOnClickListener(addTx);
        refresh.run();
    }


void customers(){
        base("الحسابات والعملاء");
        applyDenseGlassPage();

        // البحث هو العنصر الأول، تماماً كالتصميم المرجعي.
        LinearLayout searchBar=new LinearLayout(this);searchBar.setOrientation(LinearLayout.HORIZONTAL);searchBar.setGravity(Gravity.CENTER_VERTICAL);searchBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText search=field("ابحث باسم العميل أو الرقم...");
        search.setTextSize(10);search.setSingleLine(true);search.setPadding(dp(7),0,dp(7),0);search.setBackground(glassFill(Color.argb(170,255,255,255)));
        searchBar.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));
        Button mic=button("🎙");mic.setTextSize(10);mic.setPadding(0,0,0,0);mic.setTextColor(Color.WHITE);mic.setBackground(glassFill(Color.argb(120,255,255,255)));
        mic.setOnClickListener(v->{search.setTag("searchVoice");startVoiceInput(search,"تحدث باسم العميل أو رقم الهاتف",REQ_VOICE_SEARCH);});
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(34),dp(34));mp.setMargins(dp(3),0,0,0);searchBar.addView(mic,mp);
        Button plus=button("+");plus.setTextSize(17);plus.setTextColor(Color.WHITE);plus.setBackground(rounded(Color.rgb(16,181,111),dp(17)));plus.setPadding(0,0,0,0);plus.setOnClickListener(v->showCustomerCreatePopup());
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(34),dp(34));pp.setMargins(dp(3),0,0,0);searchBar.addView(plus,pp);
        content.addView(searchBar,new LinearLayout.LayoutParams(-1,dp(48)));

        double debts=db.totalDebts(), credits=db.totalCredits();int customers=db.customerCount();
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView a=denseText("العملاء "+customers,8.7f,7.8f,DARK),b=denseText("لهم "+fmt(credits)+" ر.ي",8.7f,7.8f,BLUE),d=denseText("عليهم "+fmt(debts)+" ر.ي",8.7f,7.8f,RED);
        for(TextView t:new TextView[]{a,b,d}){t.setGravity(Gravity.CENTER);t.setBackground(glassFill(Color.argb(145,255,255,255)));}
        stats.addView(a,new LinearLayout.LayoutParams(0,27,1));LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(0,27,1);sb.setMargins(dp(3),0,0,0);stats.addView(b,sb);LinearLayout.LayoutParams sd=new LinearLayout.LayoutParams(0,27,1);sd.setMargins(dp(3),0,0,0);stats.addView(d,sd);
        content.addView(stats,new LinearLayout.LayoutParams(-1,dp(29)));

        LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);filters.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final int[] mode={0};Button all=button("الكل"), debt=button("عليهم"), clear=button("خالص");
        Button[] fb={all,debt,clear};for(Button x:fb){x.setTextSize(8.5f);x.setPadding(0,0,0,0);}
        Runnable fs=()->{all.setBackground(mode[0]==0?rounded(Color.rgb(18,175,111),dp(11)):glassFill(Color.argb(135,255,255,255)));debt.setBackground(mode[0]==1?rounded(RED,dp(11)):glassFill(Color.argb(135,255,255,255)));clear.setBackground(mode[0]==2?rounded(BLUE,dp(11)):glassFill(Color.argb(135,255,255,255)));all.setTextColor(mode[0]==0?Color.WHITE:DARK);debt.setTextColor(mode[0]==1?Color.WHITE:DARK);clear.setTextColor(mode[0]==2?Color.WHITE:DARK);};
        filters.addView(all,new LinearLayout.LayoutParams(0,25,1));LinearLayout.LayoutParams f2=new LinearLayout.LayoutParams(0,25,1);f2.setMargins(dp(3),0,0,0);filters.addView(debt,f2);LinearLayout.LayoutParams f3=new LinearLayout.LayoutParams(0,25,1);f3.setMargins(dp(3),0,0,0);filters.addView(clear,f3);
        content.addView(filters,new LinearLayout.LayoutParams(-1,dp(27)));

        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list,new LinearLayout.LayoutParams(-1,-2));
        Runnable refresh=()->{
            list.removeAllViews();Cursor c=db.customers(search.getText().toString().trim());int shown=0;
            while(c.moveToNext()){
                long id=c.getLong(0);String n=c.getString(1)==null?"":c.getString(1).trim();double bal=db.balance(id);
                if(mode[0]==1&&bal<=0.005)continue;if(mode[0]==2&&bal>0.005)continue;shown++;
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setPadding(dp(3),dp(1),dp(3),dp(1));
                row.setBackground(glassFill(Color.argb(160,255,255,255)));
                TextView av=denseText("ب",10,8f,Color.WHITE);av.setGravity(Gravity.CENTER);av.setBackground(rounded(Color.rgb(19,194,111),dp(12)));row.addView(av,new LinearLayout.LayoutParams(dp(24),dp(24)));
                LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(4),0,dp(3),0);
                TextView nm=denseText(n,9.5f,8f,DARK);nm.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                TextView sub=denseText(db.transactionCount(id)+" حركة",7.7f,7f,MUTED);
                info.addView(nm,new LinearLayout.LayoutParams(-1,dp(17)));info.addView(sub,new LinearLayout.LayoutParams(-1,dp(13)));
                row.addView(info,new LinearLayout.LayoutParams(0,dp(46),1));
                TextView balv=denseText(balanceText(bal),8.7f,7.5f,balanceColor(bal));balv.setGravity(Gravity.CENTER);balv.setBackground(glassFill(bal>0.005?Color.argb(205,255,110,120):bal<-0.005?Color.argb(175,205,232,255):Color.argb(165,220,250,230)));
                row.addView(balv,new LinearLayout.LayoutParams(dp(116),dp(25)));
                Button opt=button("⋮");opt.setTextSize(12);opt.setPadding(0,0,0,0);opt.setBackgroundColor(Color.TRANSPARENT);opt.setTextColor(Color.WHITE);opt.setOnClickListener(v->customerActions(id,n));row.addView(opt,new LinearLayout.LayoutParams(dp(24),dp(28)));
                row.setOnClickListener(v->account(id,n));row.setOnLongClickListener(v->{customerActions(id,n);return true;});
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(43));rp.setMargins(0,0,0,dp(2));list.addView(row,rp);
            }
            c.close();if(shown==0){TextView e=denseText("لا يوجد عملاء مطابقون للبحث",9,8f,Color.WHITE);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));}
        };
        all.setOnClickListener(v->{mode[0]=0;fs.run();refresh.run();});debt.setOnClickListener(v->{mode[0]=1;fs.run();refresh.run();});clear.setOnClickListener(v->{mode[0]=2;fs.run();refresh.run();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});
        fs.run();refresh.run();
    }

LinearLayout glassStat(String label,String value,int accent){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4),dp(4),dp(4),dp(4));
        box.setBackground(outlined(Color.argb(225,255,255,255),dp(1),dp(21)));
        box.setElevation(dp(2));
        TextView l=tv(label,11); l.setTextColor(MUTED); l.setGravity(Gravity.CENTER);
        TextView v=tv(value,13); v.setTextColor(accent); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER);
        v.setMaxLines(1); fitInside(v,13,10);
        box.addView(l,new LinearLayout.LayoutParams(-1,dp(20)));
        box.addView(v,new LinearLayout.LayoutParams(-1,dp(29)));
        return box;
    }

    void customerActions(long id,String name){
        String[] choices={"✏ تعديل بيانات العميل","📄 كشف الحساب PDF + واتساب","📞 اتصال بالعميل","🗑 حذف حساب العميل"};
        new AlertDialog.Builder(this).setTitle("حساب: "+name).setItems(choices,(d,w)->{
            if(w==0)editCustomer(id,name);
            else if(w==1)shareAccountPdfToWhatsApp(id,name);
            else if(w==2){
                String p=db.phoneByName(name).replaceAll("[^0-9+]","");
                if(p.isEmpty()){Toast.makeText(this,"لا يوجد رقم هاتف للعميل",Toast.LENGTH_SHORT).show();return;}
                try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+p)));}catch(Exception ignored){}
            }else new AlertDialog.Builder(this).setTitle("حذف حساب العميل؟").setMessage("سيتم حذف الحساب وجميع عملياته وفواتيره المرتبطة به.").setPositiveButton("حذف",(x,y)->{db.deleteCustomer(id);customers();}).setNegativeButton("إلغاء",null).show();
        }).setNegativeButton("إغلاق",null).show();
    }


    void editCustomer(long id,String oldName){
        EditText name=field("اسم العميل"); name.setText(oldName);
        EditText phone=phoneField("رقم الهاتف"); phone.setText(db.phoneByName(oldName));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8),dp(4),dp(8),dp(4));
        box.addView(name,new LinearLayout.LayoutParams(-1,dp(40))); spaceInside(box,5); box.addView(phone,new LinearLayout.LayoutParams(-1,dp(40)));
        new AlertDialog.Builder(this).setTitle("تعديل بيانات العميل").setView(box)
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("حفظ",(d,w)->{
                String n=name.getText().toString().trim(), p=phone.getText().toString().trim();
                if(n.isEmpty()){Toast.makeText(this,"اسم العميل مطلوب",Toast.LENGTH_SHORT).show();return;}
                db.updateCustomer(id,oldName,n,p); customers();
                Toast.makeText(this,"تم تعديل بيانات العميل",Toast.LENGTH_SHORT).show();
            }).show();
    }

void shareReceiptImageAndText(String no,String customer,ArrayList<Line> lines,double total){
        double paid=0;
        long iid=db.invoiceIdByNo(no);
        if(iid>0) paid=db.invoicePaid(iid);
        shareReceiptImageAndText(no,customer,lines,total,paid);
    }

}