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
    static final int GREEN=Color.rgb(23,107,91), DARK=Color.rgb(18,63,54), GOLD=Color.rgb(217,154,43), BLUE=Color.rgb(37,99,235), RED=Color.rgb(184,74,58);
    static final int BG=Color.rgb(247,244,236), TEXT=Color.rgb(23,33,31), MUTED=Color.rgb(82,92,88), CARD=Color.WHITE;
    static final int SURFACE_ALT=Color.rgb(242,239,231), BORDER=Color.rgb(218,213,201);
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
        TextView head=tv("بقالة العزي",18);
        head.setTextColor(Color.WHITE); head.setGravity(Gravity.CENTER); head.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        head.setBackground(rounded(DARK,dp(14)));
        box.addView(head,new LinearLayout.LayoutParams(-1,-2));
        addSafeHomeButton(box,"🧾 فواتير البيع",v->invoiceHistory());
        addSafeHomeButton(box,"👥 العملاء والحسابات",v->customers());
        addSafeHomeButton(box,"🛒 فواتير الشراء",v->purchaseInvoices());
        addSafeHomeButton(box,"📦 المخزون والأصناف",v->inventory());
        addSafeHomeButton(box,"📊 التقارير",v->reports());
        addSafeHomeButton(box,"📝 الملاحظات",v->notes());
        addSafeHomeButton(box,"💸 الحوالات",v->transfers());
        TextView status=tv("تم تشغيل وضع الواجهة الآمن.\nسبب الخطأ: "+(error==null?"غير معروف":error.getClass().getSimpleName()),12);
        status.setTextColor(MUTED); status.setGravity(Gravity.CENTER);
        box.addView(status,new LinearLayout.LayoutParams(-1,-2));
        setContentView(box);
    }
    void addSafeHomeButton(LinearLayout box,String label,View.OnClickListener click){
        Button b=button(label); b.setTextSize(16); b.setTextColor(TEXT);
        b.setBackground(outlined(CARD,dp(1),12)); b.setOnClickListener(click);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,0); b.setMinHeight(dp(44)); box.addView(b,p);
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
        box.addView(title,new LinearLayout.LayoutParams(-1,-2));

        TextView msg=new TextView(this);
        msg.setText("تعذر تشغيل الشاشة الرئيسية.\\nتم إيقاف الخطأ لمنع ظهور شاشة بيضاء.\\nاضغط «إعادة المحاولة».");
        msg.setTextSize(15);
        msg.setTextColor(MUTED);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg,new LinearLayout.LayoutParams(-1,-2));

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
        box.addView(retry,new LinearLayout.LayoutParams(-1,-2));

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
        else if(prev.equals("الموردون")) suppliers();
        else if(prev.equals("حساب المورد")) suppliers();
        else if(prev.equals("الإعدادات")) settingsHub();
        else home();
    }

    GradientDrawable rounded(int color,float radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g; }
    GradientDrawable outlined(int color,int stroke,float radius){ GradientDrawable g=rounded(color,radius); g.setStroke(stroke,Color.rgb(174,185,198)); return g; }
    float fitText(float z){return Math.max(8f, z);}
    void normalizeAppText(View v){
        if(v instanceof TextView){
            TextView t=(TextView)v;
            String x=t.getText()==null?"":t.getText().toString().trim();
            if(!x.matches("[\\p{So}\\p{Cs}\\uFE0F\\u200D ]+")) {
                if(t.getTextSize()<spToPx(16f)) t.setTextSize(16f);
            }
            t.setIncludeFontPadding(true); t.setHorizontallyScrolling(false); t.setEllipsize(null);
            if(!(t instanceof EditText)){ t.setSingleLine(false); t.setMaxLines(Integer.MAX_VALUE); t.setMinLines(1); }
            if(Build.VERSION.SDK_INT>=23){try{t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);}catch(Throwable ignored){}}
            if(Build.VERSION.SDK_INT>=28){try{t.setFallbackLineSpacing(true);}catch(Throwable ignored){} try{t.setElegantTextHeight(true);}catch(Throwable ignored){}}
            t.postDelayed(()->expandForText(t),25);
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v; g.setClipChildren(false); g.setClipToPadding(false);
            for(int i=0;i<g.getChildCount();i++) normalizeAppText(g.getChildAt(i));
        }
    }
    void expandForText(TextView t){
        if(t==null || t.getWidth()<=0) return;
        // لا نعتمد على lineCount فقط؛ النص العربي قد يكون مقصوصاً داخل حاوية
        // قصيرة قبل أن يصل القياس إلى عدد الأسطر الصحيح.
        t.setIncludeFontPadding(true);
        t.setHorizontallyScrolling(false);
        t.setEllipsize(null);
        if(!(t instanceof EditText)){
            t.setSingleLine(false);
            t.setMaxLines(Integer.MAX_VALUE);
            t.setMinLines(1);
        }
        ViewGroup.LayoutParams own=t.getLayoutParams();
        // الأزرار لها ارتفاع تصميمي مقصود؛ لا نحوله إلى WRAP_CONTENT لأن ذلك يعيد
        // الارتفاع الافتراضي الكبير لزر Android ويملأ شاشة الفاتورة.
        if(!(t instanceof Button) && !(t instanceof EditText) && !(t instanceof AutoCompleteTextView)
                && own!=null && own.height>0 && own.height<=dp(140)){
            own.height=ViewGroup.LayoutParams.WRAP_CONTENT;
            t.setLayoutParams(own);
        }
        View p=t;
        for(int level=0;level<6 && p.getParent() instanceof ViewGroup;level++){
            ViewGroup parent=(ViewGroup)p.getParent();
            ViewGroup.LayoutParams lp=parent.getLayoutParams();
            // معظم البطاقات والصفوف القديمة كانت بارتفاع ثابت 14-84dp.
            // تحويلها إلى WRAP_CONTENT يمنع قص الكلمات والتداخل.
            if(lp!=null && lp.height>0 && lp.height<=dp(140)){
                lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;
                parent.setLayoutParams(lp);
            }
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
            p=parent;
        }
        t.requestLayout();
    }
    void finalizeAdaptiveLayout(View rootView){
        if(rootView==null) return;
        rootView.postDelayed(()->normalizeAppText(rootView),70);
        rootView.postDelayed(()->normalizeAppText(rootView),220);
        rootView.postDelayed(()->{
            normalizeAppText(rootView);
            rootView.requestLayout();
        },500);
        rootView.postDelayed(()->{
            normalizeAppText(rootView);
            rootView.requestLayout();
        },1000);
    }

    void fitInside(View v,float maxSp,float minSp){
        if(v instanceof TextView){
            TextView t=(TextView)v; t.setIncludeFontPadding(true); t.setHorizontallyScrolling(false);
            t.setEllipsize(null); t.setSingleLine(false); t.setMaxLines(Integer.MAX_VALUE); t.setMinLines(1);
            if(android.os.Build.VERSION.SDK_INT>=23){try{t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);}catch(Throwable ignored){}}
            t.setTextSize(16f);
            t.postDelayed(()->expandForText(t),35);
        }
    }

    void autoFitText(TextView t,float maxSp,float minSp,float stepSp){
        if(t==null)return;
        t.setSingleLine(false);
        t.setMaxLines(Integer.MAX_VALUE);
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
        TextView v=new TextView(this); v.setText(s); v.setTextSize(fitText(Math.max(15f,z*1.05f))); v.setTextColor(TEXT);
        v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); v.setPadding(dp(7),dp(5),dp(7),dp(5));
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); v.setTextDirection(View.TEXT_DIRECTION_RTL);
        if(Build.VERSION.SDK_INT>=28){try{v.setFallbackLineSpacing(true);}catch(Throwable ignored){} try{v.setElegantTextHeight(true);}catch(Throwable ignored){}}
        fitInside(v,fitText(z),8f); return v;
    }
    Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(10),dp(5),dp(10),dp(5)); b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null); b.setIncludeFontPadding(true); b.setMaxLines(Integer.MAX_VALUE);
        b.setEllipsize(null); b.setHorizontallyScrolling(false);
        if(Build.VERSION.SDK_INT>=28){try{b.setFallbackLineSpacing(true);}catch(Throwable ignored){} try{b.setElegantTextHeight(true);}catch(Throwable ignored){}}
        b.setTextColor(TEXT);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(CARD); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1),BORDER);
        b.setBackground(bg); b.setElevation(dp(1));
        b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); fitInside(b,17f,13f);
        return b;
    }
    EditText field(String h){
        EditText e=new EditText(this);
        e.setHint(h); e.setTextSize(16); e.setSingleLine(true); e.setIncludeFontPadding(false); e.setMaxLines(1);
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
            Button b=button(value);b.setTextSize(16);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setMaxLines(Integer.MAX_VALUE);b.setEllipsize(null);b.setSingleLine(false);
            b.setOnClickListener(v->{anchor.setText(value);anchor.setSelection(anchor.length());dismissLearningSuggestions();});
            box.addView(b,new LinearLayout.LayoutParams(-1,-2));n++;
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
        TextView v=tv("  "+s,13.5f); v.setTextColor(DARK); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setSingleLine(true); v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12),0,dp(12),0);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.rgb(235,242,249)); bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1),Color.rgb(211,223,236)); v.setBackground(bg);
        fitInside(v,14f,11f);
        content.addView(v,new LinearLayout.LayoutParams(-1,dp(38)));
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
        bar.setPadding(dp(7),dp(4),dp(7),dp(4));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{GREEN,DARK}));
        
        Button back=button("‹");
        back.setTextColor(Color.WHITE); back.setTextSize(28);
        back.setBackgroundColor(Color.TRANSPARENT); back.setElevation(0);
        back.setOnClickListener(v->goBack());
        bar.addView(back,new LinearLayout.LayoutParams(dp(40),dp(42)));

        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.setPadding(dp(8),0,dp(8),0);
        TextView logo=tv("بقالة العزي",15.5f);
        logo.setTextColor(Color.WHITE); logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView pt=tv(title,16.5f);
        pt.setTextColor(Color.WHITE); pt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        pt.setSingleLine(true); pt.setMaxLines(1); pt.setEllipsize(null); fitInside(pt,17f,13f);
        titleBox.addView(logo,new LinearLayout.LayoutParams(-1,-2));
        titleBox.addView(pt,new LinearLayout.LayoutParams(-1,-2));
        bar.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));

        TextView badge=tv("إدارة",11);
        badge.setTextColor(Color.WHITE); badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg=new GradientDrawable();
        badgeBg.setColor(Color.argb(55,255,255,255)); badgeBg.setCornerRadius(dp(18));
        badge.setBackground(badgeBg);
        bar.addView(badge,new LinearLayout.LayoutParams(dp(54),dp(32)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true); sv.setClipToPadding(false);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6),dp(4),dp(6),dp(8));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if(!"الرئيسية".equals(title) && !title.contains("فاتورة جديدة") && !title.contains("تعديل الفاتورة")){
            LinearLayout pageHero=new LinearLayout(this);
            pageHero.setOrientation(LinearLayout.VERTICAL);
            pageHero.setPadding(dp(8),dp(3),dp(8),dp(3));
            GradientDrawable heroBg=new GradientDrawable();
            heroBg.setColor(Color.WHITE); heroBg.setCornerRadius(dp(14));
            heroBg.setStroke(dp(1),Color.rgb(221,229,238));
            pageHero.setBackground(heroBg); pageHero.setElevation(dp(1));
            TextView heroTitle=tv(title,14);
            heroTitle.setTextColor(GREEN); heroTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            TextView heroSub=tv("إدارة سريعة ومنظمة",10.5f);
            heroSub.setTextColor(MUTED);
            pageHero.addView(heroTitle,new LinearLayout.LayoutParams(-1,dp(22)));
            pageHero.addView(heroSub,new LinearLayout.LayoutParams(-1,dp(18)));
            content.addView(pageHero,new LinearLayout.LayoutParams(-1,-2));
            addSpace(5);
        }

        sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        // إعادة قياس متدرجة بعد اكتمال كل البطاقات والصفوف تمنع قص النص والتداخل.
        finalizeAdaptiveLayout(root);
        if(withDefaultNav) attachDefaultBottomNav(title);
    }

    void attachDefaultBottomNav(String activeTitle){
        if(bottom==null)return;
        bottom.removeAllViews();
        LinearLayout nav=new LinearLayout(this); nav.setTag("fixedNavigation"); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL); nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); nav.setPadding(dp(4),dp(3),dp(4),dp(3));
        nav.setBackground(outlined(CARD,dp(1),dp(14))); nav.setElevation(dp(7));
        String[] labels={"الرئيسية","الفواتير","الحسابات","المخزون","المزيد"};
        String[] icons={"⌂","▤","●","□","⋮"};
        for(int i=0;i<labels.length;i++){
            final int idx=i;
            LinearLayout tab=new LinearLayout(this); tab.setOrientation(LinearLayout.VERTICAL); tab.setGravity(Gravity.CENTER); tab.setPadding(0,dp(2),0,dp(2));
            boolean active=(i==0&&"الرئيسية".equals(activeTitle))||(i==1&&activeTitle!=null&&activeTitle.contains("فاتورة"))||(i==2&&activeTitle!=null&&activeTitle.contains("حساب"))||(i==3&&activeTitle!=null&&activeTitle.contains("مخزون"));
            if(active) tab.setBackground(rounded(Color.rgb(231,242,238),dp(10)));
            TextView ic=tv(icons[i],19); ic.setGravity(Gravity.CENTER); ic.setTextColor(active?GREEN:TEXT); tab.addView(ic,new LinearLayout.LayoutParams(-1,dp(25)));
            TextView lab=tv(labels[i],12.5f); lab.setGravity(Gravity.CENTER); lab.setTextColor(active?GREEN:MUTED); if(active)lab.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            tab.addView(lab,new LinearLayout.LayoutParams(-1,dp(23)));
            tab.setOnClickListener(v->{hideKeyboard(); if(idx==0)home(); else if(idx==1)invoicesHub(); else if(idx==2)customers(); else if(idx==3)inventory(); else showMoreMenu();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1); lp.setMargins(dp(2),0,dp(2),0); nav.addView(tab,lp);
        }
        bottom.addView(nav,new LinearLayout.LayoutParams(-1,dp(60)));
}
void showMoreMenu(){
        String[] choices={"🏪 الموردون","📊 التقارير","💸 الحوالات","📝 الملاحظات","⚙️ الإعدادات","💾 النسخ الاحتياطي والاستعادة","🛒 المشتريات"};
        new AlertDialog.Builder(this).setTitle("المزيد").setItems(choices,(d,w)->{
            if(w==0)suppliers(); else if(w==1)reports(); else if(w==2)transfers(); else if(w==3)notes(); else if(w==4)settingsHub(); else if(w==5)settingsHub(); else purchaseInvoices();
        }).setNegativeButton("إغلاق",null).show();
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
                    if(customerPhoneInput!=null)customerPhoneInput.setText(p==null?"":p);
                    if(customerNameInput!=null)customerNameInput.requestFocus();
                    Toast.makeText(this,"تم استيراد رقم الهاتف",Toast.LENGTH_SHORT).show();
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
        valTv.setSingleLine(false);
        valTv.setMaxLines(Integer.MAX_VALUE);
        valTv.setEllipsize(null);
        fitInside(valTv, 12f, 8.5f);
        card.addView(valTv, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView lblTv = new TextView(this);
        lblTv.setText(label);
        lblTv.setTextSize(9.5f);
        lblTv.setTextColor(MUTED);
        lblTv.setGravity(Gravity.CENTER);
        lblTv.setSingleLine(false);
        lblTv.setMaxLines(Integer.MAX_VALUE);
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

        card.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

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
        card.addView(subTv, new LinearLayout.LayoutParams(-1, -2));

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
        base("الرئيسية",true);

        TextView welcome=tv("لوحة التحكم",20);
        welcome.setTextColor(GREEN); welcome.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        welcome.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        content.addView(welcome,new LinearLayout.LayoutParams(-1,dp(42)));

        TextView sub=tv("بقالة العزي للمواد الغذائية — إدارة المبيعات والحسابات والمخزون",14);
        sub.setTextColor(MUTED); sub.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        content.addView(sub,new LinearLayout.LayoutParams(-1,dp(34)));
        addSpace(4);

        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(2); grid.setUseDefaultMargins(false);
        grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        String[] labels={"🧾 الفواتير","👥 العملاء والحسابات","🏪 الموردون","🛒 المشتريات","📦 المخزون والأصناف","📊 التقارير","💸 الحوالات","📝 الملاحظات","⚙️ الإعدادات"};
        String[] desc={"مبيعات وشراء وفواتير محفوظة","الأرصدة والحركات وكشوف الحساب","حسابات الموردين وسدادهم","فواتير الشراء وتحديث المخزون","الأصناف والكميات والأسعار","ملخص الحركات والتقارير","إنشاء ومراجعة الحوالات","دفتر الملاحظات الذكي","النسخ الاحتياطي والإعدادات"};
        View.OnClickListener[] actions={v->invoicesHub(),v->customers(),v->suppliers(),v->purchaseInvoices(),v->inventory(),v->reports(),v->transfers(),v->notes(),v->settingsHub()};
        for(int i=0;i<labels.length;i++){
            LinearLayout cardBox=new LinearLayout(this);
            cardBox.setOrientation(LinearLayout.VERTICAL); cardBox.setGravity(Gravity.CENTER_VERTICAL);
            cardBox.setPadding(dp(12),dp(9),dp(12),dp(9)); cardBox.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            GradientDrawable bg=new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),BORDER);
            cardBox.setBackground(bg); cardBox.setElevation(dp(2)); cardBox.setOnClickListener(actions[i]);
            TextView t=tv(labels[i],17); t.setTextColor(TEXT); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            TextView d=tv(desc[i],13); d.setTextColor(MUTED); d.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); d.setMaxLines(2);
            cardBox.addView(t,new LinearLayout.LayoutParams(-1,dp(31)));
            cardBox.addView(d,new LinearLayout.LayoutParams(-1,dp(42)));
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams(GridLayout.spec(i/2,1),GridLayout.spec(i%2,1,1f));
            gp.width=0; gp.height=dp(92); gp.setMargins(dp(4),dp(4),dp(4),dp(4));
            grid.addView(cardBox,gp);
        }
        content.addView(grid,new LinearLayout.LayoutParams(-1,-2));

        TextView hint=tv("استخدم ＋ إضافة للعمليات السريعة من أي وقت في الرئيسية.",13);
        hint.setTextColor(GREEN); hint.setGravity(Gravity.CENTER);
        content.addView(hint,new LinearLayout.LayoutParams(-1,dp(40)));

        addHomeFab();
}
void addHomeFab(){
    if(root==null||root.getChildCount()<3) return;
    View sv=root.getChildAt(1);
    root.removeView(sv);
    FrameLayout frame=new FrameLayout(this);
    frame.setClipChildren(false); frame.setClipToPadding(false);
    frame.addView(sv,new FrameLayout.LayoutParams(-1,-1));
    Button fab=new Button(this);
    fab.setText("＋\nإضافة"); fab.setTextSize(13); fab.setTextColor(Color.WHITE);
    fab.setAllCaps(false); fab.setGravity(Gravity.CENTER); fab.setIncludeFontPadding(true);
    GradientDrawable fb=new GradientDrawable(); fb.setShape(GradientDrawable.OVAL); fb.setColor(GREEN);
    fab.setBackground(fb); fab.setElevation(dp(9)); fab.setContentDescription("إضافة عملية جديدة");
    fab.setOnClickListener(v->showGeneralActions());
    FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(68),dp(68),Gravity.BOTTOM|Gravity.LEFT);
    fp.setMargins(dp(16),0,dp(16),dp(16)); frame.addView(fab,fp);
    root.addView(frame,1,new LinearLayout.LayoutParams(-1,0,1));
    if(content!=null) content.setPadding(dp(6),dp(4),dp(6),dp(88));
    finalizeAdaptiveLayout(root);
}

        void showCustomerTransactionDialog(boolean payment){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);box.setPadding(dp(5),0,dp(5),0);
        AutoCompleteTextView name=new AutoCompleteTextView(this);name.setHint("اسم العميل");name.setTextSize(16);name.setSingleLine(true);name.setThreshold(1);name.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,db.customerNames()));name.setBackground(outlined(CARD,1,12));name.setPadding(dp(10),0,dp(10),0);name.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText amount=numberField(payment?"مبلغ السداد":"مبلغ الحركة");EditText detail=field("البيان / التفاصيل");
        box.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));spaceTo(box,6);box.addView(amount,new LinearLayout.LayoutParams(-1,dp(54)));spaceTo(box,6);box.addView(detail,new LinearLayout.LayoutParams(-1,dp(54)));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(payment?"سداد عميل":"حركة على حساب عميل").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim();double a=parseDoubleSafe(amount.getText().toString().replace(",","").trim(),0);if(n.isEmpty()||a<=0){Toast.makeText(this,"اختر العميل وأدخل مبلغاً صحيحاً",Toast.LENGTH_SHORT).show();return;}
            long id=db.customer(n);if(id<=0){Toast.makeText(this,"تعذر فتح حساب العميل",Toast.LENGTH_SHORT).show();return;}
            db.addTransaction(id,a,detail.getText().toString().trim(),payment?0:1,db.now());dlg.dismiss();account(id,n);Toast.makeText(this,payment?"✓ تم تسجيل السداد":"✓ تم تسجيل الحركة",Toast.LENGTH_SHORT).show();
        }));dlg.show();
    }
void showGeneralActions(){
        String[] choices={"🧾 فاتورة مبيعات","🛒 فاتورة شراء","💰 حركة على حساب عميل","💵 سداد عميل","🏪 حركة على حساب مورد","💸 حوالة","👤 إضافة عميل","🏪 إضافة مورد","📦 إضافة صنف"};
        new AlertDialog.Builder(this).setTitle("إضافة عملية جديدة").setItems(choices,(dlg,w)->{
            if(w==0) invoice(); else if(w==1) newPurchaseInvoice(); else if(w==2) showCustomerTransactionDialog(false); else if(w==3) showCustomerTransactionDialog(true);
            else if(w==4) suppliers(); else if(w==5) transfers(); else if(w==6) showCustomerCreatePopup(); else if(w==7) suppliers(); else inventory();
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
        invFooter.setPadding(dp(6),dp(2),dp(6),dp(3));
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
        spaceTo(invFooter,2);

        LinearLayout fButtons=new LinearLayout(this);
        fButtons.setOrientation(LinearLayout.HORIZONTAL);
        fButtons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        fButtons.setGravity(Gravity.CENTER_VERTICAL);

        Button fSave=action(edit?"💾 حفظ التعديل":"💾 حفظ الفاتورة",GREEN);
        fSave.setTextSize(13.5f);
        fButtons.addView(fSave,new LinearLayout.LayoutParams(0,dp(32),1.5f));

        Button fPrint=button("🖨️ طباعة ومعاينة");
        fPrint.setTextColor(GREEN); fPrint.setBackground(outline(Color.rgb(240,248,242),10));
        fPrint.setTextSize(12f);
        LinearLayout.LayoutParams fpp=new LinearLayout.LayoutParams(0,dp(32),1.1f); fpp.setMargins(dp(5),0,0,0);
        fButtons.addView(fPrint,fpp);

        Button fClear=button("🧹 مسح");
        fClear.setTextColor(MUTED); fClear.setBackground(outline(CARD,10));
        fClear.setTextSize(11.5f);
        LinearLayout.LayoutParams fcp=new LinearLayout.LayoutParams(0,dp(32),0.7f); fcp.setMargins(dp(5),0,0,0);
        fButtons.addView(fClear,fcp);

        invFooter.addView(fButtons,new LinearLayout.LayoutParams(-1,dp(34)));

        // أزرار طريقة البيع/السداد في الشريط السفلي الثابت
        LinearLayout payModes=new LinearLayout(this);
        payModes.setOrientation(LinearLayout.HORIZONTAL);
        payModes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        payModes.setGravity(Gravity.CENTER_VERTICAL);

        Button cashMode=button("💵 نقدي");
        Button creditMode=button("⏳ آجل");
        Button calcMode=button("🧮 حاسبة الصرف");
        cashMode.setTextSize(11.5f); creditMode.setTextSize(11.5f); calcMode.setTextSize(11.5f);
        payModes.addView(cashMode,new LinearLayout.LayoutParams(0,dp(30),1));
        LinearLayout.LayoutParams cmlpFooter=new LinearLayout.LayoutParams(0,dp(30),1); cmlpFooter.setMargins(dp(4),0,0,0);
        payModes.addView(creditMode,cmlpFooter);
        LinearLayout.LayoutParams clmlpFooter=new LinearLayout.LayoutParams(0,dp(30),1.1f); clmlpFooter.setMargins(dp(4),0,0,0);
        payModes.addView(calcMode,clmlpFooter);
        invFooter.addView(payModes,new LinearLayout.LayoutParams(-1,dp(32)));

        bottom.addView(invFooter,new LinearLayout.LayoutParams(-1,-2));

        // صف بيانات الفاتورة: رقم الفاتورة + اسم العميل + التاريخ والوقت
        LinearLayout metaCard=new LinearLayout(this);
        metaCard.setOrientation(LinearLayout.VERTICAL);
        metaCard.setPadding(dp(4),dp(3),dp(4),dp(3));
        metaCard.setBackground(outlined(CARD,1,14));
        metaCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout metaRow=new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView no=tv(displayInvoiceNo(edit?db.invoiceNo(invoiceId):String.valueOf(db.nextInvoice())),14);
        no.setTextColor(GREEN); no.setTypeface(Typeface.DEFAULT,Typeface.BOLD); no.setGravity(Gravity.CENTER);
        GradientDrawable noBg=new GradientDrawable();
        noBg.setColor(Color.rgb(240,248,242));
        noBg.setCornerRadius(dp(10));
        noBg.setStroke(dp(1),Color.rgb(190,225,200));
        no.setBackground(noBg);
        no.setContentDescription("رقم الفاتورة");
        metaRow.addView(no,new LinearLayout.LayoutParams(0,dp(36),0.75f));

        AutoCompleteTextView customer=new AutoCompleteTextView(this);
        customer.setHint("اسم العميل"); customer.setTextSize(16f); customer.setSingleLine(true);
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
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(36),1.35f); clp.setMargins(dp(5),0,dp(5),0);
        metaRow.addView(customer,clp);

        TextView dt=tv(db.now(),11); dt.setTextColor(MUTED); dt.setGravity(Gravity.CENTER);
        GradientDrawable dtBg=new GradientDrawable();
        dtBg.setColor(Color.rgb(248,250,248));
        dtBg.setCornerRadius(dp(10));
        dtBg.setStroke(dp(1),Color.rgb(228,235,230));
        dt.setBackground(dtBg);
        metaRow.addView(dt,new LinearLayout.LayoutParams(0,dp(36),1.1f));

        metaCard.addView(metaRow,new LinearLayout.LayoutParams(-1,-2));

        if(edit){
            customer.setText(db.invoiceCustomer(invoiceId));
        }

        // قسم إدخال الصنف: الحفاظ التام على ترتيب مربعات الإدخال (الإجمالي، الكمية، اسم الصنف)
        LinearLayout entry=card();
        entry.setPadding(dp(4),dp(2),dp(4),dp(2));

        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        EditText total=numberField("الإجمالي");
        EditText qty=numberField("الكمية");
        AutoCompleteTextView item=new AutoCompleteTextView(this);
        item.setHint("اسم الصنف / التفاصيل"); item.setTextSize(16); item.setSingleLine(true); item.setTextColor(TEXT); item.setHintTextColor(MUTED);
        item.setPadding(dp(8),dp(4),dp(8),dp(4)); item.setBackground(outlined(CARD,1,10)); item.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); item.setTextDirection(View.TEXT_DIRECTION_RTL); item.setSelectAllOnFocus(true);
        item.setOnClickListener(v->item.selectAll());
        item.setOnFocusChangeListener((v,has)->{if(has)item.postDelayed(()->item.selectAll(),60);});
        ArrayList<String> itemSuggestions=new ArrayList<>(Arrays.asList("السمن"));
        Cursor itemCursor=db.items(); while(itemCursor.moveToNext()) itemSuggestions.add(itemCursor.getString(1)); itemCursor.close();
        item.setThreshold(1); item.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,itemSuggestions));

        total.setInputType(2|8192); qty.setInputType(2|8192); qty.setText("1"); total.setSingleLine(true); qty.setSingleLine(true); item.setSingleLine(true);

        item.setOnItemClickListener((parent,view,pos,id)->{
            String selectedName=(String)parent.getItemAtPosition(pos);
            double saleP=db.itemSalePrice(selectedName);
            if(saleP>0){
                double q=1;
                try{q=Double.parseDouble(qty.getText().toString().trim());}catch(Exception ignored){}
                if(q<=0)q=1;
                total.setText(fmt(saleP*q));
            }
        });

        // الترتيب: الإجمالي -> الكمية -> اسم الصنف
        line.addView(total,new LinearLayout.LayoutParams(0,dp(36),1.0f));
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(0,dp(36),0.72f); qlp.setMargins(dp(4),0,dp(4),0);
        line.addView(qty,qlp);
        line.addView(item,new LinearLayout.LayoutParams(0,dp(36),1.35f));
        entry.addView(line,new LinearLayout.LayoutParams(-1,-2));
        spaceTo(entry,4);

        Button add=action("＋ إضافة الصنف",GREEN);
        add.setTextSize(12.5f);
        entry.addView(add,new LinearLayout.LayoutParams(-1,dp(34)));
        content.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        space(6);

        // صندوق عرض الفاتورة (الحفاظ على الهيكل وتنسيق العرض)
        section("صندوق عرض الفاتورة");
        LinearLayout invoiceBox=card();
        invoiceBox.setPadding(dp(4),dp(2),dp(4),dp(2));

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
            head.addView(hv,new LinearLayout.LayoutParams(0,dp(24),weights[i]));
        }
        table.addView(head,new LinearLayout.LayoutParams(-1,-2));
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
        boxTotal.setPadding(dp(6),dp(2),dp(6),dp(2));
        GradientDrawable btBg=new GradientDrawable();
        btBg.setColor(Color.rgb(255,249,230));
        btBg.setCornerRadius(dp(12));
        btBg.setStroke(dp(1),Color.rgb(245,225,175));
        boxTotal.setBackground(btBg);
        invoiceBox.addView(boxTotal,new LinearLayout.LayoutParams(-1,-2));
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
        paidRow.addView(paidTitle,new LinearLayout.LayoutParams(0,dp(32),1));
        paidRow.addView(paid,new LinearLayout.LayoutParams(dp(112),dp(32)));
        invoiceBox.addView(paidRow,new LinearLayout.LayoutParams(-1,dp(32)));
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
        invoiceBox.addView(remainingLabel,new LinearLayout.LayoutParams(-1,dp(22)));

        content.addView(invoiceBox,new LinearLayout.LayoutParams(-1,-2));
        space(5);
        final ArrayList<Line> lines=new ArrayList<>();
        if(edit){Cursor c=db.invoiceLines(invoiceId);while(c.moveToNext())lines.add(new Line(c.getString(1),c.getDouble(2),c.getDouble(3)));c.close();}

        LinearLayout infoStrip=new LinearLayout(this);
        infoStrip.setOrientation(LinearLayout.HORIZONTAL);
        infoStrip.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        infoStrip.setGravity(Gravity.CENTER_VERTICAL);
        TextView customerBalance=tv("رصيد العميل: 0 ريال",10.5f);
        customerBalance.setTextColor(GREEN); customerBalance.setGravity(Gravity.CENTER);
        customerBalance.setPadding(dp(5),dp(2),dp(5),dp(2));
        customerBalance.setBackground(outline(Color.rgb(241,247,242),8));
        infoStrip.addView(customerBalance,new LinearLayout.LayoutParams(0,dp(28),1.05f));

        TextView paymentMode=tv("نوع السداد: نقدي",9.5f);
        paymentMode.setTextColor(MUTED); paymentMode.setGravity(Gravity.CENTER);
        paymentMode.setPadding(dp(5),dp(2),dp(5),dp(2));
        LinearLayout.LayoutParams pmlp=new LinearLayout.LayoutParams(0,dp(28),1.35f);
        pmlp.setMargins(dp(4),0,0,0);
        infoStrip.addView(paymentMode,pmlp);
        content.addView(infoStrip,new LinearLayout.LayoutParams(-1,dp(30)));
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
        content.setPadding(dp(4),dp(2),dp(4),dp(10));

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
        File target=c.getDatabasePath(DB.DB_NAME);File tmp=new File(c.getCacheDir(),"restore_enezi.db");
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
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(9),dp(12),dp(9));c.setBackground(outline(CARD,14));c.setElevation(dp(2));c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return c;}
    void addCard(View v,int h){content.addView(v,new LinearLayout.LayoutParams(-1,dp(Math.max(50,h-18))));space(4);}
    void add(View v,int h){content.addView(v,new LinearLayout.LayoutParams(-1,dp(Math.max(42,h-12))));space(4);}
    void space(int h){addSpace(dp(h));}
    void spaceInside(LinearLayout p,int h){Space x=new Space(this);p.addView(x,new LinearLayout.LayoutParams(1,dp(h)));}
    Button action(String text,int color){Button b=button(text);b.setTextColor(Color.WHITE);b.setTextSize(15.5f);b.setBackground(rounded(color,dp(12)));b.setMinHeight(0);b.setMinimumHeight(0);return b;}
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
        total.setMinHeight(dp(32)); qty.setMinHeight(dp(32)); item.setMinHeight(dp(32)); unit.setMinHeight(dp(32)); del.setMinHeight(dp(32));
        View[] cells={total,qty,item,unit,del};for(int i=0;i<cells.length;i++)r.addView(cells[i],new LinearLayout.LayoutParams(0,-2,w[i]));
        total.setOnClickListener(v->editLineTotal(l,parent,all,baseBal));
        qty.setOnClickListener(v->editLineQuantity(l,parent,all,baseBal));
        item.setOnClickListener(v->editLineName(l,parent,all,baseBal));
        del.setOnClickListener(v->{all.remove(l);redrawInvoiceRows(parent,all,baseBal);});
        parent.addView(r,new LinearLayout.LayoutParams(-1,-2));
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

    void showCompactSaveSnackbar(String message,String actionLabel,Runnable action){
        View anchor=content!=null?content:root;
        if(anchor==null) anchor=findViewById(android.R.id.content);
        try{
            com.google.android.material.snackbar.Snackbar bar=com.google.android.material.snackbar.Snackbar.make(anchor,message,com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
            bar.setTextMaxLines(1);
            if(actionLabel!=null&&!actionLabel.isEmpty()){bar.setAction(actionLabel,v->{if(action!=null)action.run();});bar.setActionTextColor(Color.WHITE);}
            View sb=bar.getView();sb.setMinimumHeight(dp(42));sb.setPadding(dp(10),0,dp(8),0);
            TextView text=sb.findViewById(com.google.android.material.R.id.snackbar_text);
            if(text!=null){text.setTextSize(15);text.setMaxLines(1);text.setEllipsize(null);text.setIncludeFontPadding(true);}
            TextView av=sb.findViewById(com.google.android.material.R.id.snackbar_action);
            if(av!=null){av.setTextSize(14);av.setMaxLines(1);}
            bar.show();
        }catch(Throwable ignored){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    }

    void showPostSaveOperation(String title,String message,Runnable shareAction,Runnable hideAction){
        String t=title==null?"":title;
        String msg=t.contains("فاتورة شراء")?"✓ تم حفظ فاتورة الشراء":(t.contains("فاتورة")?"✓ تم حفظ الفاتورة":(t.contains("عملية")?"✓ تم حفظ العملية":"✓ تم الحفظ"));
        showCompactSaveSnackbar(msg,"مشاركة",shareAction);
    }


    void showPostSaveActions(String no,String customer,ArrayList<Line> lines,double total,long cid,double paid,String stockWarning){
        String msg=(stockWarning!=null&&!stockWarning.trim().isEmpty())?"✓ تم حفظ الفاتورة • تنبيه المخزون":"✓ تم حفظ الفاتورة";
        final String n=no,c=customer;final ArrayList<Line> ls=lines;final double t=total,p=paid;
        showCompactSaveSnackbar(msg,"مشاركة",()->{try{invoiceHistory();shareReceiptImageAndText(n,c,ls,t,p);}catch(Throwable e){Toast.makeText(this,"تعذر مشاركة الفاتورة",Toast.LENGTH_SHORT).show();}});
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
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),WindowManager.LayoutParams.WRAP_CONTENT);w.setGravity(Gravity.CENTER);}
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
        applyDenseGlassPage();
        TextView title=tv("💸 الحوالات المالية",20);title.setTextColor(GREEN);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));
        LinearLayout form=card();form.setPadding(dp(10),dp(8),dp(10),dp(10));
        TextView al=tv("المبلغ الصافي",17);al.setTextColor(GREEN);al.setTypeface(Typeface.DEFAULT,Typeface.BOLD);form.addView(al,new LinearLayout.LayoutParams(-1,dp(28)));
        EditText amount=numberField("19,000");amount.setTextSize(18);form.addView(amount,new LinearLayout.LayoutParams(-1,dp(52)));addSpaceTo(form,6);
        TextView rt=tv("المستلم",16);rt.setTextColor(TEXT);rt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);form.addView(rt,new LinearLayout.LayoutParams(-1,dp(25)));
        AutoCompleteTextView rn=new AutoCompleteTextView(this);rn.setHint("اسم المستلم");rn.setTextSize(16);rn.setSingleLine(true);rn.setThreshold(1);rn.setPadding(dp(10),0,dp(10),0);rn.setTextColor(TEXT);rn.setHintTextColor(MUTED);rn.setBackground(outlined(CARD,1,12));rn.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);rn.setTextDirection(View.TEXT_DIRECTION_RTL);
        rn.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,transferSuggestionNames(true)));
        transferReceiverName=rn;transferReceiverPhone=phoneField("رقم المستلم");
        LinearLayout receiverRow=new LinearLayout(this);receiverRow.setOrientation(LinearLayout.HORIZONTAL);receiverRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);receiverRow.setWeightSum(2);
        receiverRow.addView(rn,new LinearLayout.LayoutParams(0,dp(52),1));receiverRow.addView(transferReceiverPhone,new LinearLayout.LayoutParams(0,dp(52),1));form.addView(receiverRow);addSpaceTo(form,7);
        TextView st=tv("المرسل",16);st.setTextColor(TEXT);st.setTypeface(Typeface.DEFAULT,Typeface.BOLD);form.addView(st,new LinearLayout.LayoutParams(-1,dp(25)));
        AutoCompleteTextView sn=new AutoCompleteTextView(this);sn.setHint("اسم المرسل");sn.setTextSize(16);sn.setSingleLine(true);sn.setThreshold(1);sn.setPadding(dp(10),0,dp(10),0);sn.setTextColor(TEXT);sn.setHintTextColor(MUTED);sn.setBackground(outlined(CARD,1,12));sn.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);sn.setTextDirection(View.TEXT_DIRECTION_RTL);
        sn.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,transferSuggestionNames(false)));
        transferSenderName=sn;transferSenderPhone=phoneField("رقم المرسل");
        LinearLayout senderRow=new LinearLayout(this);senderRow.setOrientation(LinearLayout.HORIZONTAL);senderRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);senderRow.setWeightSum(2);
        senderRow.addView(sn,new LinearLayout.LayoutParams(0,dp(52),1));senderRow.addView(transferSenderPhone,new LinearLayout.LayoutParams(0,dp(52),1));form.addView(senderRow);addSpaceTo(form,8);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button prepare=button("✓ تجهيز الحوالة");Button clear=button("🧹 مسح");prepare.setTextColor(Color.WHITE);prepare.setBackground(rounded(GREEN,dp(12)));clear.setTextColor(TEXT);clear.setBackground(rounded(CARD,dp(12)));
        actions.addView(prepare,new LinearLayout.LayoutParams(0,dp(48),2));actions.addView(clear,new LinearLayout.LayoutParams(0,dp(48),1));form.addView(actions);content.addView(form,new LinearLayout.LayoutParams(-1,-2));
        Runnable[] filterReceiver=new Runnable[1],filterSender=new Runnable[1];
        filterReceiver[0]=()->rn.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,transferSuggestionNames(true,rn.getText().toString())));
        filterSender[0]=()->sn.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,transferSuggestionNames(false,sn.getText().toString())));
        rn.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filterReceiver[0].run();}public void afterTextChanged(android.text.Editable e){}});
        sn.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filterSender[0].run();}public void afterTextChanged(android.text.Editable e){}});
        rn.setOnItemClickListener((p,v,pos,id)->{String n=String.valueOf(p.getItemAtPosition(pos));rn.setText(n);String ph=transferPhoneForName(n,true);if(ph!=null)transferReceiverPhone.setText(ph);});
        sn.setOnItemClickListener((p,v,pos,id)->{String n=String.valueOf(p.getItemAtPosition(pos));sn.setText(n);String ph=transferPhoneForName(n,false);if(ph!=null)transferSenderPhone.setText(ph);});
        prepare.setOnClickListener(v->prepareTransfer(amount,rn,transferReceiverPhone,sn,transferSenderPhone));
        clear.setOnClickListener(v->{amount.setText("");rn.setText("");sn.setText("");transferReceiverPhone.setText("");transferSenderPhone.setText("");});
    }
    ArrayList<String> transferSuggestionNames(boolean receiver){return transferSuggestionNames(receiver,"");}
    ArrayList<String> transferSuggestionNames(boolean receiver,String query){
        ArrayList<String> out=new ArrayList<>();String q=query==null?:"";
        Cursor c=db.rawQuery("SELECT DISTINCT name FROM transfers WHERE "+(receiver?"receiver_name":"sender_name")+" LIKE ? ORDER BY id DESC LIMIT 30",new String[]{"%"+q+"%"});
        while(c.moveToNext())out.add(c.getString(0));c.close();return out;
    }
    String transferPhoneForName(String name,boolean receiver){
        Cursor c=db.rawQuery("SELECT "+(receiver?"receiver_phone":"sender_phone")+" FROM transfers WHERE "+(receiver?"receiver_name":"sender_name")+"=? ORDER BY id DESC LIMIT 1",new String[]{name});String p=c.moveToFirst()?c.getString(0):"";c.close();return p;
    }
    void prepareTransfer(EditText amount,AutoCompleteTextView rn,EditText rp,AutoCompleteTextView sn,EditText sp){
        double a=parseDoubleSafe(amount.getText().toString().replace(",",""),0);String r=rn.getText().toString().trim(),s=sn.getText().toString().trim(),rpv=rp.getText().toString().trim(),spv=sp.getText().toString().trim();
        if(a<=0||r.isEmpty()||rpv.isEmpty()||s.isEmpty()||spv.isEmpty()){Toast.makeText(this,"أكمل المبلغ والمستلم والمرسل وأرقامهما",Toast.LENGTH_SHORT).show();return;}
        String preview=fmt(a)+" صافي\\n\\nالمستلم: "+r+"\\nالرقم: "+rpv+"\\n\\nالمرسل: "+s+"\\nالرقم: "+spv;
        new AlertDialog.Builder(this).setTitle("معاينة الحوالة").setMessage(preview).setPositiveButton("نسخ",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("الحوالة",preview));Toast.makeText(this,"تم النسخ",Toast.LENGTH_SHORT).show();}).setNeutralButton("مشاركة واتساب",(d,w)->shareTransferWhatsApp(r, rpv, s, spv, a)).setNegativeButton("إغلاق",null).show();
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
    String displayInvoiceNo(String no){try{return String.format(Locale.US,"%06d",Integer.parseInt(no.replaceAll("[^0-9]","")));}catch(Exception e){return no==null?"":no;}}


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
            // هوية موحدة لكل الشاشات: لا نخفي رأس الصفحة ولا نبدل الخلفية بين التبويبات.
            root.setBackgroundColor(BG);
            if(content!=null){
                content.setBackgroundColor(BG);
                content.setPadding(dp(6),dp(4),dp(6),dp(8));
            }
            if(bottom!=null&&bottom.getChildCount()>0){
                View nav=bottom.getChildAt(0);
                nav.setBackground(outlined(CARD,dp(1),dp(10)));
            }
        }catch(Throwable ignored){}
    }
    TextView denseText(String s,float max,float min,int color){
        float safeMax=Math.max(12f,Math.min(15.5f,max));
        float safeMin=Math.max(10.5f,Math.min(safeMax-0.5f,min));
        TextView t=tv(s,safeMax);
        t.setTextColor(color); t.setTextSize(safeMax); t.setSingleLine(false);
        t.setMaxLines(4); t.setMinLines(1); t.setEllipsize(null);
        t.setHorizontallyScrolling(false); t.setIncludeFontPadding(true);
        if(Build.VERSION.SDK_INT>=23){try{t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);}catch(Throwable ignored){}}
        if(Build.VERSION.SDK_INT>=28){try{t.setFallbackLineSpacing(true);}catch(Throwable ignored){} try{t.setElegantTextHeight(true);}catch(Throwable ignored){}}
        t.postDelayed(()->expandForText(t),35);
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
        content.addView(profile,new LinearLayout.LayoutParams(-1,-2));

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
            if(count==0){TextView e=denseText("لا توجد عمليات مسجلة",10,8f,Color.WHITE);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,-2));}
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
        base("الحسابات والعملاء"); applyDenseGlassPage();
        LinearLayout searchBar=new LinearLayout(this); searchBar.setOrientation(LinearLayout.HORIZONTAL); searchBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); searchBar.setGravity(Gravity.CENTER_VERTICAL);
        AutoCompleteTextView search=new AutoCompleteTextView(this);
        search.setHint("🔎 ابحث عن عميل"); search.setTextSize(16); search.setSingleLine(true); search.setThreshold(1); search.setSelectAllOnFocus(true);
        search.setPadding(dp(12),0,dp(12),0); search.setTextColor(TEXT); search.setHintTextColor(MUTED); search.setBackground(outlined(CARD,dp(1),14));
        search.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); search.setTextDirection(View.TEXT_DIRECTION_RTL);
        search.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,db.customerNames()));
        Button add=action("＋ عميل",GREEN); add.setOnClickListener(v->showCustomerCreatePopup());
        searchBar.addView(search,new LinearLayout.LayoutParams(0,dp(54),1));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(92),dp(54));ap.setMargins(dp(5),0,0,0);searchBar.addView(add,ap);
        content.addView(searchBar); addSpace(6);
        LinearLayout stats=card();stats.setOrientation(LinearLayout.HORIZONTAL);stats.setPadding(dp(6),dp(5),dp(6),dp(5));
        TextView st1=tv("العملاء: "+db.customerCount(),13);st1.setTextColor(GREEN);st1.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView st2=tv("عليهم: "+fmt(db.totalDebts())+" ر.ي",13);st2.setTextColor(RED);st2.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        TextView st3=tv("لهم: "+fmt(db.totalCredits())+" ر.ي",13);st3.setTextColor(BLUE);st3.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        for(TextView t:new TextView[]{st1,st2,st3}){t.setGravity(Gravity.CENTER);stats.addView(t,new LinearLayout.LayoutParams(0,dp(38),1));}
        content.addView(stats); addSpace(6);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);content.addView(list);
        Runnable render=()->{
            list.removeAllViews(); String q=search.getText().toString().trim(); Cursor cur=db.customers(q); int count=0;
            while(cur.moveToNext()){
                long id=cur.getLong(0);String name=cur.getString(1);double bal=db.balance(id);count++;
                LinearLayout row=card();row.setPadding(dp(10),dp(7),dp(10),dp(7));row.setOnClickListener(v->account(id,name));row.setOnLongClickListener(v->{customerActions(id,name);return true;});
                LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView avatar=tv(name==null||name.isEmpty()?"ب":name.substring(0,1),16);avatar.setGravity(Gravity.CENTER);avatar.setTextColor(Color.WHITE);avatar.setTypeface(Typeface.DEFAULT,Typeface.BOLD);avatar.setBackground(rounded(GREEN,dp(18)));
                top.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));
                TextView nm=tv(name,16);nm.setTextColor(TEXT);nm.setTypeface(Typeface.DEFAULT,Typeface.BOLD);nm.setPadding(dp(9),0,dp(5),0);top.addView(nm,new LinearLayout.LayoutParams(0,dp(42),1));
                TextView bv=tv(balanceText(bal),15);bv.setTextColor(balanceColor(bal));bv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);bv.setGravity(Gravity.CENTER);bv.setBackground(outlined(CARD,dp(1),12));top.addView(bv,new LinearLayout.LayoutParams(dp(125),dp(36)));
                row.addView(top,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(6));list.addView(row,rp);
            } cur.close();
            if(count==0){TextView empty=tv("لا يوجد عميل مطابق. يمكنك إضافة عميل جديد.",14);empty.setTextColor(MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));}
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int d){}public void onTextChanged(CharSequence s,int a,int b,int d){render.run();}public void afterTextChanged(Editable e){}});
        search.setOnItemClickListener((p,v,pos,id)->{String n=(String)p.getItemAtPosition(pos);long cid=db.customerIdByName(n);if(cid>0)account(cid,n);});
        render.run();
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