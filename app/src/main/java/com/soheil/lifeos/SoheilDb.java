package com.soheil.lifeos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SoheilDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "soheil_v03.db";
    private static final int DB_VERSION = 2;
    private final SoheilCrypto crypto;

    public static class Capture { public long id; public String text; public String type; public int status; public long createdAt; }
    public static class Task { public long id; public String title; public String area; public int priority; public long dueAt; public int done; public long createdAt; }
    public static class Memory { public long id; public String text; public String tags; public long createdAt; }
    public static class DailyState { public String date; public int mood; public int energy; public int stress; public float sleep; public String note; }
    public static class Message { public long id; public String role; public String text; public long createdAt; }

    public SoheilDb(Context c, SoheilCrypto crypto) {
        super(c, DB_NAME, null, DB_VERSION);
        this.crypto = crypto;
        SQLiteDatabase db = getWritableDatabase();
        migrateLegacyPlaintext(db);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.execSQL("PRAGMA secure_delete=ON");
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, type TEXT DEFAULT 'INBOX', status INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, area TEXT DEFAULT '', priority INTEGER DEFAULT 2, due_at INTEGER DEFAULT 0, done INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, tags TEXT DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE daily_state_secure(day_key TEXT PRIMARY KEY, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE security_meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE access_audit(id INTEGER PRIMARY KEY AUTOINCREMENT, domain TEXT NOT NULL, purpose TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS daily_state_secure(day_key TEXT PRIMARY KEY, payload TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS security_meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS access_audit(id INTEGER PRIMARY KEY AUTOINCREMENT, domain TEXT NOT NULL, purpose TEXT NOT NULL, created_at INTEGER NOT NULL)");
        }
    }

    private boolean tableExists(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    private String meta(SQLiteDatabase db, String key) {
        Cursor c = db.rawQuery("SELECT v FROM security_meta WHERE k=?", new String[]{key});
        String out = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return out;
    }

    private void setMeta(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues(); v.put("k", key); v.put("v", value);
        db.insertWithOnConflict("security_meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void migrateLegacyPlaintext(SQLiteDatabase db) {
        if (!crypto.isUnlocked()) throw new SecurityException("Vault must be unlocked before DB migration");
        if ("1".equals(meta(db, "field_encryption_v1"))) return;

        db.beginTransaction();
        try {
            encryptColumn(db, "captures", "id", "text");
            encryptColumn(db, "tasks", "id", "title");
            encryptColumn(db, "tasks", "id", "area");
            encryptColumn(db, "memories", "id", "text");
            encryptColumn(db, "memories", "id", "tags");
            encryptColumn(db, "messages", "id", "text");

            if (tableExists(db, "daily_state")) {
                Cursor c = db.rawQuery("SELECT date,mood,energy,stress,sleep,note FROM daily_state", null);
                while (c.moveToNext()) {
                    DailyState d = new DailyState();
                    d.date = c.getString(0); d.mood = c.getInt(1); d.energy = c.getInt(2);
                    d.stress = c.getInt(3); d.sleep = c.getFloat(4); d.note = c.getString(5);
                    putDailyState(db, d.date, d.mood, d.energy, d.stress, d.sleep, d.note);
                }
                c.close();
                db.execSQL("DROP TABLE daily_state");
            }
            setMeta(db, "field_encryption_v1", "1");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // Rewrite free pages after plaintext-to-ciphertext migration.
        try { db.execSQL("VACUUM"); } catch (Exception ignored) {}
    }

    private void encryptColumn(SQLiteDatabase db, String table, String idCol, String valueCol) {
        Cursor c = db.rawQuery("SELECT " + idCol + "," + valueCol + " FROM " + table, null);
        while (c.moveToNext()) {
            long id = c.getLong(0); String raw = c.getString(1);
            if (raw != null && !crypto.isEncrypted(raw)) {
                ContentValues v = new ContentValues(); v.put(valueCol, crypto.encrypt(raw));
                db.update(table, v, idCol + "=?", new String[]{String.valueOf(id)});
            }
        }
        c.close();
    }

    public long addCapture(String text) {
        ContentValues v=new ContentValues(); v.put("text",crypto.encrypt(text)); v.put("type","INBOX");
        v.put("status",0); v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("captures",null,v);
    }

    public List<Capture> getInbox(int limit) {
        List<Capture> out=new ArrayList<>();
        Cursor c=getReadableDatabase().rawQuery("SELECT id,text,type,status,created_at FROM captures WHERE status=0 ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});
        while(c.moveToNext()){Capture x=new Capture();x.id=c.getLong(0);x.text=crypto.decrypt(c.getString(1));x.type=c.getString(2);x.status=c.getInt(3);x.createdAt=c.getLong(4);out.add(x);}c.close();return out;
    }

    public Capture latestCapture() {
        Cursor c=getReadableDatabase().rawQuery("SELECT id,text,type,status,created_at FROM captures ORDER BY created_at DESC LIMIT 1",null);Capture x=null;
        if(c.moveToFirst()){x=new Capture();x.id=c.getLong(0);x.text=crypto.decrypt(c.getString(1));x.type=c.getString(2);x.status=c.getInt(3);x.createdAt=c.getLong(4);}c.close();return x;
    }

    public int inboxCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM captures WHERE status=0",null);int n=0;if(c.moveToFirst())n=c.getInt(0);c.close();return n;}
    public void markCapture(long id,String type,int status){ContentValues v=new ContentValues();v.put("type",type);v.put("status",status);getWritableDatabase().update("captures",v,"id=?",new String[]{String.valueOf(id)});}
    public long captureToTask(Capture capture){long id=addTask(capture.text,"شخصی",2,0);markCapture(capture.id,"TASK",1);return id;}
    public long captureToMemory(Capture capture){long id=addMemory(capture.text,"inbox");markCapture(capture.id,"MEMORY",1);return id;}

    public long addTask(String title,String area,int priority,long dueAt){ContentValues v=new ContentValues();v.put("title",crypto.encrypt(title));v.put("area",crypto.encrypt(area));v.put("priority",priority);v.put("due_at",dueAt);v.put("done",0);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("tasks",null,v);}
    public List<Task> getOpenTasks(int limit){List<Task> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,title,area,priority,due_at,done,created_at FROM tasks WHERE done=0 ORDER BY CASE WHEN due_at>0 THEN 0 ELSE 1 END, due_at ASC, priority ASC, created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Task t=new Task();t.id=c.getLong(0);t.title=crypto.decrypt(c.getString(1));t.area=crypto.decrypt(c.getString(2));t.priority=c.getInt(3);t.dueAt=c.getLong(4);t.done=c.getInt(5);t.createdAt=c.getLong(6);out.add(t);}c.close();return out;}
    public int openTaskCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tasks WHERE done=0",null);int n=0;if(c.moveToFirst())n=c.getInt(0);c.close();return n;}
    public void completeTask(long id){ContentValues v=new ContentValues();v.put("done",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});}

    public long addMemory(String text,String tags){ContentValues v=new ContentValues();v.put("text",crypto.encrypt(text));v.put("tags",crypto.encrypt(tags));v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("memories",null,v);}
    public List<Memory> getRecentMemories(int limit){List<Memory> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,text,tags,created_at FROM memories ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Memory m=new Memory();m.id=c.getLong(0);m.text=crypto.decrypt(c.getString(1));m.tags=crypto.decrypt(c.getString(2));m.createdAt=c.getLong(3);out.add(m);}c.close();return out;}

    /** Search happens after decryption in memory so SQLite never receives plaintext search terms. */
    public List<Memory> searchMemories(String query,int limit){List<Memory> all=getRecentMemories(200);List<Memory> out=new ArrayList<>();String q=query==null?"":query.toLowerCase(Locale.ROOT);for(Memory m:all){if(m.text.toLowerCase(Locale.ROOT).contains(q)||m.tags.toLowerCase(Locale.ROOT).contains(q)){out.add(m);if(out.size()>=limit)break;}}return out;}

    private void putDailyState(SQLiteDatabase db,String date,int mood,int energy,int stress,float sleep,String note){
        try {
            JSONObject j=new JSONObject();j.put("date",date);j.put("mood",mood);j.put("energy",energy);j.put("stress",stress);j.put("sleep",sleep);j.put("note",note==null?"":note);
            ContentValues v=new ContentValues();v.put("day_key",dayKey(date));v.put("payload",crypto.encrypt(j.toString()));
            db.insertWithOnConflict("daily_state_secure",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        } catch(Exception e){throw new SecurityException("Daily state encryption failed",e);}
    }

    public void saveDailyState(int mood,int energy,int stress,float sleep,String note){putDailyState(getWritableDatabase(),todayKey(),mood,energy,stress,sleep,note);}
    public DailyState todayState(){
        Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM daily_state_secure WHERE day_key=?",new String[]{dayKey(todayKey())});DailyState d=null;
        if(c.moveToFirst())try{JSONObject j=new JSONObject(crypto.decrypt(c.getString(0)));d=new DailyState();d.date=j.optString("date",todayKey());d.mood=j.optInt("mood",3);d.energy=j.optInt("energy",3);d.stress=j.optInt("stress",3);d.sleep=(float)j.optDouble("sleep",0);d.note=j.optString("note","");}catch(Exception e){c.close();throw new SecurityException("Daily state integrity check failed",e);}c.close();return d;
    }

    public long addMessage(String role,String text){ContentValues v=new ContentValues();v.put("role",role);v.put("text",crypto.encrypt(text));v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("messages",null,v);}
    public List<Message> recentMessages(int limit){List<Message> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,role,text,created_at FROM messages ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(limit)});while(c.moveToNext()){Message m=new Message();m.id=c.getLong(0);m.role=c.getString(1);m.text=crypto.decrypt(c.getString(2));m.createdAt=c.getLong(3);out.add(0,m);}c.close();return out;}

    public void auditAccess(String domain,String purpose){ContentValues v=new ContentValues();v.put("domain",crypto.encrypt(domain));v.put("purpose",crypto.encrypt(purpose));v.put("created_at",System.currentTimeMillis());getWritableDatabase().insert("access_audit",null,v);}

    public void closeSecurely(){close();}

    public static String todayKey(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

    private static String dayKey(String date){
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] d=md.digest(date.getBytes(StandardCharsets.UTF_8));return Base64.encodeToString(d,Base64.NO_WRAP|Base64.NO_PADDING);}catch(Exception e){throw new IllegalStateException(e);}
    }
}
