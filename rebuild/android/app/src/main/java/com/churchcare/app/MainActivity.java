package com.churchcare.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private MemberDatabase database;
    private LinearLayout root;
    private int blue = 0xFF0B4F85, ink = 0xFF172B3A, muted = 0xFF526273, paper = 0xFFF4F7FA;
    private static final int PICK_MEMBER_PHOTO = 7104;
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
        new Field("Church / organization name", "organization", TEXT, false),
        new Field("Address / location", "address", TEXT, false),
        new Field("Status (Active / Inactive)", "status", TEXT, false)
    };
    private static final Field[] ORGANIZATION = {
        new Field("Organization name", "name", TEXT, true),
        new Field("Level / type (e.g. Zone, Area, Parish)", "type", TEXT, true),
        new Field("Parent organization name (optional)", "parent_name", TEXT, false),
        new Field("Location", "location", TEXT, false),
        new Field("Notes", "notes", TEXT, false)
    };
    private static final Field[] APPOINTMENT = {
        new Field("Person / pastor name", "person_name", TEXT, true),
        new Field("Role / title", "role", TEXT, true),
        new Field("Organization name", "organization_name", TEXT, false),
        new Field("Start date (YYYY-MM-DD)", "start_date", DATE, false),
        new Field("End date (blank if current)", "end_date", DATE, false),
        new Field("Status (Current / Completed)", "status", TEXT, false),
        new Field("Notes", "notes", TEXT, false)
    };
    private static final Field[] DELEGATION = {
        new Field("Responsible overseer", "overseer", TEXT, true),
        new Field("Delegate name", "delegate_name", TEXT, true),
        new Field("Organization / area name", "organization_name", TEXT, false),
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
        try { database = new MemberDatabase(this); showHome(); }
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
        Button back = new Button(this); back.setText("‹"); back.setOnClickListener(v -> showHome());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), -2));
        TextView heading = new TextView(this); heading.setText(title); heading.setTextSize(24); heading.setTextColor(blue); heading.setTypeface(null,1);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0,-2,1); hp.leftMargin=dp(8); header.addView(heading,hp);
        Button about = new Button(this); about.setText("About"); about.setOnClickListener(v -> showAbout()); header.addView(about);
        root.addView(header);
        TextView sub = new TextView(this); sub.setText(subtitle); sub.setTextSize(13); sub.setTextColor(muted);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); sp.bottomMargin=dp(12); root.addView(sub,sp);
        setContentView(root);
    }

    private void showHome() {
        base("ChurchCare", "Offline-first church and member care");
        int members=database.count("members"), orgs=database.count("organizations"), openCare=database.count("care_records"), pending=database.pendingChanges();
        TextView welcome = new TextView(this); welcome.setText("Your records stay on this device. Work offline and review pending changes whenever needed."); welcome.setTextSize(15); welcome.setTextColor(ink); welcome.setPadding(dp(14),dp(14),dp(14),dp(14)); welcome.setBackgroundColor(0xFFFFFFFF); root.addView(welcome);
        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(stat("Members",String.valueOf(members)),new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat("Church units",String.valueOf(orgs)),new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat("Care cases",String.valueOf(openCare)),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2); stp.topMargin=dp(10); stp.bottomMargin=dp(8); root.addView(stats,stp);
        ScrollView scroll=new ScrollView(this); LinearLayout menu=new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL);
        menuButton(menu,"Members","Register, search, update and review member records",()->showRecords("members"));
        menuButton(menu,"Church organization","Build and view the Zone / Area / Parish structure",()->showRecords("organizations"));
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
            if (isRelationshipField(table, f.key)) configureRelationshipPicker(table, f.key, e);
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
                        ids.add(id);
                        labels.add(sourceTable.equals("organizations") ? join(database.organizationPath(id), get(cursor, "type")) : name);
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
                .setItems(options, (dialog, which) -> field.setText(which == 0 && sourceTable.equals("organizations") && ownerTable.equals("organizations") ? "" : getLabelName(sourceTable, ids.get(which))))
                .setNegativeButton("Cancel", null).show();
        });
        field.setHint(field.getHint() + " — tap to select");
    }

    private String getLabelName(String table, String id) {
        return database.findNameById(table, id);
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
        if(table.equals("members"))return "Members"; if(table.equals("organizations"))return "Church organization";
        if(table.equals("appointments"))return "Appointments & transfers"; if(table.equals("delegations"))return "Delegation & authority";
        if(table.equals("visits"))return "Visits & follow-ups"; return "Care cases & escalation";
    }
    private String subtitleFor(String table) {
        if(table.equals("members"))return "Local member directory. Tap a record to view, edit or archive.";
        if(table.equals("organizations"))return "Create organization units and link each unit to a parent by name.";
        if(table.equals("appointments"))return "Keep each appointment as a separate dated record to preserve history.";
        if(table.equals("delegations"))return "Delegation scope is recorded explicitly; it does not grant app-wide access control by itself.";
        if(table.equals("visits"))return "Visit history is limited to the five newest records per member.";
        return "Track priorities, assignments, follow-up dates and care resolution.";
    }
    private String singular(String table) {
        if(table.equals("members"))return "member"; if(table.equals("organizations"))return "organization";
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
        if(table.equals("organizations"))return join(get(c,"parent_id").isEmpty()?"Top-level organization":"Parent: "+database.findNameById("organizations",get(c,"parent_id")),get(c,"location"),get(c,"notes"));
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
        if(table.equals("organizations"))return join(safe(v,"parent_id").isEmpty()?"Top-level organization":"Parent: "+database.findNameById("organizations",safe(v,"parent_id")),safe(v,"location"),safe(v,"notes"));
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
        base("Sync queue & status","Local data remains available without internet. Cloud synchronization is not connected yet.");
        TextView summary=new TextView(this); summary.setText("Records on this device\nMembers: "+database.count("members")+"\nOrganizations: "+database.count("organizations")+"\nAppointments: "+database.count("appointments")+"\nDelegations: "+database.count("delegations")+"\nVisits: "+database.count("visits")+"\nCare cases: "+database.count("care_records")+"\n\nPending change events: "+database.pendingChanges()+"\n\nNo data has been uploaded. Google Drive integration must be configured and tested before synchronization can run."); summary.setTextColor(ink); summary.setTextSize(15); summary.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(summary);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Cursor c=database.syncQueue(); try {while(c.moveToNext()){TextView row=new TextView(this);row.setText("#"+c.getLong(0)+" • "+c.getString(1)+" • "+c.getString(2)+"\n"+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date(c.getLong(3)))+" • "+c.getString(4)+(c.getString(5).isEmpty()?"":"\n"+c.getString(5)));row.setTextColor(muted);row.setPadding(dp(10),dp(9),dp(10),dp(9));row.setBackgroundColor(0xFFFFFFFF);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(5);list.addView(row,p);}}finally{c.close();}
    }

    private void showAbout() {
        String version="Version details unavailable";
        try { PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0); version="Version "+p.versionName+" ("+p.getLongVersionCode()+")\nPackage: "+getPackageName(); }
        catch(PackageManager.NameNotFoundException ignored){}
        new AlertDialog.Builder(this).setTitle("About ChurchCare").setMessage(version+"\n\nOffline-first church and member care. SQLite is the local source of truth.\n\nCurrent milestone: operational local records and change queue. Google Drive sync and enforced account-level access control are not yet connected.").setPositiveButton("OK",null).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
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
    @Override public void onBackPressed(){showHome();}
    @Override protected void onDestroy(){if(database!=null)database.close();super.onDestroy();}
}
