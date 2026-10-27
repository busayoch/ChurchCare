package com.churchcare.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.UUID;

/** Local-first member storage. Cloud synchronization is intentionally not enabled in this first foundation milestone. */
public final class MemberDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "churchcare_local.db";
    private static final int DB_VERSION = 1;

    public MemberDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); setWriteAheadLoggingEnabled(true); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE members (id TEXT PRIMARY KEY NOT NULL, full_name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', organization TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY', deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_members_name ON members(full_name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE change_log (sequence INTEGER PRIMARY KEY AUTOINCREMENT, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, operation TEXT NOT NULL, created_at INTEGER NOT NULL, sync_state TEXT NOT NULL DEFAULT 'PENDING', last_error TEXT NOT NULL DEFAULT '')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future upgrades must be additive migrations; never drop member data here.
        if (oldVersion < 2 && newVersion >= 2) {
            // Reserved for the next schema migration. No destructive action is needed for version 1.
        }
    }

    public long addMember(String name, String phone, String organization) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id); values.put("full_name", name); values.put("phone", phone); values.put("organization", organization);
        values.put("created_at", now); values.put("updated_at", now); values.put("sync_state", "LOCAL_ONLY"); values.put("deleted", 0);
        db.beginTransaction();
        try {
            db.insertOrThrow("members", null, values);
            ContentValues change = new ContentValues(); change.put("entity_type", "member"); change.put("entity_id", id); change.put("operation", "CREATE"); change.put("created_at", now); change.put("sync_state", "PENDING");
            db.insertOrThrow("change_log", null, change);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return now;
    }

    public Cursor listMembers() {
        return getReadableDatabase().query("members", new String[]{"id", "full_name", "phone", "organization", "created_at", "updated_at"}, "deleted=0", null, null, null, "full_name COLLATE NOCASE ASC");
    }
}
