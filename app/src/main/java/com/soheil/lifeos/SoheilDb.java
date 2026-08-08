package com.soheil.lifeos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SoheilDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "soheil_v03.db";
    private static final int DB_VERSION = 1;

    public static class Capture { public long id; public String text; public String type; public int status; public long createdAt; }
    public static class Task { public long id; public String title; public String area; public int priority; public long dueAt; public int done; public long createdAt; }
    public static class Memory { public long id; public String text; public String tags; public long createdAt; }
    public static class DailyState { public String date; public int mood; public int energy; public int stress; public float sleep; public String note; }
    public static class Message { public long id; public String role; public String text; public long createdAt; }

    public SoheilDb(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, type TEXT DEFAULT 'INBOX', status INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, area TEXT DEFAULT 'شخصی', priority INTEGER DEFAULT 2, due_at INTEGER DEFAULT 0, done INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, tags TEXT DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE daily_state(date TEXT PRIMARY KEY, mood INTEGER, energy INTEGER, stress INTEGER, sleep REAL, note TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long addCapture(String text) { ContentValues v=new ContentValues();v.put("text",text);v.put("type","INBOX");v.put("status",0);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("captures",null,v); }
    public List<Capture> getInbox(int limit) { List<Capture> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,text,type,status,created_at FROM captures WHERE status=0 ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Capture x=new Capture();x.id=c.getLong(0);x.text=c.getString(1);x.type=c.getString(2);x.status=c.getInt(3);x.createdAt=c.getLong(4);out.add(x);}c.close();return out; }
    public Capture latestCapture() { Cursor c=getReadableDatabase().rawQuery("SELECT id,text,type,status,created_at FROM captures ORDER BY created_at DESC LIMIT 1",null);Capture x=null;if(c.moveToFirst()){x=new Capture();x.id=c.getLong(0);x.text=c.getString(1);x.type=c.getString(2);x.status=c.getInt(3);x.createdAt=c.getLong(4);}c.close();return x; }
    public int inboxCount() { Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM captures WHERE status=0",null);int n=0;if(c.moveToFirst())n=c.getInt(0);c.close();return n; }
    public void markCapture(long id,String type,int status){ContentValues v=new ContentValues();v.put("type",type);v.put("status",status);getWritableDatabase().update("captures",v,"id=?",new String[]{String.valueOf(id)});}
    public long captureToTask(Capture capture){long id=addTask(capture.text,"شخصی",2,0);markCapture(capture.id,"TASK",1);return id;}
    public long captureToMemory(Capture capture){long id=addMemory(capture.text,"inbox");markCapture(capture.id,"MEMORY",1);return id;}

    public long addTask(String title,String area,int priority,long dueAt){ContentValues v=new ContentValues();v.put("title",title);v.put("area",area);v.put("priority",priority);v.put("due_at",dueAt);v.put("done",0);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("tasks",null,v);}
    public List<Task> getOpenTasks(int limit){List<Task> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,title,area,priority,due_at,done,created_at FROM tasks WHERE done=0 ORDER BY CASE WHEN due_at>0 THEN 0 ELSE 1 END, due_at ASC, priority ASC, created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Task t=new Task();t.id=c.getLong(0);t.title=c.getString(1);t.area=c.getString(2);t.priority=c.getInt(3);t.dueAt=c.getLong(4);t.done=c.getInt(5);t.createdAt=c.getLong(6);out.add(t);}c.close();return out;}
    public int openTaskCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tasks WHERE done=0",null);int n=0;if(c.moveToFirst())n=c.getInt(0);c.close();return n;}
    public void completeTask(long id){ContentValues v=new ContentValues();v.put("done",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});}

    public long addMemory(String text,String tags){ContentValues v=new ContentValues();v.put("text",text);v.put("tags",tags);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("memories",null,v);}
    public List<Memory> getRecentMemories(int limit){List<Memory> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,text,tags,created_at FROM memories ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Memory m=new Memory();m.id=c.getLong(0);m.text=c.getString(1);m.tags=c.getString(2);m.createdAt=c.getLong(3);out.add(m);}c.close();return out;}
    public List<Memory> searchMemories(String query,int limit){List<Memory> out=new ArrayList<>();String like="%"+query.replace("%","")+"%";Cursor c=getReadableDatabase().rawQuery("SELECT id,text,tags,created_at FROM memories WHERE text LIKE ? OR tags LIKE ? ORDER BY created_at DESC LIMIT ?",new String[]{like,like,String.valueOf(limit)});while(c.moveToNext()){Memory m=new Memory();m.id=c.getLong(0);m.text=c.getString(1);m.tags=c.getString(2);m.createdAt=c.getLong(3);out.add(m);}c.close();return out;}

    public void saveDailyState(int mood,int energy,int stress,float sleep,String note){ContentValues v=new ContentValues();String date=todayKey();v.put("date",date);v.put("mood",mood);v.put("energy",energy);v.put("stress",stress);v.put("sleep",sleep);v.put("note",note);getWritableDatabase().insertWithOnConflict("daily_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public DailyState todayState(){Cursor c=getReadableDatabase().rawQuery("SELECT date,mood,energy,stress,sleep,note FROM daily_state WHERE date=?",new String[]{todayKey()});DailyState d=null;if(c.moveToFirst()){d=new DailyState();d.date=c.getString(0);d.mood=c.getInt(1);d.energy=c.getInt(2);d.stress=c.getInt(3);d.sleep=c.getFloat(4);d.note=c.getString(5);}c.close();return d;}
    public long addMessage(String role,String text){ContentValues v=new ContentValues();v.put("role",role);v.put("text",text);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("messages",null,v);}
    public List<Message> recentMessages(int limit){List<Message> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,role,text,created_at FROM messages ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Message m=new Message();m.id=c.getLong(0);m.role=c.getString(1);m.text=c.getString(2);m.createdAt=c.getLong(3);out.add(0,m);}c.close();return out;}
    public static String todayKey(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
}
