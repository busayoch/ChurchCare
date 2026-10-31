package com.churchcare.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

public final class MainActivity extends Activity {
    private MemberDatabase database;
    private LinearLayout root;
    private int blue = 0xFF0B4F85, ink = 0xFF172B3A, muted = 0xFF526273, paper = 0xFFF4F7FA;
    private static final int PICK_MEMBER_PHOTO = 7104;
    private static final int EXPORT_BACKUP = 7105;
    private static final int IMPORT_BACKUP = 7106;
    private static final int DRIVE_AUTHORIZATION = 7107;
    private String pendingDriveAction = "";
    private String photoRecordId;
    private String photoColumn;

    private static final class Field {
        final String label, key; final int type; final boolean required;
        Field(String label, String key, int type, boolean required) { this.label=label; this.key=key; this.type=type; this.required=required; }
    }
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int PHONE = InputType.TYPE_CLASS_PHONE;
    private static final int DATE = InputType.TYPE_CLASS_TEXT;
    private static final Field[] MEMBER = {
        new Field("Full name", "full_name", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS, true),
        new Field("Phone number", "phone", PHONE, false),
        new Field("Church branch / unit", "organization", TEXT, false),
        new Field("Address / location", "address", TEXT, false),
        new Field("Status (Active / Inactive)", "status", TEXT, false)
    };
    private static final Field[] ORGANIZATION = {
        new Field("Branch / unit name", "name", TEXT, true),
        new Field("Level in the church structure (e.g. District, Region, Parish)", "type", TEXT, true),
        new Field("Parent branch / unit (optional)", "parent_name", TEXT, false),
        new Field("Location", "location", TEXT, false),
        new Field("Notes", "notes", TEXT, false)
    };
    private static final Field[] APPOINTMENT = {
        new Field("Person / pastor name", "person_name", TEXT, true),
        new Field("Role / title", "role", TEXT, true),
        new Field("Church branch / unit", "organization_name", TEXT, false),
        new Field("Start date (YYYY-MM-DD)", "start_date", DATE, false),
        new Field("End date (blank if current)", "end_date", DATE, false),
        new Field("Status (Current / Completed)", "status", TEXT, false),
        new Field("Notes", "notes", TEXT, false)
    };
    private static final Field[] DELEGATION = {
        new Field("Responsible overseer", "overseer", TEXT, true),
        new Field("Delegate name", "delegate_name", TEXT, true),
        new Field("Church branch / unit", "organization_name", TEXT, false),
        new Field("Granted scope / authority", "scope", TEXT, true),
        new Field("Start date (YYYY-MM-DD)", "start_date", DATE, false),
        new Field("End date (optional)", "end_date", DATE, false),
        new Field("Status (Active / Ended)", "status", TEXT, false),
        new Field("Notes", "notes", TEXT, false)
    };
    private static final Field[] VISIT = {
        new Field("Member full name", "member_name", TEXT, true),
        new Field("Visit date (YYYY-MM-DD)", "visit_date", DATE, true),
        new Field("Visitor / care worker", "visitor", TEXT, false),
        new Field("Outcome", "outcome", TEXT, false),
        new Field("Next follow-up date", "next_follow_up", DATE, false),
        new Field("Visit notes", "notes", TEXT, false)
    };
    private static final Field[] CARE = {
        new Field("Member full name", "member_name", TEXT, true),
        new Field("Category (Pastoral, Welfare, Family, Other)", "category", TEXT, true),
        new Field("Priority (Normal / High / Urgent)", "priority", TEXT, true),
        new Field("Status (Open / In progress / Escalated / Resolved)", "status", TEXT, true),
        new Field("Assigned to", "assigned_to", TEXT, false),
        new Field("Opened date (YYYY-MM-DD)", "opened_date", DATE, true),
        new Field("Due / follow-up date", "due_date", DATE, false),
        new Field("Care details", "details", TEXT, true),
        new Field("Resolution / outcome", "resolution", TEXT, false)
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { database = new MemberDatabase(this); if (database.hasChurchSetup()) showHome(); else showChurchSetup(); }
        catch (Throwable error) {
            android.util.Log.e("ChurchCare", "Startup failed", error);
            new AlertDialog.Builder(this).setTitle("ChurchCare could not start")
                .setMessage("The app encountered a startup problem. Your local data has not been intentionally deleted. Details: " + error.getClass().getSimpleName())
                .setPositiveButton("Close", (d,w) -> finish()).show();
        }
    }

    private void base(String title, String subtitle) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12)); root.setBackgroundColor(paper);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this); back.setText("‹"); back.setOnClickListener(v -> { if (database.hasChurchSetup()) showHome(); else showChurchSetup(); });
        if (!database.hasChurchSetup()) back.setVisibility(View.GONE);
        header.addView(back, new LinearLayout.LayoutParams(dp(52), -2));
        TextView heading = new TextView(this); heading.setText(title); heading.setTextSize(24); heading.setTextColor(blue); heading.setTypeface(null,1);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0,-2,1); hp.leftMargin=dp(8); header.addView(heading,hp);
        Button about = new Button(this); about.setText("About"); about.setOnClickListener(v -> showAbout()); header.addView(about);
        root.addView(header);
        TextView sub = new TextView(this); sub.setText(subtitle); sub.setTextSize(13); sub.setTextColor(muted);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); sp.bottomMargin=dp(12); root.addView(sub,sp);
        setContentView(root);
    }

    private void showChurchSetup() {
        base("Set up your church", "Start ChurchCare at the level where your church is ready to begin");
        TextView intro = new TextView(this);
        intro.setText("Every church has its own private ChurchCare space. You can begin at national, regional, district, parish or local branch level. You do not need to register the levels above you first.");
        intro.setTextColor(ink); intro.setTextSize(15); intro.setPadding(dp(14),dp(14),dp(14),dp(14)); intro.setBackgroundColor(0xFFFFFFFF); root.addView(intro);
        EditText church = new EditText(this); church.setSingleLine(true); church.setHint("Church name *"); church.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); root.addView(church);
        EditText unit = new EditText(this); unit.setSingleLine(true); unit.setHint("Your starting branch / unit name *"); unit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); root.addView(unit);
        EditText level = new EditText(this); level.setSingleLine(true); level.setHint("What level is this? e.g. Local Branch, District, Region, National *"); level.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); root.addView(level);
        TextView note = new TextView(this); note.setText("You can add the other levels and branches later. Leave the parent level unselected if it has not joined ChurchCare yet."); note.setTextColor(muted); note.setTextSize(13); note.setPadding(0,dp(10),0,dp(10)); root.addView(note);
        Button create = new Button(this); create.setText("Create my ChurchCare space"); create.setAllCaps(false); root.addView(create);
        create.setOnClickListener(v -> {
            String churchName = church.getText().toString().trim();
            String unitName = unit.getText().toString().trim();
            String levelName = level.getText().toString().trim();
            if (churchName.isEmpty() || unitName.isEmpty() || levelName.isEmpty()) { Toast.makeText(this, "Complete all three fields to continue.", Toast.LENGTH_LONG).show(); return; }
            try { database.saveChurchSetup(churchName, unitName, levelName); showHome(); }
            catch (Exception error) { android.util.Log.e("ChurchCare", "Church setup failed", error); Toast.makeText(this, "Church setup could not be saved. Please try again.", Toast.LENGTH_LONG).show(); }
        });
    }

    private void showHome() {
        base("ChurchCare", database.churchName() + " • " + database.localUnitName());
        int members=database.count("members"), orgs=database.count("organizations"), openCare=database.count("care_records"), pending=database.pendingChanges();
        TextView welcome = new TextView(this); welcome.setText("Your records stay on this device. Work offline and review pending changes whenever needed."); welcome.setTextSize(15); welcome.setTextColor(ink); welcome.setPadding(dp(14),dp(14),dp(14),dp(14)); welcome.setBackgroundColor(0xFFFFFFFF); root.addView(welcome);
        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(stat("Members",String.valueOf(members)),new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat("Branches / units",String.valueOf(orgs)),new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat("Care cases",String.valueOf(openCare)),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2); stp.topMargin=dp(10); stp.bottomMargin=dp(8); root.addView(stats,stp);
        ScrollView scroll=new ScrollView(this); LinearLayout menu=new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL);
        menuButton(menu,"Members","Register, search, update and review member records",()->showRecords("members"));
        menuButton(menu,"Church branches & units","Build and view your church structure at every level",()->showRecords("organizations"));
        menuButton(menu,"Appointments & transfers","Track roles and retain previous appointments",()->showRecords("appointments"));
        menuButton(menu,"Delegation & authority","Define who can act, for whom, and within what scope",()->showRecords("delegations"));
        menuButton(menu,"Visits & follow-ups","Record pastoral visits and next steps",()->showRecords("visits"));
        menuButton(menu,"Care cases & escalation","Track care needs, priorities, assignments and resolution",()->showRecords("care_records"));
        menuButton(menu,"Sync queue & data status","Review local changes awaiting a future cloud sync ("+pending+" pending)",this::showSync);
        menuButton(menu,"Settings / About","Version, local storage and app information",this::showAbout);
        scroll.addView(menu); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=new TextView(this); footer.setText("Offline ready • SQLite local database • Cloud sync not connected"); footer.setTextColor(muted); footer.setTextSize(12); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2); fp.topMargin=dp(8); root.addView(footer,fp);
    }

    private View stat(String label,String value) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10),dp(10),dp(6),dp(10)); box.setBackgroundColor(0xFFFFFFFF);
        TextView v=new TextView(this); v.setText(value); v.setTextSize(22); v.setTypeface(null,1); v.setTextColor(blue); box.addView(v);
        TextView l=new TextView(this); l.setText(label); l.setTextSize(11); l.setTextColor(muted); box.addView(l); return box;
    }
    private void menuButton(LinearLayout menu,String title,String detail,Runnable action) {
        Button b=new Button(this); b.setText(title+"\n"+detail); b.setAllCaps(false); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT); b.setTextSize(14); b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(5); menu.addView(b,p);
    }

    private void showRecords(String table) {
        String title=titleFor(table); base(title, subtitleFor(table));
        final EditText search;
        if (table.equals("members")) {
            search=new EditText(this); search.setSingleLine(true); search.setHint("Search name, phone, church or location");
            root.addView(search);
        } else search=null;
        Button add=new Button(this); add.setText("+ Add "+singular(table)); add.setOnClickListener(v->showForm(table,null,null)); root.addView(add);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if (search != null) {
            Button find=new Button(this); find.setText("Search members"); find.setOnClickListener(v->renderInto(table,list,search.getText().toString()));
            root.addView(find, root.indexOfChild(scroll));
            search.setOnEditorActionListener((v,action,event)->{renderInto(table,list,search.getText().toString());return false;});
        }
        renderInto(table,list,"");
    }

    private void renderRecords(String table,String query) { showRecords(table); }

    private void renderInto(String table,LinearLayout list,String query) {
        list.removeAllViews();
        Cursor c = table.equals("members") && query!=null && !query.trim().isEmpty() ? database.searchMembers(query) : database.listRecords(table);
        try {
            if (!c.moveToFirst()) { TextView empty=new TextView(this); empty.setText("No "+titleFor(table).toLowerCase(Locale.ROOT)+" recorded yet. Use the Add button to begin."); empty.setTextColor(muted); empty.setPadding(dp(12),dp(18),dp(12),dp(18)); list.addView(empty); return; }
            do {
                String id=get(c,"id"), title=recordTitle(table,c), detail=recordDetail(table,c);
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(12)); card.setBackgroundColor(0xFFFFFFFF);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=dp(7); card.setLayoutParams(cp);
                TextView t=new TextView(this); t.setText(title); t.setTextSize(16); t.setTypeface(null,1); t.setTextColor(ink); card.addView(t);
                if (!detail.isEmpty()) { TextView d=new TextView(this); d.setText(detail); d.setTextSize(13); d.setTextColor(muted); d.setPadding(0,dp(4),0,0); card.addView(d); }
                TextView actions=new TextView(this); actions.setText("Tap for details and actions"); actions.setTextSize(11); actions.setTextColor(blue); actions.setPadding(0,dp(7),0,0); card.addView(actions);
                ContentValues snapshot=new ContentValues(); for(int i=0;i<c.getColumnCount();i++){if(!c.isNull(i)) snapshot.put(c.getColumnName(i),c.getString(i));}
                card.setOnClickListener(v->showRecordActions(table,id,snapshot));
                list.addView(card);
            } while(c.moveToNext());
        } finally { c.close(); }
    }

    private void showRecordActions(String table,String id,ContentValues data) {
        String[] actions=table.equals("members")
            ? new String[]{"View details","Edit record","Archive record","Choose profile thumbnail","Choose additional photo 1 (local only)","Choose additional photo 2 (local only)"}
            : new String[]{"View details","Edit record","Archive record"};
        new AlertDialog.Builder(this).setTitle(recordTitle(table,data)).setItems(actions,(d,which)->{
            if(which==0) new AlertDialog.Builder(this).setTitle(recordTitle(table,data)).setMessage(recordDetail(table,data)+"\n\n"+allDetails(data)).setPositiveButton("Close",null).show();
            else if(which==1) showForm(table,id,data);
            else if(which==2) new AlertDialog.Builder(this).setTitle("Archive record?").setMessage("This hides the record from normal lists and records the deletion for future synchronization.").setNegativeButton("Cancel",null).setPositiveButton("Archive",(x,w)->{database.softDelete(table,id);showRecords(table);Toast.makeText(this,"Record archived",Toast.LENGTH_SHORT).show();}).show();
            else if(table.equals("members")) {
                photoRecordId=id;
                photoColumn=which==3?"profile_photo_path":which==4?"extra_photo_one_path":"extra_photo_two_path";
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,PICK_MEMBER_PHOTO);
            }
        }).show();
    }

    private void showForm(String table,String editId,ContentValues existing) {
        Field[] fields=fieldsFor(table);
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(18),dp(6),dp(18),0);
        ScrollView sc=new ScrollView(this); sc.addView(form);
        EditText[] inputs=new EditText[fields.length];
        for(int i=0;i<fields.length;i++){
            Field f=fields[i]; EditText e=new EditText(this); e.setHint(f.label+(f.required?" *":"")); e.setInputType(f.type); e.setSingleLine(false); e.setMinLines(1);
            if(f.key.equals("visit_date")||f.key.equals("opened_date")) e.setText(today());
            if(f.key.equals("status")) e.setText(table.equals("appointments")?"Current":table.equals("members")?"Active":table.equals("care_records")?"Open":"Active");
            if(f.key.equals("priority")) e.setText("Normal");
            if(f.key.equals("scope")) e.setText("View and record care");
            if(existing!=null && existing.containsKey(f.key)) e.setText(existing.getAsString(f.key));
            if(existing!=null && table.equals("organizations") && f.key.equals("parent_name")) {
                String parentId=existing.getAsString("parent_id");
                if(parentId!=null&&!parentId.isEmpty()) e.setText(database.findNameById("organizations",parentId));
            }
            if (isRelationshipField(table, f.key)) {
                String foreignKey = relationshipForeignKey(table, f.key);
                if (existing != null && foreignKey != null) e.setTag(existing.getAsString(foreignKey));
                configureRelationshipPicker(table, f.key, e);
            }
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(4); form.addView(e,p); inputs[i]=e;
        }
        new AlertDialog.Builder(this).setTitle((editId==null?"Add ":"Edit ")+singular(table)).setView(sc)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{
                ContentValues values=new ContentValues();
                for(int i=0;i<fields.length;i++){
                    String value=inputs[i].getText().toString().trim();
                    if(fields[i].required && value.isEmpty()){Toast.makeText(this,"Please complete: "+fields[i].label,Toast.LENGTH_LONG).show();return;}
                    values.put(fields[i].key,value);
                }
                applyRelationships(table,values);
                if (!applySelectedRelationshipIds(table, fields, inputs, values)) return;
                if (table.equals("organizations")) {
                    String proposedParentId = values.getAsString("parent_id");
                    if (database.wouldCreateOrganizationCycle(editId, proposedParentId)) {
                        Toast.makeText(this, "That parent would create a circular organization hierarchy. Choose a different parent.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                try {
                    if(editId==null) {
                        if(table.equals("appointments") && "Current".equalsIgnoreCase(values.getAsString("status")))
                            database.closeCurrentAppointments(values.getAsString("person_name"),values.getAsString("start_date"));
                        database.insertRecord(table,values);
                    } else database.updateRecord(table,editId,values);
                    if(table.equals("visits")) database.trimVisits(values.getAsString("member_name"));
                    Toast.makeText(this,"Saved on this device",Toast.LENGTH_SHORT).show(); showRecords(table);
                } catch(Exception e){android.util.Log.e("ChurchCare","Save failed",e);Toast.makeText(this,"Could not save. Check the details and try again.",Toast.LENGTH_LONG).show();}
            }).show();
    }

    private boolean isRelationshipField(String table, String key) {
        return (table.equals("organizations") && key.equals("parent_name"))
            || (table.equals("members") && key.equals("organization"))
            || ((table.equals("appointments") || table.equals("delegations")) && key.equals("organization_name"))
            || ((table.equals("visits") || table.equals("care_records")) && key.equals("member_name"));
    }

    private void configureRelationshipPicker(String ownerTable, String key, EditText field) {
        String sourceTable = (key.equals("parent_name") || key.equals("organization") || key.equals("organization_name")) ? "organizations" : "members";
        String displayColumn = sourceTable.equals("organizations") ? "name" : "full_name";
        field.setFocusable(false);
        field.setClickable(true);
        field.setOnClickListener(v -> {
            java.util.ArrayList<String> labels = new java.util.ArrayList<>();
            java.util.ArrayList<String> ids = new java.util.ArrayList<>();
            try (Cursor cursor = database.listRecords(sourceTable)) {
                while (cursor.moveToNext()) {
                    String id = get(cursor, "id");
                    String name = get(cursor, displayColumn);
                    if (!name.trim().isEmpty()) {
                        if (sourceTable.equals("organizations") && ownerTable.equals("organizations")
                                && id.equals(String.valueOf(field.getTag()))) continue;
                        ids.add(id);
                        String label = sourceTable.equals("organizations")
                            ? join(database.organizationPath(id), get(cursor, "type"))
                            : (get(cursor, "phone").trim().isEmpty() ? name : name + " • " + get(cursor, "phone"));
                        labels.add(label);
                    }
                }
            }
            if (sourceTable.equals("organizations") && ownerTable.equals("organizations")) {
                labels.add(0, "No parent (top level)");
                ids.add(0, "");
            }
            if (labels.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("No records available")
                    .setMessage("Add the required " + (sourceTable.equals("organizations") ? "church organization" : "member") + " first, then return here.")
                    .setPositiveButton("OK", null).show();
                return;
            }
            String[] options = labels.toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle("Select " + (sourceTable.equals("organizations") ? "church organization" : "member"))
                .setItems(options, (dialog, which) -> {
                    String selectedId = ids.get(which);
                    field.setTag(selectedId);
                    field.setText(selectedId.isEmpty() ? "" : getLabelName(sourceTable, selectedId));
                })
                .setNegativeButton("Cancel", null).show();
        });
        field.setHint(field.getHint() + " — tap to select");
    }

    private String getLabelName(String table, String id) {
        return database.findNameById(table, id);
    }

    private String relationshipForeignKey(String table, String key) {
        if (table.equals("organizations") && key.equals("parent_name")) return "parent_id";
        if (table.equals("members") && key.equals("organization")) return "organization_id";
        if ((table.equals("appointments") || table.equals("delegations")) && key.equals("organization_name")) return "organization_id";
        if ((table.equals("visits") || table.equals("care_records")) && key.equals("member_name")) return "member_id";
        return null;
    }

    /** Save the exact selected record IDs instead of resolving potentially duplicate names. */
    private boolean applySelectedRelationshipIds(String table, Field[] fields, EditText[] inputs, ContentValues values) {
        for (int i = 0; i < fields.length; i++) {
            String key = fields[i].key;
            if (!isRelationshipField(table, key)) continue;
            String foreignKey = relationshipForeignKey(table, key);
            String selectedId = inputs[i].getTag() == null ? null : String.valueOf(inputs[i].getTag());
            String display = inputs[i].getText().toString().trim();
            if (selectedId == null && !display.isEmpty()) {
                Toast.makeText(this, "Please select the linked record from the picker so ChurchCare can save the correct record ID.", Toast.LENGTH_LONG).show();
                return false;
            }
            if ((table.equals("visits") || table.equals("care_records")) && key.equals("member_name")
                    && (selectedId == null || selectedId.isEmpty())) {
                Toast.makeText(this, "Please select a member from the list.", Toast.LENGTH_LONG).show();
                return false;
            }
            if (foreignKey != null) {
                if (selectedId == null || selectedId.isEmpty()) values.putNull(foreignKey);
                else values.put(foreignKey, selectedId);
            }
        }
        return true;
    }

    private void applyRelationships(String table,ContentValues v) {
        if(table.equals("organizations")){
            String parent=v.getAsString("parent_name"); v.remove("parent_name");
            if(parent!=null&&!parent.trim().isEmpty()) v.put("parent_id",database.findIdByName("organizations","name",parent)); else v.putNull("parent_id");
        } else if(table.equals("members")){
            String org=v.getAsString("organization"); if(org!=null&&!org.trim().isEmpty()) v.put("organization_id",database.findIdByName("organizations","name",org));
        } else if(table.equals("appointments")||table.equals("delegations")){
            String org=v.getAsString("organization_name"); v.remove("organization_name");
            if(org!=null&&!org.trim().isEmpty()) v.put("organization_id",database.findIdByName("organizations","name",org)); else v.putNull("organization_id");
        } else if(table.equals("visits")||table.equals("care_records")){
            String member=v.getAsString("member_name"); if(member!=null) v.put("member_id",database.findIdByName("members","full_name",member));
            if(table.equals("care_records")&&!v.containsKey("opened_date"))v.put("opened_date",today());
        }
    }

    private Field[] fieldsFor(String table) {
        if(table.equals("members"))return MEMBER;
        if(table.equals("organizations"))return ORGANIZATION;
        if(table.equals("appointments"))return APPOINTMENT;
        if(table.equals("delegations"))return DELEGATION;
        if(table.equals("visits"))return VISIT;
        return CARE;
    }
    private String titleFor(String table) {
        if(table.equals("members"))return "Members"; if(table.equals("organizations"))return "Church branches & units";
        if(table.equals("appointments"))return "Appointments & transfers"; if(table.equals("delegations"))return "Delegation & authority";
        if(table.equals("visits"))return "Visits & follow-ups"; return "Care cases & escalation";
    }
    private String subtitleFor(String table) {
        if(table.equals("members"))return "Local member directory. Tap a record to view, edit or archive.";
        if(table.equals("organizations"))return "Build your church structure. Add any level now and link its parent branch/unit when that level is available.";
        if(table.equals("appointments"))return "Keep each appointment as a separate dated record to preserve history.";
        if(table.equals("delegations"))return "Delegation scope is recorded explicitly; it does not grant app-wide access control by itself.";
        if(table.equals("visits"))return "Visit history is limited to the five newest records per member.";
        return "Track priorities, assignments, follow-up dates and care resolution.";
    }
    private String singular(String table) {
        if(table.equals("members"))return "member"; if(table.equals("organizations"))return "church branch / unit";
        if(table.equals("appointments"))return "appointment"; if(table.equals("delegations"))return "delegation";
        if(table.equals("visits"))return "visit"; return "care case";
    }
    private String recordTitle(String table,Cursor c) {
        if(table.equals("members"))return get(c,"full_name");
        if(table.equals("organizations"))return join(database.organizationPath(get(c,"id")),get(c,"type"));
        if(table.equals("appointments"))return get(c,"person_name")+" • "+get(c,"role");
        if(table.equals("delegations"))return get(c,"overseer")+" → "+get(c,"delegate_name");
        if(table.equals("visits"))return get(c,"member_name")+" • "+get(c,"visit_date");
        return get(c,"member_name")+" • "+get(c,"category");
    }
    private String recordTitle(String table,ContentValues v) {
        if(table.equals("members"))return safe(v,"full_name");
        if(table.equals("organizations"))return join(database.organizationPath(safe(v,"id")),safe(v,"type"));
        if(table.equals("appointments"))return safe(v,"person_name")+" • "+safe(v,"role");
        if(table.equals("delegations"))return safe(v,"overseer")+" → "+safe(v,"delegate_name");
        if(table.equals("visits"))return safe(v,"member_name")+" • "+safe(v,"visit_date");
        return safe(v,"member_name")+" • "+safe(v,"category");
    }
    private String recordDetail(String table,Cursor c) {
        if(table.equals("members"))return join(get(c,"phone"),get(c,"organization"),get(c,"status"),get(c,"address"));
        if(table.equals("organizations"))return join(get(c,"parent_id").isEmpty()?"No parent branch/unit linked yet":"Parent: "+database.findNameById("organizations",get(c,"parent_id")),get(c,"location"),get(c,"notes"));
        if(table.equals("appointments"))return join(get(c,"organization_id"),get(c,"start_date")+" → "+(get(c,"end_date").isEmpty()?"Current":get(c,"end_date")),get(c,"status"),get(c,"notes"));
        if(table.equals("delegations"))return join("Scope: "+get(c,"scope"),"Organization ID: "+get(c,"organization_id"),get(c,"status"),get(c,"start_date")+" → "+get(c,"end_date"));
        if(table.equals("visits"))return join("Visitor: "+get(c,"visitor"),"Outcome: "+get(c,"outcome"),"Follow-up: "+get(c,"next_follow_up"),get(c,"notes"));
        return join("Priority: "+get(c,"priority"),"Status: "+get(c,"status"),"Assigned: "+get(c,"assigned_to"),"Due: "+get(c,"due_date"),get(c,"details"),get(c,"resolution"));
    }
    private String recordDetail(String table,ContentValues v) {
        if(table.equals("members"))return join(safe(v,"phone"),safe(v,"organization"),safe(v,"status"),safe(v,"address"),
            safe(v,"profile_photo_path").isEmpty()?"":"Profile thumbnail saved",
            safe(v,"extra_photo_one_path").isEmpty()?"":"Local photo 1 saved",
            safe(v,"extra_photo_two_path").isEmpty()?"":"Local photo 2 saved");
        if(table.equals("organizations"))return join(safe(v,"parent_id").isEmpty()?"No parent branch/unit linked yet":"Parent: "+database.findNameById("organizations",safe(v,"parent_id")),safe(v,"location"),safe(v,"notes"));
        if(table.equals("appointments"))return join(safe(v,"organization_id"),safe(v,"start_date")+" → "+(safe(v,"end_date").isEmpty()?"Current":safe(v,"end_date")),safe(v,"status"),safe(v,"notes"));
        if(table.equals("delegations"))return join("Scope: "+safe(v,"scope"),"Organization ID: "+safe(v,"organization_id"),safe(v,"status"),safe(v,"start_date")+" → "+safe(v,"end_date"));
        if(table.equals("visits"))return join("Visitor: "+safe(v,"visitor"),"Outcome: "+safe(v,"outcome"),"Follow-up: "+safe(v,"next_follow_up"),safe(v,"notes"));
        return join("Priority: "+safe(v,"priority"),"Status: "+safe(v,"status"),"Assigned: "+safe(v,"assigned_to"),"Due: "+safe(v,"due_date"),safe(v,"details"),safe(v,"resolution"));
    }
    private String allDetails(ContentValues v) {
        StringBuilder b=new StringBuilder();
        for(String k:v.keySet()) if(!k.equals("id")&&!k.equals("deleted")&&!k.equals("sync_state")&&!k.equals("created_at")&&!k.equals("updated_at")) b.append(k.replace('_',' ')).append(": ").append(safe(v,k)).append("\n");
        return b.toString().trim();
    }
    private String get(Cursor c,String key){int i=c.getColumnIndex(key);return i<0||c.isNull(i)?"":c.getString(i);}
    private String safe(ContentValues v,String key){String x=v.getAsString(key);return x==null?"":x;}
    private String join(String... values){StringBuilder b=new StringBuilder();for(String x:values)if(x!=null&&!x.trim().isEmpty()&&!x.endsWith(": ")) {if(b.length()>0)b.append(" • ");b.append(x);}return b.toString();}
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

    private void showSync() {
        base("Sync queue & status","Offline records stay on this device. Cloud backup is explicit; automatic multi-device sync is not active.");
        Button exportButton=new Button(this); exportButton.setText("Export local backup"); exportButton.setAllCaps(false); exportButton.setOnClickListener(v->{ Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/zip"); intent.putExtra(Intent.EXTRA_TITLE,"ChurchCare-backup-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".zip"); startActivityForResult(intent,EXPORT_BACKUP); }); root.addView(exportButton);
        Button driveUpload=new Button(this); driveUpload.setText("Back up full ChurchCare data to Google Drive"); driveUpload.setAllCaps(false); driveUpload.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Upload full ChurchCare backup?").setMessage("This uploads a ZIP backup of member records, visits, care cases, notes, the change log, and accessible profile thumbnails to the Google Drive account you authorize. Additional member photos stay on this device and are not uploaded. These records may contain sensitive pastoral information. Upload only to an account and Drive location you trust.").setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->authorizeDrive("upload")).show()); root.addView(driveUpload);
        Button driveRestore=new Button(this); driveRestore.setText("Restore latest ChurchCare Drive backup"); driveRestore.setAllCaps(false); driveRestore.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Replace this device's records?").setMessage("ChurchCare will download the newest backup created by this app in the Google Drive account you authorize and replace the records and change log on this device. Export a local backup first. Only continue if you trust the selected Google account and backup.").setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->authorizeDrive("restore")).show()); root.addView(driveRestore);
        Button importButton=new Button(this); importButton.setText("Restore from backup (replaces local records)"); importButton.setAllCaps(false); importButton.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Restore ChurchCare backup?").setMessage("Restore replaces the records and change log currently stored on this device. Export a backup of the current data first if you may need it. Only choose a trusted ChurchCare ZIP backup or an older ChurchCare JSON backup.").setNegativeButton("Cancel",null).setPositiveButton("Choose backup",(d,w)->{ Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*"); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent,IMPORT_BACKUP); }).show()); root.addView(importButton);
        android.content.SharedPreferences syncPrefs=getSharedPreferences("churchcare_sync_status",MODE_PRIVATE);
        String lastDriveBackup=syncPrefs.getString("drive_backup_time","");
        String driveBackupStatus=syncPrefs.getString("drive_backup_status","Not yet confirmed");
        String lastDriveRestore=syncPrefs.getString("drive_restore_time","");
        String driveRestoreStatus=syncPrefs.getString("drive_restore_status","Not yet confirmed");
        String localBackupTime=syncPrefs.getString("local_backup_time","");
        TextView summary=new TextView(this);
        summary.setText("Backup operation status\\n\\nGoogle Drive backup: "+driveBackupStatus+(lastDriveBackup.isEmpty()?"":"\\nLast completed: "+lastDriveBackup)+"\\n\\nGoogle Drive restore: "+driveRestoreStatus+(lastDriveRestore.isEmpty()?"":"\\nLast completed: "+lastDriveRestore)+"\\n\\nLocal backup: "+(localBackupTime.isEmpty()?"Not yet confirmed": "Completed — "+localBackupTime));
        summary.setTextColor(ink); summary.setTextSize(15); summary.setPadding(dp(12),dp(12),dp(12),dp(12)); summary.setBackgroundColor(0xFFFFFFFF); root.addView(summary);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void showAbout() {
        String version="Version details unavailable";
        try { PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0); version="Version "+p.versionName+" ("+p.getLongVersionCode()+")\nPackage: "+getPackageName(); }
        catch(PackageManager.NameNotFoundException ignored){}
        new AlertDialog.Builder(this).setTitle("About ChurchCare").setMessage(version+"\n\nOffline-first church and member care. SQLite is the local source of truth.\n\nCurrent milestone: operational local records and change queue. Google Drive sync and enforced account-level access control are not yet connected.").setPositiveButton("OK",null).show();
    }
    /** Local ZIP backup may include all saved member photos. */
    private void exportBackupArchive(OutputStream destination) throws Exception {
        exportBackupArchive(destination, true);
    }

    /** Cloud backups include profile thumbnails only; extra photos stay on the capturing device. */
    private void exportCloudBackupArchive(OutputStream destination) throws Exception {
        exportBackupArchive(destination, false);
    }

    private void exportBackupArchive(OutputStream destination, boolean includeExtraPhotos) throws Exception {
        JSONObject backup = database.exportBackup();
        Map<String, byte[]> media = new HashMap<>();
        org.json.JSONArray tables = backup.getJSONArray("tables");
        String[] photoColumns = includeExtraPhotos
            ? new String[]{"profile_photo_path", "extra_photo_one_path", "extra_photo_two_path"}
            : new String[]{"profile_photo_path"};
        for (int t = 0; t < tables.length(); t++) {
            JSONObject table = tables.getJSONObject(t);
            if (!"members".equals(table.optString("name"))) continue;
            org.json.JSONArray rows = table.getJSONArray("rows");
            for (int r = 0; r < rows.length(); r++) {
                JSONObject row = rows.getJSONObject(r);
                String memberId = row.optString("id", "");
                for (String column : photoColumns) {
                    String reference = row.optString(column, "");
                    if (reference.isEmpty() || "null".equals(reference)) continue;
                    String entryName = "media/" + memberId + "_" + column + ".bin";
                    byte[] imageBytes;
                    Uri uri = Uri.parse(reference);
                    try (InputStream imageIn = reference.startsWith("/") ? new FileInputStream(reference) : getContentResolver().openInputStream(uri);
                         ByteArrayOutputStream imageOut = new ByteArrayOutputStream()) {
                        if (imageIn == null) throw new java.io.IOException("A saved member photo is no longer accessible.");
                        byte[] buf = new byte[8192]; int n;
                        while ((n = imageIn.read(buf)) != -1) {
                            if (imageOut.size() + n > 20 * 1024 * 1024) throw new java.io.IOException("A member photo exceeds the 20 MB safety limit.");
                            imageOut.write(buf, 0, n);
                        }
                        imageBytes = imageOut.toByteArray();
                    }
                    media.put(entryName, imageBytes);
                    row.put(column, "churchcare-media:" + entryName);
                }
            }
        }
        try (ZipOutputStream zip = new ZipOutputStream(destination)) {
            zip.putNextEntry(new ZipEntry("backup.json"));
            zip.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> entry : media.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    /** Validate a ZIP backup and its media references before the database transaction begins. */
    private void restoreBackupArchive(byte[] archiveBytes) throws Exception {
        JSONObject backup = null;
        Map<String, File> extracted = new HashMap<>();
        File mediaDir = new File(getFilesDir(), "restored_member_photos");
        if (!mediaDir.exists() && !mediaDir.mkdirs()) throw new java.io.IOException("Could not prepare private photo storage.");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("backup.json".equals(name)) {
                    ByteArrayOutputStream json = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192]; int n;
                    while ((n = zip.read(buf)) != -1) {
                        if (json.size() + n > 20 * 1024 * 1024) throw new java.io.IOException("Backup data file is too large.");
                        json.write(buf, 0, n);
                    }
                    backup = new JSONObject(new String(json.toByteArray(), StandardCharsets.UTF_8));
                } else if (name.startsWith("media/")) {
                    String filename = name.substring("media/".length());
                    if (!filename.matches("[A-Za-z0-9_-]+\\.bin")) throw new java.io.IOException("Backup contains an unsafe media filename.");
                    File target = new File(mediaDir, filename);
                    try (FileOutputStream fileOut = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192]; int n; int total = 0;
                        while ((n = zip.read(buf)) != -1) {
                            total += n;
                            if (total > 20 * 1024 * 1024) throw new java.io.IOException("A bundled photo exceeds the 20 MB safety limit.");
                            fileOut.write(buf, 0, n);
                        }
                    }
                    extracted.put("media/" + filename, target);
                }
                zip.closeEntry();
            }
        }
        if (backup == null) throw new java.io.IOException("This ZIP does not contain a ChurchCare backup.");
        org.json.JSONArray tables = backup.getJSONArray("tables");
        for (int t = 0; t < tables.length(); t++) {
            JSONObject table = tables.getJSONObject(t);
            if (!"members".equals(table.optString("name"))) continue;
            org.json.JSONArray rows = table.getJSONArray("rows");
            String[] columns = {"profile_photo_path", "extra_photo_one_path", "extra_photo_two_path"};
            for (int r = 0; r < rows.length(); r++) {
                JSONObject row = rows.getJSONObject(r);
                for (String column : columns) {
                    String value = row.optString(column, "");
                    if (!value.startsWith("churchcare-media:")) continue;
                    String entryName = value.substring("churchcare-media:".length());
                    File file = extracted.get(entryName);
                    if (file == null || !file.isFile()) throw new java.io.IOException("A referenced member photo is missing from the backup.");
                    row.put(column, Uri.fromFile(file).toString());
                }
            }
        }
        database.restoreBackup(backup);
    }


    /** Request Drive permission only when the user starts a Drive operation. */
    private void authorizeDrive(String action) {
        pendingDriveAction = action;
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(java.util.Collections.singletonList(new Scope("https://www.googleapis.com/auth/drive.file"))).build();
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener(this::continueDriveAuthorization)
            .addOnFailureListener(error -> showDriveError("Google Drive authorization failed. Check Google Play services and the Google Cloud OAuth setup. " + error.getClass().getSimpleName()));
    }

    private void continueDriveAuthorization(AuthorizationResult result) {
        if (result.hasResolution()) {
            try { startIntentSenderForResult(result.getPendingIntent().getIntentSender(), DRIVE_AUTHORIZATION, null, 0, 0, 0); }
            catch (IntentSender.SendIntentException error) { showDriveError("Could not open Google's permission screen. " + error.getClass().getSimpleName()); }
            return;
        }
        String token = result.getAccessToken();
        if (token == null || token.isEmpty()) { showDriveError("Google did not return a Drive access token. Please try again."); return; }
        runDriveAction(token, pendingDriveAction);
    }

    private void runDriveAction(String token, String action) {
        Toast.makeText(this, action.equals("upload") ? "Preparing secure backup…" : "Looking for the latest Drive backup…", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                if ("upload".equals(action)) {
                    String name = "ChurchCare-cloud-backup-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip";
                    File archive = new File(getCacheDir(), "churchcare-drive-upload.zip");
                    try (OutputStream out = new FileOutputStream(archive)) { exportCloudBackupArchive(out); }
                    if (archive.length() > 100L * 1024L * 1024L) throw new java.io.IOException("Backup exceeds the 100 MB safety limit.");
                    uploadDriveBackup(token, archive, name);
                    archive.delete();
                    getSharedPreferences("churchcare_sync_status",MODE_PRIVATE).edit().putString("drive_backup_status","Completed").putString("drive_backup_time",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())).putString("drive_backup_file",name).apply();
                    runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Google Drive backup complete")
                        .setMessage("Upload completed successfully: " + name + ". Pending Local Changes are a separate queue and do not mean this upload is pending.")
                        .setPositiveButton("OK", (d,w) -> showSync()).show());
                } else if ("restore".equals(action)) {
                    byte[] backup = downloadLatestDriveBackup(token);
                    restoreBackupArchive(backup);
                    getSharedPreferences("churchcare_sync_status",MODE_PRIVATE).edit().putString("drive_restore_status","Completed").putString("drive_restore_time",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())).apply();
                    runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Drive backup restored")
                        .setMessage("The latest ChurchCare ZIP backup was restored. Review member records and photos before continuing.")
                        .setPositiveButton("OK", (d,w) -> showHome()).show());
                } else throw new java.io.IOException("Unknown Drive action.");
            } catch (Exception error) {
                android.util.Log.e("ChurchCare", "Drive operation failed", error);
                android.content.SharedPreferences.Editor statusEdit=getSharedPreferences("churchcare_sync_status",MODE_PRIVATE).edit();
                if ("upload".equals(action)) statusEdit.putString("drive_backup_status","Failed").remove("drive_backup_file");
                else if ("restore".equals(action)) statusEdit.putString("drive_restore_status","Failed");
                statusEdit.apply();
                runOnUiThread(() -> showDriveError("Google Drive operation did not complete. Your current records should remain unchanged if download validation or the restore transaction failed. Details: " + error.getMessage()));
            }
        }, "ChurchCare-Drive").start();
    }

    private void uploadDriveBackup(String token, File archive, String filename) throws Exception {
        String boundary = "churchcare_" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime").openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(20000); connection.setReadTimeout(60000);
        connection.setDoOutput(true); connection.setChunkedStreamingMode(8192);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        JSONObject metadata = new JSONObject(); metadata.put("name", filename); metadata.put("mimeType", "application/zip");
        byte[] head = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n--" + boundary + "\r\nContent-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream(); InputStream in = new FileInputStream(archive)) {
            out.write(head); byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode(); String response = readHttpResponse(connection, status); connection.disconnect();
        if (status < 200 || status >= 300) throw new java.io.IOException("Drive upload HTTP " + status + ": " + response);
    }

    private byte[] downloadLatestDriveBackup(String token) throws Exception {
        String query = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode("name contains 'ChurchCare-cloud-backup-' and trashed = false", "UTF-8") + "&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name,modifiedTime,size)";
        HttpURLConnection list = (HttpURLConnection) new URL(query).openConnection();
        list.setConnectTimeout(20000); list.setReadTimeout(30000); list.setRequestProperty("Authorization", "Bearer " + token);
        int listStatus = list.getResponseCode(); String listing = readHttpResponse(list, listStatus); list.disconnect();
        if (listStatus < 200 || listStatus >= 300) throw new java.io.IOException("Drive search HTTP " + listStatus + ": " + listing);
        org.json.JSONArray files = new JSONObject(listing).optJSONArray("files");
        if (files == null || files.length() == 0) throw new java.io.IOException("No ChurchCare cloud backup found in this Google Drive account. Upload one first or check the selected account.");
        String id = files.getJSONObject(0).getString("id");
        HttpURLConnection download = (HttpURLConnection) new URL("https://www.googleapis.com/drive/v3/files/" + URLEncoder.encode(id, "UTF-8") + "?alt=media").openConnection();
        download.setConnectTimeout(20000); download.setReadTimeout(60000); download.setRequestProperty("Authorization", "Bearer " + token);
        int status = download.getResponseCode();
        if (status < 200 || status >= 300) { String error = readHttpResponse(download, status); download.disconnect(); throw new java.io.IOException("Drive download HTTP " + status + ": " + error); }
        try (InputStream in = download.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) { if (bytes.size() + count > 100 * 1024 * 1024) throw new java.io.IOException("Drive backup exceeds the 100 MB safety limit."); bytes.write(buffer, 0, count); }
            download.disconnect(); return bytes.toByteArray();
        }
    }

    private String readHttpResponse(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) { if (bytes.size() + count > 1024 * 1024) break; bytes.write(buffer, 0, count); }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void showDriveError(String message) {
        pendingDriveAction = "";
        new AlertDialog.Builder(this).setTitle("Google Drive not completed").setMessage(message).setPositiveButton("OK", (d,w) -> showSync()).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        if(requestCode == DRIVE_AUTHORIZATION) {
            if(resultCode != RESULT_OK || data == null) { pendingDriveAction = ""; showDriveError("Google Drive permission was cancelled or not granted."); return; }
            try { continueDriveAuthorization(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)); }
            catch(Exception error) { pendingDriveAction = ""; showDriveError("Google Drive permission could not be completed. " + error.getClass().getSimpleName()); }
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null && requestCode == EXPORT_BACKUP) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                if (out == null) throw new java.io.IOException("Could not open backup destination.");
                exportBackupArchive(out);
                getSharedPreferences("churchcare_sync_status",MODE_PRIVATE).edit().putString("local_backup_time",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())).apply();
                Toast.makeText(this, "Local backup exported with available member photos. Keep the file somewhere safe.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                android.util.Log.e("ChurchCare", "Backup export failed", e);
                new AlertDialog.Builder(this).setTitle("Backup export failed").setMessage("ChurchCare could not complete the backup. Check that saved photos are still accessible and choose a writable destination. Your records were not changed.").setPositiveButton("OK", null).show();
            }
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getData() != null && requestCode == IMPORT_BACKUP) {
            try (InputStream in = getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (in == null) throw new java.io.IOException("Could not open selected backup.");
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 100 * 1024 * 1024) throw new java.io.IOException("Backup exceeds the 100 MB safety limit.");
                    bytes.write(buffer, 0, count);
                }
                byte[] raw = bytes.toByteArray();
                String prefix = new String(raw, 0, Math.min(raw.length, 32), StandardCharsets.UTF_8).trim();
                if (prefix.startsWith("{")) {
                    // Backward compatibility with the earlier JSON-only backup format.
                    database.restoreBackup(new JSONObject(new String(raw, StandardCharsets.UTF_8)));
                } else {
                    restoreBackupArchive(raw);
                }
                Toast.makeText(this, "Backup restored. Review your records before continuing.", Toast.LENGTH_LONG).show();
                showHome();
            } catch (Exception e) {
                android.util.Log.e("ChurchCare", "Backup restore failed", e);
                new AlertDialog.Builder(this).setTitle("Restore failed").setMessage("ChurchCare could not restore this file. Your current database should remain unchanged if validation or the restore transaction failed. Details: " + e.getClass().getSimpleName()).setPositiveButton("OK", null).show();
            }
            return;
        }
        if(requestCode==PICK_MEMBER_PHOTO && resultCode==RESULT_OK && data!=null && data.getData()!=null && photoRecordId!=null && photoColumn!=null) {
            Uri uri=data.getData();
            try {
                final int takeFlags=data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri,takeFlags);
                ContentValues values=new ContentValues(); values.put(photoColumn,uri.toString());
                database.updateRecord("members",photoRecordId,values);
                Toast.makeText(this,photoColumn.equals("profile_photo_path")?"Profile thumbnail saved locally":"Additional photo saved on this device only",Toast.LENGTH_LONG).show();
                showRecords("members");
            } catch(Exception e) {
                android.util.Log.e("ChurchCare","Could not save photo reference",e);
                Toast.makeText(this,"Photo could not be saved. Please choose another image.",Toast.LENGTH_LONG).show();
            }
        }
        photoRecordId=null; photoColumn=null;
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    @Override public void onBackPressed(){if(database!=null && database.hasChurchSetup()) showHome(); else if(database!=null) showChurchSetup(); else super.onBackPressed();}
    @Override protected void onDestroy(){if(database!=null)database.close();super.onDestroy();}
}
