            t.setIncludeFontPadding(true); t.setHorizontallyScrolling(false); t.setEllipsize(null);
            if(!(t instanceof EditText)){ t.setSingleLine(false); t.setMaxLines(6); t.setMinLines(1); }
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
            t.setMaxLines(8);
            t.setMinLines(1);
        }
        View p=t;
        for(int level=0;level<5 && p.getParent() instanceof ViewGroup;level++){
            ViewGroup parent=(ViewGroup)p.getParent();
            ViewGroup.LayoutParams lp=parent.getLayoutParams();
            // معظم البطاقات والصفوف القديمة كانت بارتفاع ثابت 14-74dp.
            // نحولها إلى WRAP_CONTENT حتى لا تخفي نصف الكلمة أو تتداخل مع
            // العنصر التالي. الحاويات الكبيرة/الرئيسية تبقى كما هي.
            if(lp!=null && lp.height>0 && lp.height<=dp(84)){
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
    }

    void fitInside(View v,float maxSp,float minSp){
        if(v instanceof TextView){
            TextView t=(TextView)v; t.setIncludeFontPadding(true); t.setHorizontallyScrolling(false);
            t.setEllipsize(null); t.setSingleLine(false); t.setMaxLines(6); t.setMinLines(1);
            if(android.os.Build.VERSION.SDK_INT>=23){try{t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);}catch(Throwable ignored){}}