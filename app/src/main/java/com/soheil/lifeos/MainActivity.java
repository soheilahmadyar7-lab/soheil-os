package com.soheil.lifeos;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class MainActivity extends FragmentActivity {
    private static final int BG=Color.rgb(7,11,16), PANEL=Color.rgb(16,23,31), PANEL2=Color.rgb(21,30,39);
    private static final int TEXT=Color.rgb(239,244,248), MUTED=Color.rgb(139,154,169), ACCENT=Color.rgb(83,229,194), WARN=Color.rgb(255,190,82), STROKE=Color.rgb(38,50,62);

    private LinearLayout root,content,navBar;
    private SoheilCrypto crypto;
    private SecurePrefs prefs;
    private SoheilDb db;
    private JarvisEngine jarvis;
    private boolean vaultReady=false, authenticating=false;
    private long backgroundAt=0L;
    private final Handler securityHandler=new Handler(Looper.getMainLooper());
    private final Runnable inactivityLock=()->{ if(vaultReady) lockNow("inactivity"); };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        SecurityCenter.hardenWindow(this);
        try { crypto=new SoheilCrypto(this); }
        catch(Exception e){ showSecurityRequirement("برای استفاده امن از SOHEIL باید روی گوشی Screen Lock فعال باشد."); return; }
        showLockedScreen();
        promptUnlock();
    }

    @Override protected void onResume(){
        super.onResume();
        if(vaultReady && backgroundAt>0 && System.currentTimeMillis()-backgroundAt>SecurityCenter.BACKGROUND_LOCK_MS && !authenticating){
            lockNow("background timeout");
        }
        backgroundAt=0L;
    }

    @Override protected void onStop(){
        super.onStop();
        if(vaultReady && !authenticating) backgroundAt=System.currentTimeMillis();
    }

    @Override protected void onDestroy(){
        securityHandler.removeCallbacksAndMessages(null);
        if(db!=null) db.closeSecurely();
        if(crypto!=null) crypto.lockVault();
        super.onDestroy();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev){
        if(vaultReady && ev.getAction()==MotionEvent.ACTION_DOWN) armInactivityLock();
        return super.dispatchTouchEvent(ev);
    }

    private void armInactivityLock(){
        securityHandler.removeCallbacks(inactivityLock);
        securityHandler.postDelayed(inactivityLock,SecurityCenter.INACTIVITY_LOCK_MS);
    }

    private void showSecurityRequirement(String message){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setGravity(Gravity.CENTER);r.setPadding(dp(28),dp(28),dp(28),dp(28));r.setBackgroundColor(BG);
        TextView mark=txt("SOHEIL SECURITY",14,ACCENT,true);mark.setGravity(Gravity.CENTER);r.addView(mark);
        TextView title=txt("Vault نمی‌تواند امن باز شود",25,TEXT,true);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(16),0,dp(12));r.addView(title);
        TextView body=txt(message,15,MUTED,false);body.setGravity(Gravity.CENTER);r.addView(body);
        setContentView(r);
    }

    private void showLockedScreen(){
        vaultReady=false;
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setGravity(Gravity.CENTER);r.setPadding(dp(28),dp(28),dp(28),dp(28));r.setBackgroundColor(BG);
        TextView icon=txt("◈",48,ACCENT,true);icon.setGravity(Gravity.CENTER);r.addView(icon);
        TextView title=txt("SOHEIL LOCKED",24,TEXT,true);title.setTextDirection(View.TEXT_DIRECTION_LTR);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(12),0,dp(7));r.addView(title);
        TextView body=txt("Vault رمزگذاری‌شده است. برای ورود احراز هویت کن.",14,MUTED,false);body.setGravity(Gravity.CENTER);body.setPadding(0,0,0,dp(20));r.addView(body);
        Button unlock=button("باز کردن با اثرانگشت / قفل گوشی",true);unlock.setOnClickListener(v->promptUnlock());r.addView(unlock,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView note=txt("AES-256-GCM • Android Keystore • Screen Shield",11,MUTED,false);note.setTextDirection(View.TEXT_DIRECTION_LTR);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(18),0,0);r.addView(note);
        setContentView(r);
    }

    private void promptUnlock(){
        if(authenticating) return;
        authenticating=true;
        Executor executor=ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt=new BiometricPrompt(this,executor,new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result){
                super.onAuthenticationSucceeded(result); authenticating=false;
                try { openVault(); }
                catch(Exception e){ if(crypto!=null)crypto.lockVault(); showSecurityRequirement("Vault باز نشد. قفل دستگاه را دوباره تأیید کن یا برنامه را ببند و باز کن."); }
            }
            @Override public void onAuthenticationError(int errorCode,@NonNull CharSequence errString){
                super.onAuthenticationError(errorCode,errString); authenticating=false; showLockedScreen();
            }
            @Override public void onAuthenticationFailed(){ super.onAuthenticationFailed(); }
        });

        BiometricPrompt.PromptInfo.Builder pb=new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock SOHEIL")
                .setSubtitle("Private Life Vault")
                .setConfirmationRequired(false);
        if(Build.VERSION.SDK_INT>=30){
            pb.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        }else{
            pb.setDeviceCredentialAllowed(true);
        }
        try { prompt.authenticate(pb.build()); }
        catch(Exception e){ authenticating=false; showSecurityRequirement("Biometric/Device Credential روی این دستگاه آماده نیست. ابتدا Screen Lock را در تنظیمات Android فعال کن."); }
    }

    private void openVault(){
        crypto.unlockVault();
        prefs=new SecurePrefs(this,crypto);
        db=new SoheilDb(this,crypto);
        jarvis=new JarvisEngine(db);
        migrateLegacyAlphaData();
        // Touch the encrypted focus key now so any old plaintext copy is migrated/removed.
        prefs.getString("today_focus","");
        ensureNotificationPermission();
        vaultReady=true;
        armInactivityLock();
        showShell(); showToday();
    }

    private void lockNow(String reason){
        if(!vaultReady)return;
        securityHandler.removeCallbacks(inactivityLock);
        vaultReady=false;
        if(db!=null){db.closeSecurely();db=null;}
        jarvis=null;prefs=null;
        if(crypto!=null)crypto.lockVault();
        showLockedScreen();
    }

    private void migrateLegacyAlphaData(){
        SharedPreferences legacy=getSharedPreferences("soheil",MODE_PRIVATE);
        String old=legacy.getString("last_capture","");
        boolean already=legacy.getBoolean("v03_migrated",false);
        if(!already && old!=null && !old.trim().isEmpty()) db.addCapture(old.trim());
        legacy.edit().remove("last_capture").putBoolean("v03_migrated",true).apply();
    }

    private void ensureNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},90);}

    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(1),stroke);return g;}
    private TextView txt(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setTextDirection(View.TEXT_DIRECTION_RTL);v.setGravity(Gravity.RIGHT);v.setLineSpacing(0,1.12f);return v;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(17),dp(16),dp(17),dp(16));c.setBackground(bg(PANEL,20,STROKE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));c.setLayoutParams(p);return c;}
    private TextView chip(String s){TextView v=txt(s,11,ACCENT,true);v.setGravity(Gravity.CENTER);v.setTextDirection(View.TEXT_DIRECTION_LTR);v.setPadding(dp(10),dp(6),dp(10),dp(6));v.setBackground(bg(Color.rgb(15,51,45),30,Color.TRANSPARENT));return v;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(primary?Color.rgb(4,23,19):TEXT);b.setBackground(bg(primary?ACCENT:PANEL2,15,primary?Color.TRANSPARENT:STROKE));b.setMinHeight(dp(49));return b;}
    private EditText input(String hint,boolean multi){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(97,114,129));e.setTextColor(TEXT);e.setTextSize(15);e.setTextDirection(View.TEXT_DIRECTION_RTL);e.setGravity((multi?Gravity.TOP:Gravity.CENTER_VERTICAL)|Gravity.RIGHT);e.setPadding(dp(15),dp(13),dp(15),dp(13));e.setBackground(bg(PANEL2,15,STROKE));e.setInputType(InputType.TYPE_CLASS_TEXT|(multi?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);if(Build.VERSION.SDK_INT>=26)e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return e;}
    private void gap(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private String fmt(long ms){if(ms<=0)return"بدون زمان";return new SimpleDateFormat("MMM d • HH:mm",Locale.getDefault()).format(new Date(ms));}

    private void showShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,i)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());top=x.top;bottom=x.bottom;}else{top=i.getSystemWindowInsetTop();bottom=i.getSystemWindowInsetBottom();}root.setPadding(0,top,0,bottom);return i;});
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(12),dp(18),dp(8));
        TextView brand=txt("SOHEIL",20,TEXT,true);brand.setTextDirection(View.TEXT_DIRECTION_LTR);brand.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);header.addView(brand,new LinearLayout.LayoutParams(0,dp(42),1));header.addView(chip("◈ VAULT SECURE"));root.addView(header);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(17),dp(8),dp(17),dp(26));sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        navBar=new LinearLayout(this);navBar.setOrientation(LinearLayout.HORIZONTAL);navBar.setPadding(dp(7),dp(7),dp(7),dp(9));navBar.setBackgroundColor(Color.rgb(9,14,20));
        nav("⌂\nامروز",0,()->showToday());nav("✓\nکارها",1,()->showTasks());nav("＋\nثبت",2,()->showCapture());nav("✦\nجارویس",3,()->showJarvis());nav("●\nمن",4,()->showMe());root.addView(navBar,new LinearLayout.LayoutParams(-1,dp(76)));setContentView(root);root.requestApplyInsets();selectNav(0);
    }
    private void nav(String s,int idx,Runnable r){TextView v=txt(s,12,MUTED,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(3),dp(6),dp(3),dp(6));v.setOnClickListener(x->{selectNav(idx);r.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);navBar.addView(v,p);}
    private void selectNav(int n){if(navBar==null)return;for(int i=0;i<navBar.getChildCount();i++){TextView v=(TextView)navBar.getChildAt(i);boolean on=i==n;v.setTextColor(on?ACCENT:MUTED);v.setTypeface(Typeface.create("sans-serif",on?Typeface.BOLD:Typeface.NORMAL));v.setBackground(bg(on?Color.rgb(15,51,45):Color.TRANSPARENT,17,Color.TRANSPARENT));}}
    private void clear(){content.removeAllViews();}
    private void heading(String t,String s){content.addView(txt(t,29,TEXT,true));TextView x=txt(s,13,MUTED,false);x.setPadding(0,dp(4),0,0);content.addView(x);gap(content,17);}

    private void showToday(){
        clear();selectNav(0);String focus=prefs.getString("today_focus","");heading("امروز","Morning Brief • Secure local context");
        LinearLayout brief=card();brief.addView(chip("JARVIS BRIEF"));TextView b=txt(jarvis.morningBrief(focus),16,TEXT,false);b.setPadding(0,dp(13),0,0);brief.addView(b);content.addView(brief);
        LinearLayout f=card();f.addView(txt("تمرکز اصلی",18,TEXT,true));TextView current=txt(focus.isEmpty()?"هنوز تعیین نشده":focus,20,focus.isEmpty()?MUTED:TEXT,true);current.setPadding(0,dp(10),0,dp(11));f.addView(current);EditText fi=input("یک نتیجه مهم برای امروز…",false);f.addView(fi,new LinearLayout.LayoutParams(-1,dp(54)));gap(f,9);Button save=button(focus.isEmpty()?"ثبت Focus":"تغییر Focus",true);save.setOnClickListener(v->{String s=fi.getText().toString().trim();if(!s.isEmpty()){prefs.putString("today_focus",s);showToday();}});f.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));content.addView(f);
        LinearLayout tasks=card();LinearLayout tr=new LinearLayout(this);tr.setOrientation(LinearLayout.HORIZONTAL);tr.setGravity(Gravity.CENTER_VERTICAL);tr.addView(txt("کارهای باز",18,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));tr.addView(chip(String.valueOf(db.openTaskCount())));tasks.addView(tr);List<SoheilDb.Task> list=db.getOpenTasks(3);if(list.isEmpty()){TextView z=txt("فعلاً Task بازی نداری.",14,MUTED,false);z.setPadding(0,dp(12),0,0);tasks.addView(z);}else for(SoheilDb.Task t:list){TextView line=txt("•  "+t.title+"\n   "+t.area+"  •  "+fmt(t.dueAt),14,TEXT,false);line.setPadding(0,dp(11),0,0);tasks.addView(line);}gap(tasks,12);Button all=button("مدیریت همه کارها",false);all.setOnClickListener(v->showTasks());tasks.addView(all,new LinearLayout.LayoutParams(-1,dp(48)));content.addView(tasks);
        LinearLayout inbox=card();inbox.addView(txt("Inbox",18,TEXT,true));TextView ic=txt(db.inboxCount()+" مورد منتظر تصمیم توست",14,db.inboxCount()>0?ACCENT:MUTED,true);ic.setPadding(0,dp(8),0,dp(11));inbox.addView(ic);Button cap=button("＋ Capture سریع",true);cap.setOnClickListener(v->showCapture());inbox.addView(cap,new LinearLayout.LayoutParams(-1,dp(49)));content.addView(inbox);
    }

    private void showCapture(){
        clear();selectNav(2);heading("Capture","Encrypted Universal Inbox");LinearLayout c=card();c.addView(chip("ENCRYPTED INBOX"));EditText e=input("فکر، کار، سؤال، ایده…",true);e.setMinLines(5);gap(c,12);c.addView(e,new LinearLayout.LayoutParams(-1,dp(170)));gap(c,10);Button add=button("ذخیره رمزگذاری‌شده",true);add.setOnClickListener(v->{String s=e.getText().toString().trim();if(!s.isEmpty()){db.addCapture(s);e.setText("");Toast.makeText(this,"Saved securely",Toast.LENGTH_SHORT).show();showCapture();}});c.addView(add,new LinearLayout.LayoutParams(-1,dp(51)));content.addView(c);content.addView(txt("Inbox",20,TEXT,true));gap(content,9);List<SoheilDb.Capture> list=db.getInbox(12);if(list.isEmpty())content.addView(txt("Inbox خالی است.",14,MUTED,false));for(SoheilDb.Capture x:list){LinearLayout item=card();item.addView(txt(x.text,15,TEXT,false));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(12),0,0);Button task=button("Task",true),mem=button("Memory",false),done=button("Done",false);task.setOnClickListener(v->{db.captureToTask(x);showCapture();});mem.setOnClickListener(v->{db.captureToMemory(x);showCapture();});done.setOnClickListener(v->{db.markCapture(x.id,"DONE",1);showCapture();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),0,dp(3),0);row.addView(task,p);LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(44),1);p2.setMargins(dp(3),0,dp(3),0);row.addView(mem,p2);LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(0,dp(44),1);p3.setMargins(dp(3),0,dp(3),0);row.addView(done,p3);item.addView(row);content.addView(item);}}

    private void showTasks(){
        clear();selectNav(1);heading("کارها","Encrypted Task Manager + private reminders");LinearLayout add=card();EditText title=input("عنوان کار…",false);add.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));gap(add,9);EditText minutes=input("یادآوری بعد از چند دقیقه (اختیاری)",false);minutes.setInputType(InputType.TYPE_CLASS_NUMBER);minutes.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);add.addView(minutes,new LinearLayout.LayoutParams(-1,dp(54)));gap(add,9);Button save=button("اضافه کردن Task",true);save.setOnClickListener(v->{String s=title.getText().toString().trim();if(s.isEmpty())return;long due=0;try{int m=Integer.parseInt(minutes.getText().toString().trim());if(m>0)due=System.currentTimeMillis()+m*60000L;}catch(Exception ignored){}long id=db.addTask(s,"شخصی",2,due);if(due>0)scheduleReminder(id,due);showTasks();});add.addView(save,new LinearLayout.LayoutParams(-1,dp(51)));content.addView(add);List<SoheilDb.Task> list=db.getOpenTasks(50);if(list.isEmpty())content.addView(txt("هیچ Task بازی وجود ندارد.",14,MUTED,false));for(SoheilDb.Task t:list){LinearLayout item=card();LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);TextView titlev=txt(t.title,16,TEXT,true);top.addView(titlev,new LinearLayout.LayoutParams(0,-2,1));Button done=button("✓",true);done.setOnClickListener(v->{db.completeTask(t.id);showTasks();});top.addView(done,new LinearLayout.LayoutParams(dp(52),dp(45)));item.addView(top);TextView meta=txt(t.area+"  •  "+fmt(t.dueAt),12,t.dueAt>0?ACCENT:MUTED,false);meta.setPadding(0,dp(7),0,0);item.addView(meta);content.addView(item);}}

    private void scheduleReminder(long id,long at){AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);Intent in=new Intent(this,ReminderReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(this,(int)(id%Integer.MAX_VALUE),in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);Toast.makeText(this,"Private reminder set",Toast.LENGTH_SHORT).show();}

    private void showJarvis(){
        clear();selectNav(3);heading("Jarvis","Local-only • audited access • encrypted memory");List<SoheilDb.Message> msgs=db.recentMessages(8);for(SoheilDb.Message m:msgs){LinearLayout bubble=card();TextView role=txt(m.role.equals("user")?"YOU":"JARVIS",10,m.role.equals("user")?MUTED:ACCENT,true);role.setTextDirection(View.TEXT_DIRECTION_LTR);role.setGravity(Gravity.LEFT);bubble.addView(role);TextView body=txt(m.text,15,TEXT,false);body.setPadding(0,dp(8),0,0);bubble.addView(body);content.addView(bubble);}LinearLayout askbox=card();EditText q=input("از Jarvis بپرس…",true);q.setMinLines(3);askbox.addView(q,new LinearLayout.LayoutParams(-1,dp(110)));gap(askbox,9);Button ask=button("Ask Jarvis ✦",true);ask.setOnClickListener(v->{String query=q.getText().toString().trim();if(query.isEmpty())return;db.addMessage("user",query);String a=jarvis.answer(query,prefs.getString("today_focus",""));db.addMessage("jarvis",a);showJarvis();});askbox.addView(ask,new LinearLayout.LayoutParams(-1,dp(51)));content.addView(askbox);
    }

    private SeekBar slider(int value){SeekBar s=new SeekBar(this);s.setMax(4);s.setProgress(Math.max(0,Math.min(4,value-1)));return s;}
    private void addMetric(LinearLayout box,String title,SeekBar seek){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);TextView l=txt(title,14,TEXT,true);r.addView(l,new LinearLayout.LayoutParams(dp(85),-2));r.addView(seek,new LinearLayout.LayoutParams(0,-2,1));box.addView(r);}

    private void showMe(){
        clear();selectNav(4);heading("Me","Daily State + Security Center");SoheilDb.DailyState state=db.todayState();
        LinearLayout check=card();check.addView(txt("Daily Check-in",19,TEXT,true));TextView hint=txt("همه این مقادیر داخل Vault رمزگذاری می‌شوند.",12,MUTED,false);hint.setPadding(0,dp(5),0,dp(8));check.addView(hint);SeekBar mood=slider(state==null?3:state.mood),energy=slider(state==null?3:state.energy),stress=slider(state==null?3:state.stress);addMetric(check,"Mood",mood);addMetric(check,"Energy",energy);addMetric(check,"Stress",stress);EditText sleep=input("خواب دیشب (ساعت، مثلاً 7.5)",false);sleep.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(state!=null&&state.sleep>0)sleep.setText(String.format(Locale.US,"%.1f",state.sleep));check.addView(sleep,new LinearLayout.LayoutParams(-1,dp(52)));gap(check,8);EditText note=input("یادداشت کوتاه امروز…",true);if(state!=null)note.setText(state.note);check.addView(note,new LinearLayout.LayoutParams(-1,dp(90)));gap(check,9);Button save=button("ذخیره Check-in",true);save.setOnClickListener(v->{float sh=0;try{sh=Float.parseFloat(sleep.getText().toString().trim());}catch(Exception ignored){}db.saveDailyState(mood.getProgress()+1,energy.getProgress()+1,stress.getProgress()+1,sh,note.getText().toString().trim());Toast.makeText(this,"Encrypted check-in saved",Toast.LENGTH_SHORT).show();showMe();});check.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));content.addView(check);

        LinearLayout security=card();security.addView(chip("SECURITY CENTER"));TextView summary=txt(SecurityCenter.runtimeSummary(this,crypto),13,TEXT,false);summary.setTextDirection(View.TEXT_DIRECTION_LTR);summary.setGravity(Gravity.LEFT);summary.setPadding(0,dp(12),0,0);security.addView(summary);if(SecurityCenter.isDebuggable(this)){TextView w=txt("این build برای تست است؛ برای نگهداری اطلاعات فوق‌حساس از Release-signed build استفاده کن.",13,WARN,true);w.setPadding(0,dp(12),0,0);security.addView(w);}content.addView(security);

        LinearLayout access=card();access.addView(txt("JARVIS ACCESS",12,ACCENT,true));TextView policy=txt("Tasks: Local allowed\nInbox: Local allowed\nMemory: Local allowed + audited\nDaily State: Local allowed + audited\nNetwork AI: DENIED (no INTERNET permission)\nHealth / Calendar / Files: DENIED",13,MUTED,false);policy.setTextDirection(View.TEXT_DIRECTION_LTR);policy.setGravity(Gravity.LEFT);policy.setPadding(0,dp(10),0,0);access.addView(policy);content.addView(access);

        Button lock=button("قفل کردن SOHEIL الآن",false);lock.setOnClickListener(v->lockNow("manual"));content.addView(lock,new LinearLayout.LayoutParams(-1,dp(52)));
    }
}
