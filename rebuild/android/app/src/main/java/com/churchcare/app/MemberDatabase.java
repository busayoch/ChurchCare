package com.churchcare.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private static final int DB_VERSION = 5;
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
        createChurchProfileTable(db);
        addColumnIfMissing(db, "church_profile", "template_id", "TEXT NOT NULL DEFAULT 'custom'");
        addColumnIfMissing(db, "church_profile", "structure_json", "TEXT NOT NULL DEFAULT '[]'");
        addColumnIfMissing(db, "church_profile", "configuration_version", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(db, "church_profile", "template_version", "INTEGER NOT NULL DEFAULT 0");
        createConfigurationSuggestionsTable(db);
    }

    private void createChurchProfileTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS church_profile (id INTEGER PRIMARY KEY CHECK(id=1), church_id TEXT NOT NULL UNIQUE, church_name TEXT NOT NULL, local_unit_id TEXT NOT NULL, local_unit_name TEXT NOT NULL, local_level_name TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    }

    private void createConfigurationSuggestionsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS configuration_suggestions (id TEXT PRIMARY KEY NOT NULL, area TEXT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL DEFAULT '', proposed_by TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'PENDING', reviewer TEXT NOT NULL DEFAULT '', decision_notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_config_suggestions_status ON configuration_suggestions(status, created_at)");
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
        if (oldVersion < 4) createChurchProfileTable(db);
        if (oldVersion < 5) {
            addColumnIfMissing(db, "church_profile", "template_id", "TEXT NOT NULL DEFAULT 'custom'");
            addColumnIfMissing(db, "church_profile", "structure_json", "TEXT NOT NULL DEFAULT '[]'");
            addColumnIfMissing(db, "church_profile", "configuration_version", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(db, "church_profile", "template_version", "INTEGER NOT NULL DEFAULT 0");
            createConfigurationSuggestionsTable(db);
        }
    }

    /** A local ChurchCare installation belongs to one church and one starting branch/unit. */
    public boolean hasChurchSetup() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM church_profile WHERE id=1", null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }

    public String churchName() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT church_name FROM church_profile WHERE id=1", null)) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    public String localUnitName() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT local_unit_name FROM church_profile WHERE id=1", null)) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    /** Create the church identity, adopted template copy and first unit atomically. */
    public String saveChurchSetup(String churchName, String unitName, String levelName, String templateId, String structureJson, int templateVersion) {
        String church = churchName == null ? "" : churchName.trim();
        String unit = unitName == null ? "" : unitName.trim();
        String level = levelName == null ? "" : levelName.trim();
        if (church.isEmpty() || unit.isEmpty() || level.isEmpty()) {
            throw new IllegalArgumentException("Enter the church name, starting branch/unit name and its level.");
        }
        SQLiteDatabase db = getWritableDatabase();
        String churchId = UUID.randomUUID().toString();
        String unitId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            ContentValues branch = new ContentValues();
            branch.put("id", unitId); branch.put("name", unit); branch.put("type", level);
            branch.putNull("parent_id"); branch.put("location", ""); branch.put("notes", "");
            branch.put("created_at", now); branch.put("updated_at", now);
            branch.put("deleted", 0); branch.put("sync_state", "LOCAL_ONLY");
            db.insertOrThrow("organizations", null, branch);

            ContentValues profile = new ContentValues();
            profile.put("id", 1); profile.put("church_id", churchId); profile.put("church_name", church);
            profile.put("local_unit_id", unitId); profile.put("local_unit_name", unit);
            profile.put("local_level_name", level); profile.put("template_id", templateId == null ? "custom" : templateId);
            profile.put("structure_json", structureJson == null ? "[]" : structureJson);
            profile.put("configuration_version", 1); profile.put("template_version", templateVersion);
            profile.put("created_at", now); profile.put("updated_at", now);
            db.insertOrThrow("church_profile", null, profile);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return unitId;
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

    /** Reject a proposed parent if it is the organization itself or one of its descendants. */
    public boolean wouldCreateOrganizationCycle(String organizationId, String proposedParentId) {
        if (proposedParentId == null || proposedParentId.trim().isEmpty()) return false;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String current = proposedParentId;
        while (current != null && !current.trim().isEmpty()) {
            if (current.equals(organizationId)) return true;
            if (!seen.add(current)) return true; // Existing corruption: fail closed.
            try (Cursor c = getReadableDatabase().query("organizations", new String[]{"parent_id"},
                    "id=? AND deleted=0", new String[]{current}, null, null, null, "1")) {
                if (!c.moveToFirst() || c.isNull(0)) return false;
                current = c.getString(0);
            }
        }
        return false;
    }

    public String organizationPath(String id) {
        if (id == null || id.isEmpty()) return "";
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String current = id;
        while (current != null && !current.isEmpty() && seen.add(current) && names.size() < 20) {
            String parent = null;
            try (Cursor c = getReadableDatabase().query("organizations", new String[]{"name","parent_id"}, "id=? AND deleted=0", new String[]{current}, null, null, null, "1")) {
                if (!c.moveToFirst()) break;
                names.add(c.getString(0));
                parent = c.isNull(1) ? null : c.getString(1);
            }
            current = parent;
        }
        java.util.Collections.reverse(names);
        StringBuilder path = new StringBuilder();
        for (String name : names) { if (path.length() > 0) path.append(" › "); path.append(name); }
        return path.toString();
    }

    public void closeCurrentAppointments(String personName, String newStartDate) {
        SQLiteDatabase db = getWritableDatabase();
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        try (Cursor c = db.query("appointments", new String[]{"id"}, "deleted=0 AND person_name=? COLLATE NOCASE AND status='Current'", new String[]{personName.trim()}, null, null, null)) {
            while (c.moveToNext()) ids.add(c.getString(0));
        }
        for (String id : ids) {
            ContentValues v = new ContentValues(); v.put("end_date", newStartDate); v.put("status", "Completed");
            updateRecord("appointments", id, v);
        }
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

    /** Export a complete logical backup, including archived rows and the durable change log. */
    public JSONObject exportBackup() throws JSONException {
        SQLiteDatabase db = getReadableDatabase();
        JSONObject backup = new JSONObject();
        backup.put("format", "ChurchCare-Local-Backup");
        backup.put("schema_version", DB_VERSION);
        backup.put("exported_at", System.currentTimeMillis());
        JSONArray tables = new JSONArray();
        for (String table : new String[]{"members","organizations","appointments","delegations","visits","care_records","change_log","church_profile"}) {
            JSONObject tableObject = new JSONObject();
            tableObject.put("name", table);
            JSONArray rows = new JSONArray();
            try (Cursor c = db.query(table, null, null, null, null, null, null)) {
                while (c.moveToNext()) {
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String column = c.getColumnName(i);
                        switch (c.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER: row.put(column, c.getLong(i)); break;
                            case Cursor.FIELD_TYPE_FLOAT: row.put(column, c.getDouble(i)); break;
                            case Cursor.FIELD_TYPE_STRING: row.put(column, c.getString(i)); break;
                            default: row.put(column, JSONObject.NULL); break;
                        }
                    }
                    rows.put(row);
                }
            }
            tableObject.put("rows", rows);
            tables.put(tableObject);
        }
        backup.put("tables", tables);
        return backup;
    }

    /**
     * Restore a previously exported backup as one SQLite transaction.
     * The input is validated before any current records are removed.
     */
    public void restoreBackup(JSONObject backup) throws JSONException {
        if (!"ChurchCare-Local-Backup".equals(backup.optString("format"))
                || backup.optInt("schema_version", -1) < 1
                || !(backup.opt("tables") instanceof JSONArray)) {
            throw new JSONException("This is not a valid ChurchCare backup.");
        }
        JSONArray tableArray = backup.getJSONArray("tables");
        java.util.HashMap<String, JSONArray> incoming = new java.util.HashMap<>();
        for (int i = 0; i < tableArray.length(); i++) {
            JSONObject tableObject = tableArray.getJSONObject(i);
            String name = tableObject.optString("name", "");
            if (!Arrays.asList("members","organizations","appointments","delegations","visits","care_records","change_log","church_profile").contains(name)
                    || !(tableObject.opt("rows") instanceof JSONArray) || incoming.containsKey(name)) {
                throw new JSONException("The backup contains an invalid or duplicate table.");
            }
            incoming.put(name, tableObject.getJSONArray("rows"));
        }
        for (String required : new String[]{"members","organizations","appointments","delegations","visits","care_records","change_log"}) {
            if (!incoming.containsKey(required)) throw new JSONException("The backup is incomplete: " + required);
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : new String[]{"change_log","visits","care_records","appointments","delegations","members","organizations"}) {
                db.delete(table, null, null);
            }
            if (incoming.containsKey("church_profile")) db.delete("church_profile", null, null);
            for (String table : new String[]{"members","organizations","appointments","delegations","visits","care_records","change_log","church_profile"}) {
                if (!incoming.containsKey(table)) continue;
                JSONArray rows = incoming.get(table);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    java.util.Iterator<String> keys = row.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = row.get(key);
                        if (value == JSONObject.NULL) values.putNull(key);
                        else if (value instanceof String) values.put(key, (String)value);
                        else if (value instanceof Integer) values.put(key, (Integer)value);
                        else if (value instanceof Long) values.put(key, (Long)value);
                        else if (value instanceof Double) values.put(key, (Double)value);
                        else if (value instanceof Boolean) values.put(key, (Boolean)value);
                        else throw new JSONException("Unsupported value in backup.");
                    }
                    db.insertOrThrow(table, null, values);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void checkTable(String table) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported ChurchCare table");
    }
}
