package com.soheil.lifeos;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;
import java.util.Calendar;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 12, 17);
    private static final int PANEL = Color.rgb(17, 24, 32);
    private static final int PANEL_2 = Color.rgb(22, 31, 41);
    private static final int TEXT = Color.rgb(239, 244, 248);
    private static final int MUTED = Color.rgb(144, 158, 171);
    private static final int ACCENT = Color.rgb(80, 227, 194);
    private static final int ACCENT_DARK = Color.rgb(18, 55, 49);
    private static final int STROKE = Color.rgb(39, 50, 62);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout navBar;
    private SharedPreferences prefs;
    private int activeTab = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("soheil", MODE_PRIVATE);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        showShell();
        showToday();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setTextDirection(View.TEXT_DIRECTION_RTL);
        v.setGravity(Gravity.RIGHT);
        v.setLineSpacing(0, 1.12f);
        return v;
    }

    private TextView sectionTitle(String title, String eyebrow) {
        LinearLayout box = new LinearLayout(this);
        return label(title, 26, TEXT, true);
    }

    private void addGap(int dpValue) {
        View gap = new View(this);
        content.addView(gap, new LinearLayout.LayoutParams(1, dp(dpValue)));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(shape(PANEL, 20, STROKE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(p);
        return card;
    }

    private TextView chip(String text) {
        TextView chip = label(text, 12, ACCENT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(shape(ACCENT_DARK, 30, Color.TRANSPARENT));
        return chip;
    }

    private Button primaryButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(15);
        b.setTextColor(Color.rgb(4, 21, 18));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(shape(ACCENT, 16, Color.TRANSPARENT));
        b.setMinHeight(dp(52));
        return b;
    }

    private Button secondaryButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(shape(PANEL_2, 16, STROKE));
        b.setMinHeight(dp(50));
        return b;
    }

    private EditText inputBox(String hint, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(103, 119, 133));
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setBackground(shape(PANEL_2, 16, STROKE));
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setGravity((multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL) | Gravity.RIGHT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) input.setMinLines(6);
        return input;
    }

    private void showShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(0, top, 0, bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(10));

        TextView brand = label("SOHEIL", 20, TEXT, true);
        brand.setTextDirection(View.TEXT_DIRECTION_LTR);
        brand.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView jarvisState = chip("●  JARVIS LOCAL");
        jarvisState.setTextDirection(View.TEXT_DIRECTION_LTR);
        header.addView(jarvisState);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), dp(26));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(8), dp(8), dp(8), dp(10));
        navBar.setBackgroundColor(Color.rgb(10, 15, 21));
        addNavItem("⌂\nامروز", 0, () -> showToday());
        addNavItem("＋\nثبت", 1, () -> showCapture());
        addNavItem("◫\nزندگی", 2, () -> showLife());
        addNavItem("✦\nجارویس", 3, () -> showJarvis());
        addNavItem("●\nمن", 4, () -> showMe());
        root.addView(navBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        setContentView(root);
        root.requestApplyInsets();
        updateNav(0);
    }

    private void addNavItem(String text, int index, Runnable action) {
        TextView item = label(text, 12, MUTED, false);
        item.setGravity(Gravity.CENTER);
        item.setTextDirection(View.TEXT_DIRECTION_RTL);
        item.setPadding(dp(4), dp(7), dp(4), dp(7));
        item.setOnClickListener(v -> {
            activeTab = index;
            updateNav(index);
            action.run();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        navBar.addView(item, p);
    }

    private void updateNav(int selected) {
        activeTab = selected;
        if (navBar == null) return;
        for (int i = 0; i < navBar.getChildCount(); i++) {
            TextView v = (TextView) navBar.getChildAt(i);
            boolean on = i == selected;
            v.setTextColor(on ? ACCENT : MUTED);
            v.setTypeface(Typeface.create("sans-serif", on ? Typeface.BOLD : Typeface.NORMAL));
            v.setBackground(on ? shape(ACCENT_DARK, 18, Color.TRANSPARENT) : shape(Color.TRANSPARENT, 18, Color.TRANSPARENT));
        }
    }

    private void clear() {
        content.removeAllViews();
    }

    private void pageHeading(String title, String subtitle) {
        TextView t = label(title, 30, TEXT, true);
        content.addView(t);
        TextView s = label(subtitle, 14, MUTED, false);
        s.setPadding(0, dp(4), 0, 0);
        content.addView(s);
        addGap(18);
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "صبح بخیر";
        if (hour < 18) return "بعدازظهر بخیر";
        return "شب بخیر";
    }

    private void showToday() {
        clear();
        updateNav(0);
        pageHeading(greeting(), "مرکز فرمان امروز");

        LinearLayout focus = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow = label("WHAT MATTERS NOW", 11, ACCENT, true);
        eyebrow.setTextDirection(View.TEXT_DIRECTION_LTR);
        eyebrow.setGravity(Gravity.LEFT);
        row.addView(eyebrow, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(chip("FOCUS"));
        focus.addView(row);

        String savedFocus = prefs.getString("today_focus", "");
        TextView focusText = label(savedFocus.isEmpty() ? "مهم‌ترین کار امروز را مشخص کن" : savedFocus, 21, TEXT, true);
        focusText.setPadding(0, dp(15), 0, dp(13));
        focus.addView(focusText);

        EditText focusInput = inputBox("مثلاً: ۴۵ دقیقه کار عمیق روی مهم‌ترین هدف", false);
        focus.addView(focusInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        View fg = new View(this); focus.addView(fg, new LinearLayout.LayoutParams(1, dp(10)));
        Button setFocus = primaryButton(savedFocus.isEmpty() ? "ثبت تمرکز امروز" : "تغییر تمرکز");
        setFocus.setOnClickListener(v -> {
            String s = focusInput.getText().toString().trim();
            if (!s.isEmpty()) {
                prefs.edit().putString("today_focus", s).apply();
                showToday();
            }
        });
        focus.addView(setFocus);
        content.addView(focus);

        LinearLayout quick = card();
        quick.addView(label("ورودی سریع", 18, TEXT, true));
        TextView q = label("فکر را نگه ندار؛ مستقیم وارد سیستمش کن.", 14, MUTED, false);
        q.setPadding(0, dp(5), 0, dp(14));
        quick.addView(q);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button capture = primaryButton("＋  Capture");
        capture.setOnClickListener(v -> { updateNav(1); showCapture(); });
        Button ask = secondaryButton("✦  Ask Jarvis");
        ask.setOnClickListener(v -> { updateNav(3); showJarvis(); });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        bp.setMargins(0, 0, dp(6), 0);
        buttons.addView(capture, bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        bp2.setMargins(dp(6), 0, 0, 0);
        buttons.addView(ask, bp2);
        quick.addView(buttons);
        content.addView(quick);

        LinearLayout status = card();
        status.addView(label("وضعیت سیستم", 18, TEXT, true));
        int count = prefs.getInt("capture_count", 0);
        String last = prefs.getString("last_capture", "");
        TextView metrics = label("Captureها  " + count + "     •     Jarvis  Local", 14, ACCENT, true);
        metrics.setPadding(0, dp(10), 0, dp(8));
        status.addView(metrics);
        status.addView(label(last.isEmpty() ? "هنوز چیزی ثبت نکرده‌ای." : "آخرین ثبت:  " + last, 14, MUTED, false));
        content.addView(status);
    }

    private void showCapture() {
        clear();
        updateNav(1);
        pageHeading("Capture", "هر چیزی که در ذهن باز مانده، اینجا تخلیه کن");

        LinearLayout c = card();
        c.addView(chip("UNIVERSAL INBOX"));
        TextView prompt = label("فکر، کار، سؤال یا ایده را بدون مرتب‌سازی بنویس.", 17, TEXT, true);
        prompt.setPadding(0, dp(14), 0, dp(12));
        c.addView(prompt);
        EditText input = inputBox("اینجا بنویس…", true);
        c.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
        View gap = new View(this); c.addView(gap, new LinearLayout.LayoutParams(1, dp(12)));
        TextView result = label("", 13, ACCENT, true);
        c.addView(result);
        Button save = primaryButton("ذخیره در SOHEIL");
        save.setOnClickListener(v -> {
            String s = input.getText().toString().trim();
            if (!s.isEmpty()) {
                int count = prefs.getInt("capture_count", 0) + 1;
                prefs.edit().putString("last_capture", s).putInt("capture_count", count).apply();
                input.setText("");
                result.setText("✓ ثبت شد. Jarvis در نسخه‌های بعدی آن را طبقه‌بندی می‌کند.");
            }
        });
        c.addView(save, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        content.addView(c);
    }

    private LinearLayout lifeMiniCard(String title, String subtitle, String symbol) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(shape(PANEL, 18, STROKE));
        TextView icon = label(symbol, 21, ACCENT, true);
        icon.setGravity(Gravity.RIGHT);
        c.addView(icon);
        TextView t = label(title, 16, TEXT, true);
        t.setPadding(0, dp(8), 0, dp(3));
        c.addView(t);
        c.addView(label(subtitle, 12, MUTED, false));
        return c;
    }

    private void addLifeRow(LinearLayout parent, LinearLayout left, LinearLayout right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(126), 1f);
        p1.setMargins(0, 0, dp(6), dp(12));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(126), 1f);
        p2.setMargins(dp(6), 0, 0, dp(12));
        row.addView(left, p1);
        row.addView(right, p2);
        parent.addView(row);
    }

    private void showLife() {
        clear();
        updateNav(2);
        pageHeading("Life", "نقشه‌ی کل زندگی در یک نگاه");
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        addLifeRow(grid, lifeMiniCard("سلامت", "بدن • خواب • ورزش", "♥"), lifeMiniCard("یادگیری", "مطالعه • مهارت", "◇"));
        addLifeRow(grid, lifeMiniCard("کار", "پروژه‌ها • شیفت", "▣"), lifeMiniCard("روابط", "افراد • پیگیری", "◎"));
        addLifeRow(grid, lifeMiniCard("مالی", "تصمیم‌ها • روند", "↗"), lifeMiniCard("شخصی", "اهداف • زندگی", "✦"));
        content.addView(grid);
        content.addView(label("این کارت‌ها از نسخه‌ی بعد به داده، هدف و روند واقعی هر حوزه متصل می‌شوند.", 13, MUTED, false));
    }

    private void showJarvis() {
        clear();
        updateNav(3);
        pageHeading("Jarvis", "لایه‌ی هوشمند SOHEIL — فعلاً Local Mode");

        LinearLayout context = card();
        context.addView(chip("CONTEXT ONLINE"));
        String focus = prefs.getString("today_focus", "ثبت نشده");
        String cap = prefs.getString("last_capture", "ثبت نشده");
        TextView ctext = label("تمرکز امروز: " + focus + "\n\nآخرین Capture: " + cap, 14, MUTED, false);
        ctext.setPadding(0, dp(12), 0, 0);
        context.addView(ctext);
        content.addView(context);

        LinearLayout chat = card();
        TextView answer = label("من آماده‌ام. فعلاً از اطلاعات محلی همین گوشی استفاده می‌کنم.", 16, TEXT, false);
        answer.setPadding(0, 0, 0, dp(14));
        chat.addView(answer);
        EditText input = inputBox("از Jarvis بپرس…", false);
        chat.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        View gap = new View(this); chat.addView(gap, new LinearLayout.LayoutParams(1, dp(10)));
        Button ask = primaryButton("Ask Jarvis  ✦");
        ask.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            String last = prefs.getString("last_capture", "");
            String currentFocus = prefs.getString("today_focus", "");
            String a;
            if (q.contains("امروز") || q.contains("کار") || q.contains("تمرکز")) {
                a = currentFocus.isEmpty()
                        ? "هنوز تمرکز اصلی امروز را ثبت نکرده‌ای. از صفحه امروز یک کار اصلی انتخاب کن."
                        : "تمرکز اصلی امروزت «" + currentFocus + "» است. قبل از اضافه کردن کار جدید، همین را جلو ببر.";
            } else if (q.contains("یاد") || q.contains("حافظ")) {
                a = "در Alpha فعلی من Focus و آخرین Capture را می‌بینم. Memory Engine کامل بعد از تثبیت UI فعال می‌شود.";
            } else if (!last.isEmpty()) {
                a = "آخرین چیزی که وارد سیستم کردی این بود: «" + last + "». فعلاً Local Mode هستم؛ در نسخه AI آن را با حافظه و Context کامل تحلیل می‌کنم.";
            } else {
                a = "فعلاً داده‌ی کافی ندارم. یک Focus یا Capture ثبت کن تا Context محلی من شکل بگیرد.";
            }
            answer.setText(a);
        });
        chat.addView(ask, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        content.addView(chat);
    }

    private void showMe() {
        clear();
        updateNav(4);
        pageHeading("Me", "وضعیت SOHEIL و دسترسی‌های Jarvis");

        LinearLayout c = card();
        c.addView(label("SOHEIL", 22, TEXT, true));
        TextView version = label("v0.2.1-alpha  •  UI Rescue", 13, ACCENT, true);
        version.setPadding(0, dp(5), 0, dp(16));
        c.addView(version);
        c.addView(label("Jarvis Local    فعال\nStorage          روی گوشی\nCloud AI        هنوز متصل نشده\nSystem Insets   Safe", 15, MUTED, false));
        content.addView(c);

        LinearLayout privacy = card();
        privacy.addView(label("JARVIS ACCESS", 12, ACCENT, true));
        TextView p = label("در این Alpha فقط Focus و Captureهای داخل خود SOHEIL خوانده می‌شوند. هیچ Calendar، Health یا فایل دیگری بدون اجازه وارد سیستم نشده است.", 14, MUTED, false);
        p.setPadding(0, dp(10), 0, 0);
        privacy.addView(p);
        content.addView(privacy);
    }
}
