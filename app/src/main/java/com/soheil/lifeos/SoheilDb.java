package com.soheil.lifeos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SOHEIL encrypted record vault.
 * All security-relevant record state lives inside AES-GCM/AAD authenticated payloads.
 * SQLite retains only minimal routing metadata (kind, opaque record key, timestamp).
 */
public class SoheilDb extends SQLiteOpenHelper {
    private static final String DB_NAME="soheil_v03.db";
    private static final int DB_VERSION=3;
    private static final String TABLE="vault_records";
    private final SoheilCrypto crypto;

    private static final String CAPTURE="C", TASK="T", MEMORY="M", DAILY="D", MESSAGE="G", AUDIT="A";

    public static class Capture { public long id; public String text; public String type; public int status; public long createdAt; }
    public static class Task { public long id; public String title; public String area; public int priority; public long dueAt; public int done; public long createdAt; }
    public static class Memory { public long id; public String text; public String tags; public long createdAt; }
    public static class DailyState { public String date; public int mood; public int energy; public int stress; public float sleep; public String note; }
    public static class Message { public long id; public String role; public String text; public long createdAt; }

    private static class VaultRow { long id; String kind; String key; String payload; long createdAt; }

    public SoheilDb(Context c,SoheilCrypto crypto){super(c,DB_NAME,null,DB_VERSION);this.crypto=crypto;SQLiteDatabase db=getWritableDatabase();migrateLegacyTables(db);}

    @Override public void onConfigure(SQLiteDatabase db){super.onConfigure(db);db.execSQL("PRAGMA secure_delete=ON");db.execSQL("PRAGMA foreign_keys=ON");}

    @Override public void onCreate(SQLiteDatabase db){createVaultTables(db);}

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){createVaultTables(db);}

    private void createVaultTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS vault_records(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, record_key TEXT NOT NULL, payload TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(kind,record_key) ON CONFLICT REPLACE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_kind_created ON vault_records(kind,created_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS security_meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)");
    }

    private boolean tableExists(SQLiteDatabase db,String table){Cursor c=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",new String[]{table});boolean ok=c.moveToFirst();c.close();return ok;}
    private String meta(SQLiteDatabase db,String key){Cursor c=db.rawQuery("SELECT v FROM security_meta WHERE k=?",new String[]{key});String v=c.moveToFirst()?c.getString(0):null;c.close();return v;}
    private void setMeta(SQLiteDatabase db,String key,String value){ContentValues v=new ContentValues();v.put("k",key);v.put("v",value);db.insertWithOnConflict("security_meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);}

    private String aad(String kind,String key,long createdAt){return"record:"+kind+":"+key+":"+createdAt;}

    private long insertRecord(SQLiteDatabase db,String kind,String recordKey,JSONObject json,long createdAt){
        try{
            json.put("_kind",kind);json.put("_key",recordKey);json.put("_createdAt",createdAt);
            ContentValues v=new ContentValues();v.put("kind",kind);v.put("record_key",recordKey);v.put("created_at",createdAt);v.put("payload",crypto.encryptFor(aad(kind,recordKey,createdAt),json.toString()));
            return db.insertWithOnConflict(TABLE,null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Exception e){throw new SecurityException("Vault record write failed",e);}
    }

    private long insertRecord(String kind,JSONObject json,long createdAt){return insertRecord(getWritableDatabase(),kind,UUID.randomUUID().toString(),json,createdAt);}

    private VaultRow rowFromCursor(Cursor c){VaultRow r=new VaultRow();r.id=c.getLong(0);r.kind=c.getString(1);r.key=c.getString(2);r.payload=c.getString(3);r.createdAt=c.getLong(4);return r;}

    private JSONObject decryptRow(VaultRow r){
        try{
            JSONObject j=new JSONObject(crypto.decryptFor(aad(r.kind,r.key,r.createdAt),r.payload));
            if(!r.kind.equals(j.optString("_kind"))||!r.key.equals(j.optString("_key"))||r.createdAt!=j.optLong("_createdAt",-1))throw new SecurityException("Vault metadata mismatch");
            return j;
        }catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("Vault record parse/integrity failure",e);}
    }

    private VaultRow getRow(long id,String expectedKind){Cursor c=getReadableDatabase().rawQuery("SELECT id,kind,record_key,payload,created_at FROM vault_records WHERE id=? AND kind=?",new String[]{String.valueOf(id),expectedKind});VaultRow r=c.moveToFirst()?rowFromCursor(c):null;c.close();return r;}

    private void updateRow(VaultRow r,JSONObject j){
        try{
            j.put("_kind",r.kind);j.put("_key",r.key);j.put("_createdAt",r.createdAt);
            ContentValues v=new ContentValues();v.put("payload",crypto.encryptFor(aad(r.kind,r.key,r.createdAt),j.toString()));
            if(getWritableDatabase().update(TABLE,v,"id=? AND kind=?",new String[]{String.valueOf(r.id),r.kind})!=1)throw new SecurityException("Vault record update lost");
        }catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("Vault record update failed",e);}
    }

    private List<VaultRow> rows(String kind,int max){List<VaultRow> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,kind,record_key,payload,created_at FROM vault_records WHERE kind=? ORDER BY created_at DESC LIMIT ?",new String[]{kind,String.valueOf(max)});while(c.moveToNext())out.add(rowFromCursor(c));c.close();return out;}

    private String legacyText(String value){if(value==null)return"";if(!crypto.isEncrypted(value))return value;return crypto.decrypt(value);}

    /** Migrates v0.3 plaintext and intermediate security schemas, then drops legacy tables. */
    private void migrateLegacyTables(SQLiteDatabase db){
        if(!crypto.isUnlocked())throw new SecurityException("Vault must be unlocked before migration");
        boolean legacy=tableExists(db,"captures")||tableExists(db,"tasks")||tableExists(db,"memories")||tableExists(db,"messages")||tableExists(db,"daily_state")||tableExists(db,"daily_state_secure")||tableExists(db,"access_audit");
        if(!legacy&&"1".equals(meta(db,"record_vault_v1")))return;
        db.beginTransaction();
        try{
            if(tableExists(db,"captures")){Cursor c=db.rawQuery("SELECT id,text,type,status,created_at FROM captures",null);while(c.moveToNext())try{JSONObject j=new JSONObject();j.put("text",legacyText(c.getString(1)));j.put("type",c.getString(2));j.put("status",c.getInt(3));j.put("createdAt",c.getLong(4));insertRecord(db,CAPTURE,"legacy-c-"+c.getLong(0),j,c.getLong(4));}catch(Exception e){throw new SecurityException(e);}c.close();}
            if(tableExists(db,"tasks")){Cursor c=db.rawQuery("SELECT id,title,area,priority,due_at,done,created_at FROM tasks",null);while(c.moveToNext())try{JSONObject j=new JSONObject();j.put("title",legacyText(c.getString(1)));j.put("area",legacyText(c.getString(2)));j.put("priority",c.getInt(3));j.put("dueAt",c.getLong(4));j.put("done",c.getInt(5));j.put("createdAt",c.getLong(6));insertRecord(db,TASK,"legacy-t-"+c.getLong(0),j,c.getLong(6));}catch(Exception e){throw new SecurityException(e);}c.close();}
            if(tableExists(db,"memories")){Cursor c=db.rawQuery("SELECT id,text,tags,created_at FROM memories",null);while(c.moveToNext())try{JSONObject j=new JSONObject();j.put("text",legacyText(c.getString(1)));j.put("tags",legacyText(c.getString(2)));j.put("createdAt",c.getLong(3));insertRecord(db,MEMORY,"legacy-m-"+c.getLong(0),j,c.getLong(3));}catch(Exception e){throw new SecurityException(e);}c.close();}
            if(tableExists(db,"messages")){Cursor c=db.rawQuery("SELECT id,role,text,created_at FROM messages",null);while(c.moveToNext())try{JSONObject j=new JSONObject();j.put("role",c.getString(1));j.put("text",legacyText(c.getString(2)));j.put("createdAt",c.getLong(3));insertRecord(db,MESSAGE,"legacy-g-"+c.getLong(0),j,c.getLong(3));}catch(Exception e){throw new SecurityException(e);}c.close();}
            if(tableExists(db,"daily_state")){Cursor c=db.rawQuery("SELECT date,mood,energy,stress,sleep,note FROM daily_state",null);while(c.moveToNext())putDailyState(db,c.getString(0),c.getInt(1),c.getInt(2),c.getInt(3),c.getFloat(4),c.getString(5));c.close();}
            if(tableExists(db,"daily_state_secure")){Cursor c=db.rawQuery("SELECT payload FROM daily_state_secure",null);while(c.moveToNext())try{JSONObject old=new JSONObject(legacyText(c.getString(0)));putDailyState(db,old.optString("date"),old.optInt("mood",3),old.optInt("energy",3),old.optInt("stress",3),(float)old.optDouble("sleep",0),old.optString("note",""));}catch(Exception e){throw new SecurityException(e);}c.close();}
            if(tableExists(db,"access_audit")){Cursor c=db.rawQuery("SELECT id,domain,purpose,created_at FROM access_audit",null);while(c.moveToNext())try{JSONObject j=new JSONObject();j.put("domain",legacyText(c.getString(1)));j.put("purpose",legacyText(c.getString(2)));j.put("createdAt",c.getLong(3));insertRecord(db,AUDIT,"legacy-a-"+c.getLong(0),j,c.getLong(3));}catch(Exception e){throw new SecurityException(e);}c.close();}

            drop(db,"captures");drop(db,"tasks");drop(db,"memories");drop(db,"messages");drop(db,"daily_state");drop(db,"daily_state_secure");drop(db,"access_audit");
            setMeta(db,"record_vault_v1","1");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        try{db.execSQL("VACUUM");}catch(Exception ignored){}
    }

    private void drop(SQLiteDatabase db,String table){if(tableExists(db,table))db.execSQL("DROP TABLE "+table);}

    public long addCapture(String text){long now=System.currentTimeMillis();try{JSONObject j=new JSONObject();j.put("text",text);j.put("type","INBOX");j.put("status",0);j.put("createdAt",now);return insertRecord(CAPTURE,j,now);}catch(Exception e){throw new SecurityException(e);}}
    public List<Capture> getInbox(int limit){List<Capture>out=new ArrayList<>();for(VaultRow r:rows(CAPTURE,2000)){JSONObject j=decryptRow(r);if(j.optInt("status",0)!=0)continue;Capture x=new Capture();x.id=r.id;x.text=j.optString("text","");x.type=j.optString("type","INBOX");x.status=j.optInt("status",0);x.createdAt=j.optLong("createdAt",r.createdAt);out.add(x);if(out.size()>=limit)break;}return out;}
    public Capture latestCapture(){List<VaultRow>rs=rows(CAPTURE,1);if(rs.isEmpty())return null;VaultRow r=rs.get(0);JSONObject j=decryptRow(r);Capture x=new Capture();x.id=r.id;x.text=j.optString("text","");x.type=j.optString("type","INBOX");x.status=j.optInt("status",0);x.createdAt=j.optLong("createdAt",r.createdAt);return x;}
    public int inboxCount(){int n=0;for(VaultRow r:rows(CAPTURE,5000))if(decryptRow(r).optInt("status",0)==0)n++;return n;}
    public void markCapture(long id,String type,int status){VaultRow r=getRow(id,CAPTURE);if(r==null)return;JSONObject j=decryptRow(r);try{j.put("type",type);j.put("status",status);}catch(Exception e){throw new SecurityException(e);}updateRow(r,j);}
    public long captureToTask(Capture c){long id=addTask(c.text,"شخصی",2,0);markCapture(c.id,"TASK",1);return id;}
    public long captureToMemory(Capture c){long id=addMemory(c.text,"inbox");markCapture(c.id,"MEMORY",1);return id;}

    public long addTask(String title,String area,int priority,long dueAt){long now=System.currentTimeMillis();try{JSONObject j=new JSONObject();j.put("title",title);j.put("area",area);j.put("priority",priority);j.put("dueAt",dueAt);j.put("done",0);j.put("createdAt",now);return insertRecord(TASK,j,now);}catch(Exception e){throw new SecurityException(e);}}
    public List<Task> getOpenTasks(int limit){List<Task>out=new ArrayList<>();for(VaultRow r:rows(TASK,5000)){JSONObject j=decryptRow(r);if(j.optInt("done",0)!=0)continue;Task t=new Task();t.id=r.id;t.title=j.optString("title","");t.area=j.optString("area","شخصی");t.priority=j.optInt("priority",2);t.dueAt=j.optLong("dueAt",0);t.done=j.optInt("done",0);t.createdAt=j.optLong("createdAt",r.createdAt);out.add(t);}Collections.sort(out,new Comparator<Task>(){public int compare(Task a,Task b){boolean ad=a.dueAt>0,bd=b.dueAt>0;if(ad!=bd)return ad?-1:1;if(ad&&a.dueAt!=b.dueAt)return Long.compare(a.dueAt,b.dueAt);if(a.priority!=b.priority)return Integer.compare(a.priority,b.priority);return Long.compare(b.createdAt,a.createdAt);}});if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));return out;}
    public int openTaskCount(){int n=0;for(VaultRow r:rows(TASK,5000))if(decryptRow(r).optInt("done",0)==0)n++;return n;}
    public void completeTask(long id){VaultRow r=getRow(id,TASK);if(r==null)return;JSONObject j=decryptRow(r);try{j.put("done",1);}catch(Exception e){throw new SecurityException(e);}updateRow(r,j);}

    public long addMemory(String text,String tags){long now=System.currentTimeMillis();try{JSONObject j=new JSONObject();j.put("text",text);j.put("tags",tags);j.put("createdAt",now);return insertRecord(MEMORY,j,now);}catch(Exception e){throw new SecurityException(e);}}
    public List<Memory> getRecentMemories(int limit){List<Memory>out=new ArrayList<>();for(VaultRow r:rows(MEMORY,limit)){JSONObject j=decryptRow(r);Memory m=new Memory();m.id=r.id;m.text=j.optString("text","");m.tags=j.optString("tags","");m.createdAt=j.optLong("createdAt",r.createdAt);out.add(m);}return out;}
    public List<Memory> searchMemories(String query,int limit){String q=query==null?"":query.toLowerCase(Locale.ROOT);List<Memory>out=new ArrayList<>();for(VaultRow r:rows(MEMORY,1000)){JSONObject j=decryptRow(r);String text=j.optString("text","");String tags=j.optString("tags","");if(text.toLowerCase(Locale.ROOT).contains(q)||tags.toLowerCase(Locale.ROOT).contains(q)){Memory m=new Memory();m.id=r.id;m.text=text;m.tags=tags;m.createdAt=j.optLong("createdAt",r.createdAt);out.add(m);if(out.size()>=limit)break;}}return out;}

    private void putDailyState(SQLiteDatabase db,String date,int mood,int energy,int stress,float sleep,String note){if(date==null||date.isEmpty())date=todayKey();long now=System.currentTimeMillis();try{JSONObject j=new JSONObject();j.put("date",date);j.put("mood",mood);j.put("energy",energy);j.put("stress",stress);j.put("sleep",sleep);j.put("note",note==null?"":note);j.put("createdAt",now);insertRecord(db,DAILY,crypto.blindIndex("daily-date",date),j,now);}catch(Exception e){throw new SecurityException(e);}}
    public void saveDailyState(int mood,int energy,int stress,float sleep,String note){putDailyState(getWritableDatabase(),todayKey(),mood,energy,stress,sleep,note);}
    public DailyState todayState(){String key=crypto.blindIndex("daily-date",todayKey());Cursor c=getReadableDatabase().rawQuery("SELECT id,kind,record_key,payload,created_at FROM vault_records WHERE kind=? AND record_key=? LIMIT 1",new String[]{DAILY,key});if(!c.moveToFirst()){c.close();return null;}VaultRow r=rowFromCursor(c);c.close();JSONObject j=decryptRow(r);DailyState d=new DailyState();d.date=j.optString("date",todayKey());d.mood=j.optInt("mood",3);d.energy=j.optInt("energy",3);d.stress=j.optInt("stress",3);d.sleep=(float)j.optDouble("sleep",0);d.note=j.optString("note","");return d;}

    public long addMessage(String role,String text){long now=System.currentTimeMillis();try{JSONObject j=new JSONObject();j.put("role",role);j.put("text",text);j.put("createdAt",now);return insertRecord(MESSAGE,j,now);}catch(Exception e){throw new SecurityException(e);}}
    public List<Message> recentMessages(int limit){List<Message>out=new ArrayList<>();for(VaultRow r:rows(MESSAGE,limit)){JSONObject j=decryptRow(r);Message m=new Message();m.id=r.id;m.role=j.optString("role","");m.text=j.optString("text","");m.createdAt=j.optLong("createdAt",r.createdAt);out.add(0,m);}return out;}

    public void auditAccess(String domain,String purpose){long now=System.currentTimeMillis();try{JSONObject j=new JSONObject();j.put("domain",domain);j.put("purpose",purpose);j.put("createdAt",now);insertRecord(AUDIT,j,now);}catch(Exception e){throw new SecurityException(e);}pruneAudit(500);}
    private void pruneAudit(int keep){List<VaultRow>rs=rows(AUDIT,5000);if(rs.size()<=keep)return;SQLiteDatabase db=getWritableDatabase();for(int i=keep;i<rs.size();i++)db.delete(TABLE,"id=? AND kind=?",new String[]{String.valueOf(rs.get(i).id),AUDIT});}

    public void closeSecurely(){close();}
    public static String todayKey(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new java.util.Date());}
}
