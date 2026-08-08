package com.soheil.lifeos;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    private LinearLayout content;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("soheil", MODE_PRIVATE);
        showShell();
        showToday();
    }

    private TextView text(String value, int size) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setPadding(18,14,18,14);
        v.setTextDirection(View.TEXT_DIRECTION_RTL); v.setGravity(Gravity.RIGHT);
        return v;
    }

    private Button nav(String label, View.OnClickListener l) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(p); return b;
    }

    private void showShell() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        TextView header = text("SOHEIL  •  JARVIS", 24); header.setGravity(Gravity.CENTER); root.addView(header);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(20,10,20,10); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(nav("امروز", v -> showToday())); nav.addView(nav("ثبت", v -> showCapture())); nav.addView(nav("زندگی", v -> showLife())); nav.addView(nav("جارویس", v -> showJarvis())); nav.addView(nav("من", v -> showMe()));
        root.addView(nav); setContentView(root);
    }

    private void clear() { content.removeAllViews(); }

    private void showToday() {
        clear(); content.addView(text("امروز", 28)); content.addView(text("آنچه الآن مهم است", 17));
        content.addView(text("• یک کار مهم را انتخاب کن\n• هر فکر باز را در Capture بریز\n• قبل از اضافه کردن کار جدید، Inbox را خالی کن", 18));
        String last = prefs.getString("last_capture", "هنوز چیزی ثبت نشده است.");
        content.addView(text("آخرین ثبت:\n" + last, 16));
    }

    private void showCapture() {
        clear(); content.addView(text("Capture", 28)); content.addView(text("هر چیزی که در ذهنت است همین‌جا خالی کن.", 17));
        EditText input = new EditText(this); input.setHint("فکر، کار، سؤال، ایده..."); input.setMinLines(5); input.setGravity(Gravity.TOP|Gravity.RIGHT); input.setTextDirection(View.TEXT_DIRECTION_RTL); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); content.addView(input);
        Button save = new Button(this); save.setText("ذخیره در SOHEIL"); save.setOnClickListener(v -> { String s = input.getText().toString().trim(); if(!s.isEmpty()){ prefs.edit().putString("last_capture", s).apply(); input.setText(""); content.addView(text("ذخیره شد ✓", 16)); } }); content.addView(save);
    }

    private void showLife() {
        clear(); content.addView(text("Life Areas", 28));
        content.addView(text("سلامت  •  یادگیری  •  کار  •  روابط  •  مالی  •  شخصی", 19));
        content.addView(text("این صفحه در نسخه بعدی به داشبورد واقعی هر حوزه، اهداف، روندها و پروژه‌ها وصل می‌شود.", 16));
    }

    private void showJarvis() {
        clear(); content.addView(text("Jarvis", 28)); content.addView(text("نسخه محلی اولیه — بدون اینترنت", 15));
        EditText input = new EditText(this); input.setHint("از جارویس بپرس..."); input.setTextDirection(View.TEXT_DIRECTION_RTL); input.setGravity(Gravity.RIGHT); content.addView(input);
        TextView answer = text("", 17); content.addView(answer);
        Button ask = new Button(this); ask.setText("Ask Jarvis"); ask.setOnClickListener(v -> { String q = input.getText().toString().trim(); String cap = prefs.getString("last_capture", ""); String a;
            if(q.contains("امروز") || q.contains("کار")) a = "برای امروز یک کار اصلی انتخاب کن. آخرین چیزی که ثبت کرده‌ای: " + (cap.isEmpty()?"چیزی ثبت نشده":cap);
            else if(q.contains("یاد") || q.contains("حافظ")) a = "در این Alpha من فقط Capture اخیر را روی گوشی نگه می‌دارم. Memory Engine کامل در v0.2 اصلی فعال می‌شود.";
            else a = "من Jarvis Local هستم. فعلاً Context محدودی دارم، اما مسیر نصب و اجرای SOHEIL روی گوشی واقعی را داریم تثبیت می‌کنیم.";
            answer.setText(a);
        }); content.addView(ask);
    }

    private void showMe() {
        clear(); content.addView(text("Me", 28)); content.addView(text("SOHEIL v0.2-alpha\nJarvis Local: فعال\nStorage: محلی روی گوشی\nCloud AI: هنوز متصل نشده", 18));
    }
}
