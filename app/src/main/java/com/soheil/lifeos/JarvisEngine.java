package com.soheil.lifeos;

import java.util.List;

public class JarvisEngine {
    private final SoheilDb db;
    public JarvisEngine(SoheilDb db){this.db=db;}

    public String morningBrief(String focus){
        int tasks=db.openTaskCount(); int inbox=db.inboxCount(); SoheilDb.DailyState s=db.todayState();
        StringBuilder b=new StringBuilder();
        if(s==null) b.append("Check-in امروز هنوز ثبت نشده. ");
        else { b.append("Energy ").append(s.energy).append("/5، Stress ").append(s.stress).append("/5. "); if(s.sleep>0) b.append("خواب ").append(String.format(java.util.Locale.US,"%.1f",s.sleep)).append(" ساعت. "); }
        if(focus==null||focus.trim().isEmpty()) b.append("اول یک Focus اصلی برای امروز تعیین کن. ");
        else b.append("Focus: «").append(focus).append("». ");
        b.append(tasks).append(" کار باز و ").append(inbox).append(" مورد در Inbox داری.");
        return b.toString();
    }

    public String answer(String q, String focus){
        String query=q==null?"":q.trim();
        SoheilDb.DailyState s=db.todayState();
        List<SoheilDb.Task> tasks=db.getOpenTasks(5);
        SoheilDb.Capture cap=db.latestCapture();
        StringBuilder a=new StringBuilder();

        if(query.contains("امروز")||query.contains("تمرکز")||query.contains("چی کار")||query.contains("چه کار")){
            if(focus==null||focus.isEmpty()) a.append("Focus اصلی امروز هنوز مشخص نشده. "); else a.append("Focus ثبت‌شده‌ات «").append(focus).append("» است. ");
            if(!tasks.isEmpty()){ a.append("کارهای باز مهم: "); for(int i=0;i<Math.min(3,tasks.size());i++){if(i>0)a.append("، ");a.append(tasks.get(i).title);} a.append(". "); }
            if(s!=null&&s.energy<=2) a.append("Energy امروز پایین ثبت شده؛ کار اصلی را به یک بلوک کوتاه‌تر بشکن.");
            else if(s!=null&&s.stress>=4) a.append("Stress بالاست؛ تعداد تعهدات جدید را امروز کم نگه دار.");
            else a.append("کار جدید اضافه نکن مگر اینکه از Focus فعلی مهم‌تر باشد.");
        } else if(query.contains("inbox")||query.contains("اینباکس")||query.contains("ثبت")){
            a.append("الان ").append(db.inboxCount()).append(" مورد پردازش‌نشده در Inbox داری. ");
            if(cap!=null) a.append("آخرین Capture: «").append(cap.text).append("». "); a.append("هر مورد را به Task، Memory یا Done تبدیل کن.");
        } else if(query.contains("حافظ")||query.contains("یاد")||query.contains("memory")){
            List<SoheilDb.Memory> memories=db.getRecentMemories(3);
            if(memories.isEmpty()) a.append("هنوز Memory دائمی ثبت نکرده‌ای. از Inbox یک مورد را به Memory تبدیل کن.");
            else {a.append("سه Memory اخیر: ");for(int i=0;i<memories.size();i++){if(i>0)a.append(" | ");a.append(memories.get(i).text);} }
        } else if(query.contains("حال")||query.contains("انرژی")||query.contains("استرس")||query.contains("خواب")){
            if(s==null) a.append("Check-in امروز ثبت نشده. از بخش Me وضعیت امروز را وارد کن.");
            else a.append("امروز Mood ").append(s.mood).append("/5، Energy ").append(s.energy).append("/5، Stress ").append(s.stress).append("/5 و Sleep ").append(String.format(java.util.Locale.US,"%.1f",s.sleep)).append("h ثبت شده.");
        } else {
            String keyword=longestWord(query); List<SoheilDb.Memory> found=keyword.length()>2?db.searchMemories(keyword,3):db.getRecentMemories(2);
            if(!found.isEmpty()){ a.append("در حافظه SOHEIL چیزی مرتبط پیدا کردم: "); for(int i=0;i<found.size();i++){if(i>0)a.append(" | ");a.append(found.get(i).text);} }
            else a.append("برای این سؤال هنوز Context کافی در حافظه محلی ندارم. Capture و Taskهای بیشتری ثبت کن؛ در نسخه AI آنلاین تحلیل معنایی عمیق‌تر اضافه می‌شود.");
        }
        return a.toString();
    }

    private String longestWord(String q){String[] parts=q.replaceAll("[^\\p{L}\\p{N} ]"," ").split("\\s+");String best="";for(String p:parts)if(p.length()>best.length())best=p;return best;}
}
