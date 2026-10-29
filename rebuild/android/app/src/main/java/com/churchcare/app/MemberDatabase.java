package com.churchcare.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Offline-first ChurchCare database. SQLite remains authoritative on this device.
 * Schema changes are additive so an upgrade never intentionally clears local records.
 */
public final class MemberDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "churchcare_local.db";
    private static final int DB_VERSION = 3;
    private static final Set<String> TABLES = new HashSet<>(Arrays.asList(
        "members", "organizations", "appointments", "delegations", "visits", "care_records"
    ));

    public MemberDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createBaseTables(db);
        createDomainTables(db);
    }

    private void createBaseTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS members (id TEXT PRIMARY KEY NOT NULL, full_name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', organization TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'Active', organization_id TEXT, profile_photo_path TEXT NOT NULL DEFAULT '', extra_photo_one_path TEXT NOT NULL DEFAULT '', extra_photo_two_path TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY', deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_members_name ON members(full_name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS change_log (sequence INTEGER PRIMARY KEY AUTOINCREMENT, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, operation TEXT NOT NULL, created_at INTEGER NOT NULL, sync_state TEXT NOT NULL DEFAULT 'PENDING', last_error TEXT NOT NULL DEFAULT '')");
    }

    private void createDomainTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS organizations (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'Church', parent_id TEXT, location TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_parent ON organizations(parent_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS appointments (id TEXT PRIMARY KEY NOT NULL, person_name TEXT NOT NULL, role TEXT NOT NULL, organization_id TEXT, start_date TEXT NOT NULL DEFAULT '', end_date TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'Current', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_appointments_person ON appointments(person_name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS delegations (id TEXT PRIMARY KEY NOT NULL, overseer TEXT NOT NULL, delegate_name TEXT NOT NULL, organization_id TEXT, scope TEXT NOT NULL DEFAULT 'View and record care', start_date TEXT NOT NULL DEFAULT '', end_date TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'Active', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY')");
        db.execSQL("CREATE TABLE IF NOT EXISTS visits (id TEXT PRIMARY KEY NOT NULL, member_id TEXT, member_name TEXT NOT NULL, visit_date TEXT NOT NULL, visitor TEXT NOT NULL DEFAULT '', outcome TEXT NOT NULL DEFAULT '', next_follow_up TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_visits_member_date ON visits(member_id, visit_date DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS care_records (id TEXT PRIMARY KEY NOT NULL, member_id TEXT, member_name TEXT NOT NULL, category TEXT NOT NULL DEFAULT 'General', priority TEXT NOT NULL DEFAULT 'Normal', status TEXT NOT NULL DEFAULT 'Open', assigned_to TEXT NOT NULL DEFAULT '', opened_date TEXT NOT NULL, due_date TEXT NOT NULL DEFAULT '', details TEXT NOT NULL DEFAULT '', resolution TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_care_status ON care_records(status, priority)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 had a smaller members table. Add columns without deleting user data.
        if (oldVersion < 2) {
            addColumnIfMissing(db, "members", "address", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(db, "members", "status", "TEXT NOT NULL DEFAULT 'Active'");
            addColumnIfMissing(db, "members", "organization_id", "TEXT");
            addColumnIfMissing(db, "members", "profile_photo_path", "TEXT NOT NULL DEFAULT ''");
            createBaseTables(db);
            createDomainTables(db);
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, "members", "extra_photo_one_path", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(db, "members", "extra_photo_two_path", "TEXT NOT NULL DEFAULT ''");
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String type) {
        boolean found = false;
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) if (column.equals(c.getString(c.getColumnIndexOrThrow("name")))) found = true;
        }
        if (!found) db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    private void logChange(SQLiteDatabase db, String entityType, String id, String operation, long now) {
        ContentValues change = new ContentValues();
        change.put("entity_type", entityType); change.put("entity_id", id);
        change.put("operation", operation); change.put("created_at", now);
        change.put("sync_state", "PENDING"); change.put("last_error", "");
        db.insertOrThrow("change_log", null, change);
    }

    public String addMember(String name, String phone, String organization) {
        ContentValues v = new ContentValues();
        v.put("full_name", name); v.put("phone", phone); v.put("organization", organization);
        return insertRecord("members", v);
    }

    public String insertRecord(String table, ContentValues values) {
        checkTable(table);
        SQLiteDatabase db = getWritableDatabase();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        values.put("id", id); values.put("created_at", now); values.put("updated_at", now);
        values.put("deleted", 0); values.put("sync_state", "LOCAL_ONLY");
        db.beginTransaction();
        try {
            db.insertOrThrow(table, null, values);
            logChange(db, table.equals("care_records") ? "care" : table.substring(0, table.length() - (table.endsWith("s") ? 1 : 0)), id, "CREATE", now);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return id;
    }

    public void updateMember(String id, String name, String phone, String organization, String address, String status) {
        ContentValues v = new ContentValues();
        v.put("full_name", name); v.put("phone", phone); v.put("organization", organization);
        v.put("address", address); v.put("status", status);
        updateRecord("members", id, v);
    }

    public void updateRecord(String table, String id, ContentValues values) {
        checkTable(table);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis(); values.put("updated_at", now);
        db.beginTransaction();
        try {
            db.update(table, values, "id=? AND deleted=0", new String[]{id});
            logChange(db, table.equals("care_records") ? "care" : table.substring(0, table.length() - (table.endsWith("s") ? 1 : 0)), id, "UPDATE", now);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void softDelete(String table, String id) {
        checkTable(table);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues(); v.put("deleted", 1); v.put("updated_at", now); v.put("sync_state", "PENDING_DELETE");
        db.beginTransaction();
        try {
            db.update(table, v, "id=?", new String[]{id});
            logChange(db, table.equals("care_records") ? "care" : table.substring(0, table.length() - (table.endsWith("s") ? 1 : 0)), id, "DELETE", now);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public Cursor listMembers() {
        return getReadableDatabase().query("members",
            new String[]{"id","full_name","phone","organization","address","status","organization_id","profile_photo_path","extra_photo_one_path","extra_photo_two_path","created_at","updated_at"},
            "deleted=0", null, null, null, "full_name COLLATE NOCASE ASC");
    }

    public Cursor listRecords(String table) {
        checkTable(table);
        String order = table.equals("organizations") ? "name COLLATE NOCASE ASC"
            : table.equals("appointments") ? "person_name COLLATE NOCASE ASC"
            : table.equals("delegations") ? "overseer COLLATE NOCASE ASC"
            : table.equals("visits") ? "visit_date DESC, created_at DESC"
            : table.equals("care_records") ? "CASE priority WHEN 'Urgent' THEN 0 WHEN 'High' THEN 1 ELSE 2 END, opened_date DESC"
            : "full_name COLLATE NOCASE ASC";
        return getReadableDatabase().query(table, null, "deleted=0", null, null, null, order);
    }

    public Cursor searchMembers(String query) {
        String q = "%" + query.trim() + "%";
        return getReadableDatabase().query("members", null,
            "deleted=0 AND (full_name LIKE ? OR phone LIKE ? OR organization LIKE ? OR address LIKE ?)",
            new String[]{q,q,q,q}, null, null, "full_name COLLATE NOCASE ASC");
    }

    public int count(String table) {
        checkTable(table);
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table + " WHERE deleted=0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int pendingChanges() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM change_log WHERE sync_state IN ('PENDING','FAILED')", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }


    public String findIdByName(String table, String nameColumn, String name) {
        checkTable(table);
        if (!Arrays.asList("name", "full_name", "person_name", "member_name").contains(nameColumn)) return null;
        try (Cursor c = getReadableDatabase().query(table, new String[]{"id"}, "deleted=0 AND " + nameColumn + "=? COLLATE NOCASE", new String[]{name.trim()}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** Keep no more than five visit records per member, recording removals for later sync. */
    public void trimVisits(String memberName) {
        SQLiteDatabase db = getWritableDatabase();
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id FROM visits WHERE deleted=0 AND member_name=? COLLATE NOCASE ORDER BY visit_date DESC, created_at DESC", new String[]{memberName.trim()})) {
            int index = 0;
            while (c.moveToNext()) if (index++ >= 5) ids.add(c.getString(0));
        }
        for (String id : ids) softDelete("visits", id);
    }

    public String findNameById(String table, String id) {
        checkTable(table);
        String column = table.equals("organizations") ? "name" : table.equals("members") ? "full_name" : "id";
        try (Cursor c = getReadableDatabase().query(table, new String[]{column}, "id=? AND deleted=0", new String[]{id}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    public Cursor syncQueue() {
        return getReadableDatabase().rawQuery("SELECT sequence,entity_type,operation,created_at,sync_state,last_error FROM change_log ORDER BY sequence DESC LIMIT 100", null);
    }

    private void checkTable(String table) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported ChurchCare table");
    }
}
