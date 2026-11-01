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
import android.widget.RadioGroup;
import android.widget.RadioButton;
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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
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
    private static final int FIREBASE_GOOGLE_SIGN_IN = 7108;
    private String pendingDriveAction = "";
    private String photoRecordId;
    private String photoColumn;

    private interface SetupLevelAdder { void add(String initial); }

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
        new Field("Position code (stable key, e.g. HEAD_OF_FOLLOW_UP)", "position_code", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, false),
        new Field("Appointed person Firebase UID (optional)", "person_uid", InputType.TYPE_CLASS_TEXT, false),
        new Field("Church branch / unit", "organization_name", TEXT, false),
        new Field("Start date (YYYY-MM-DD)", "start_date", DATE, false),
        new Field("End date (blank if current)", "end_date", DATE, false),
        new Field("Status (Current / Completed)", "status", TEXT, false),
        new Field("Notes", "notes", TEXT, false)
    };
    private static final Field[] DELEGATION = {
        new Field("Responsible overseer", "overseer", TEXT, true),
        new Field("Overseer Firebase UID (optional)", "overseer_uid", InputType.TYPE_CLASS_TEXT, false),
        new Field("Delegate name", "delegate_name", TEXT, true),
        new Field("Delegate Firebase UID (optional)", "delegate_uid", InputType.TYPE_CLASS_TEXT, false),
        new Field("Church branch / unit", "organization_name", TEXT, false),
        new Field("Granted scope / authority", "scope", TEXT, true),
        new Field("Permission keys (JSON array; optional)", "permissions_json", InputType.TYPE_CLASS_TEXT, false),
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

    private String[] setupTemplateNames = new String[]{"My church is not listed — add it"};
    private String[] setupTemplateIds = new String[]{"custom"};
    private int[] setupTemplateVersions = new int[]{0};
    private String[][] setupTemplateLevels = new String[][]{new String[0]};
    private String[][] setupTemplateLevelChoices = new String[][]{new String[0]};

    private void loadChurchTemplateOptions() {
        java.util.ArrayList<String> names=new java.util.ArrayList<>();
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        java.util.ArrayList<Integer> versions=new java.util.ArrayList<>();
        java.util.ArrayList<String[]> structures=new java.util.ArrayList<>();
        java.util.ArrayList<String[]> choices=new java.util.ArrayList<>();
        names.add("My church is not listed — add it");
        ids.add("custom"); versions.add(0); structures.add(new String[0]); choices.add(new String[0]);
        try (InputStream in=getAssets().open("church-organisation-templates.v1.json")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
            while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            JSONObject catalog=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            JSONArray templates=catalog.getJSONArray("templates");
            for(int i=0;i<templates.length();i++) {
                JSONObject item=templates.getJSONObject(i);
                String id=item.optString("id","");
                if("custom".equals(id)) continue;
                names.add(item.optString("name", "Church"));
                ids.add(id); versions.add(item.optInt("version",1));
                java.util.ArrayList<String> ordered=new java.util.ArrayList<>();
                JSONArray levels=item.optJSONArray("levelsHighestToLowest");
                if(levels!=null) for(int j=0;j<levels.length();j++) ordered.add(levels.optString(j));
                java.util.ArrayList<String> available=new java.util.ArrayList<>(ordered);
                if(available.isEmpty()) {
                    JSONArray suggested=item.optJSONArray("suggestedLevelNames");
                    if(suggested!=null) for(int j=0;j<suggested.length();j++) available.add(suggested.optString(j));
                }
                structures.add(ordered.toArray(new String[0]));
                choices.add(available.toArray(new String[0]));
            }
        } catch(Exception error) { android.util.Log.e("ChurchCare","Could not load template catalogue",error); }
        setupTemplateNames=names.toArray(new String[0]); setupTemplateIds=ids.toArray(new String[0]);
        setupTemplateVersions=new int[versions.size()]; setupTemplateLevels=structures.toArray(new String[0][]);
        setupTemplateLevelChoices=choices.toArray(new String[0][]);
        for(int i=0;i<versions.size();i++) setupTemplateVersions[i]=versions.get(i);
    }

    private void showChurchSetup() {
        loadChurchTemplateOptions();
        base("Create your church profile", "Choose your church, define its structure and name your starting branch or unit");

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView intro=new TextView(this);
        intro.setText("A church can start using ChurchCare at any level. Its other levels and branches can join later without changing this church's identity.");
        intro.setTextColor(ink); intro.setTextSize(15); intro.setPadding(dp(12),dp(10),dp(12),dp(12)); form.addView(intro);

        TextView churchLabel=new TextView(this); churchLabel.setText("1. Pick your church"); churchLabel.setTextColor(blue); churchLabel.setTextSize(15); churchLabel.setTypeface(null,1); form.addView(churchLabel);
        Spinner templates=new Spinner(this);
        templates.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,setupTemplateNames));
        form.addView(templates);

        EditText customChurch=new EditText(this); customChurch.setSingleLine(true); customChurch.setHint("Enter your church's name");
        customChurch.setVisibility(View.GONE); form.addView(customChurch);

        TextView levelLabel=new TextView(this); levelLabel.setText("2. Define your church's structure (highest level first)"); levelLabel.setTextColor(blue); levelLabel.setTextSize(15); levelLabel.setTypeface(null,1);
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,-2); llp.topMargin=dp(12); form.addView(levelLabel,llp);
        TextView structureHelp=new TextView(this); structureHelp.setTextColor(muted); structureHelp.setTextSize(12);
        structureHelp.setText("Add each organisational level in order, from the highest level down to the local congregation or unit. These are levels, not leadership titles.");
        form.addView(structureHelp);
        LinearLayout levelRows=new LinearLayout(this); levelRows.setOrientation(LinearLayout.VERTICAL); form.addView(levelRows);
        Button addLevel=new Button(this); addLevel.setText("＋ Add another level"); addLevel.setAllCaps(false); form.addView(addLevel);
        TextView startingLevelLabel=new TextView(this); startingLevelLabel.setText("Pick your church level"); startingLevelLabel.setTextColor(blue); startingLevelLabel.setTypeface(null,1); form.addView(startingLevelLabel);
        RadioGroup levelRadioGroup=new RadioGroup(this); levelRadioGroup.setOrientation(LinearLayout.VERTICAL); form.addView(levelRadioGroup);

        EditText customLevel=new EditText(this); customLevel.setSingleLine(true); customLevel.setHint("Enter a level name");
        customLevel.setVisibility(View.GONE); form.addView(customLevel);

        TextView unitLabel=new TextView(this); unitLabel.setText("3. Name the branch or unit being registered"); unitLabel.setTextColor(blue); unitLabel.setTextSize(15); unitLabel.setTypeface(null,1);
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(-1,-2); ulp.topMargin=dp(12); form.addView(unitLabel,ulp);
        EditText unit=new EditText(this); unit.setSingleLine(true); unit.setHint("e.g. Charity Assembly"); form.addView(unit);

        final String otherChoice="Other — enter a level";
        final java.util.ArrayList<EditText> customLevelFields=new java.util.ArrayList<>();
        final boolean[] customMode={true};

        Runnable refreshCustomLevels=()->{
            java.util.ArrayList<String> names=new java.util.ArrayList<>();
            for(EditText field:customLevelFields) {
                String value=field.getText().toString().trim();
                if(!value.isEmpty()) names.add(value);
            }
            String previouslySelected=levelRadioGroup.getCheckedRadioButtonId()==View.NO_ID?"":String.valueOf(((RadioButton)findViewById(levelRadioGroup.getCheckedRadioButtonId())).getText());
            levelRadioGroup.removeAllViews();
            for(String name:names) {
                RadioButton option=new RadioButton(MainActivity.this);
                option.setId(View.generateViewId());
                option.setText(name);
                option.setTextColor(ink);
                option.setTextSize(15);
                option.setPadding(dp(4),dp(5),dp(4),dp(5));
                levelRadioGroup.addView(option);
                if(name.equals(previouslySelected)) option.setChecked(true);
            }
            if(levelRadioGroup.getCheckedRadioButtonId()==View.NO_ID && !names.isEmpty())
                ((RadioButton)levelRadioGroup.getChildAt(0)).setChecked(true);
        };

        SetupLevelAdder addCustomLevel=(initial)->{
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            EditText field=new EditText(this); field.setSingleLine(true); field.setHint("Level name (e.g. District)");
            field.setText(initial==null?"":initial);
            row.addView(field,new LinearLayout.LayoutParams(0,-2,1));
            Button up=new Button(this); up.setText("↑"); up.setAllCaps(false);
            Button down=new Button(this); down.setText("↓"); down.setAllCaps(false);
            Button remove=new Button(this); remove.setText("×"); remove.setAllCaps(false);
            row.addView(up); row.addView(down); row.addView(remove);
            int index=customLevelFields.size(); customLevelFields.add(field); levelRows.addView(row);
            field.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
                public void onTextChanged(CharSequence s,int st,int before,int count) { refreshCustomLevels.run(); }
                public void afterTextChanged(android.text.Editable e) {}
            });
            up.setOnClickListener(v->{
                int at=customLevelFields.indexOf(field); if(at<=0)return;
                EditText previous=customLevelFields.get(at-1);
                String a=field.getText().toString(), b=previous.getText().toString();
                field.setText(b); previous.setText(a);
            });
            down.setOnClickListener(v->{
                int at=customLevelFields.indexOf(field); if(at<0||at>=customLevelFields.size()-1)return;
                EditText next=customLevelFields.get(at+1);
                String a=field.getText().toString(), b=next.getText().toString();
                field.setText(b); next.setText(a);
            });
            remove.setOnClickListener(v->{
                if(customLevelFields.size()<=1) { field.setText(""); return; }
                customLevelFields.remove(field); levelRows.removeView(row); refreshCustomLevels.run();
            });
            refreshCustomLevels.run();
        };

        addLevel.setOnClickListener(v->addCustomLevel.add(""));
        addCustomLevel.add("");

        templates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) {
                boolean addingChurch=position==0;
                customChurch.setVisibility(addingChurch?View.VISIBLE:View.GONE);
                customMode[0]=addingChurch;
                levelRows.removeAllViews(); customLevelFields.clear();
                if(addingChurch) {
                    addCustomLevel.add("");
                    addLevel.setVisibility(View.VISIBLE); levelRows.setVisibility(View.VISIBLE);
                    structureHelp.setText("For a church you add yourself, enter every level from highest to lowest. Use the arrows to change the order and × to remove a level.");
                    startingLevelLabel.setText("Pick your church level");
                    levelRadioGroup.setVisibility(View.VISIBLE); customLevel.setVisibility(View.GONE);
                } else {
                    String[] known=setupTemplateLevels[position];
                    String[] choices=setupTemplateLevelChoices[position];
                    if(known.length>0) {
                        for(String name:known) addCustomLevel.add(name);
                        addLevel.setVisibility(View.VISIBLE); levelRows.setVisibility(View.VISIBLE);
                        structureHelp.setText("This is a suggested starting structure for the selected church. You can rename, add, remove or reorder levels for its local configuration.");
                        startingLevelLabel.setText("Pick your church level");
                        customLevel.setVisibility(View.GONE);
                    } else {
                        addCustomLevel.add("");
                        addLevel.setVisibility(View.VISIBLE); levelRows.setVisibility(View.VISIBLE);
                        structureHelp.setText("The catalogue does not confirm a complete order for this church. Define the actual order used by your congregation; suggested names are available in the catalogue notes.");
                        startingLevelLabel.setText("Pick your church level");
                        customLevel.setVisibility(View.GONE);
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        Button create=new Button(this); create.setText("Review and create ChurchCare profile"); create.setAllCaps(false); form.addView(create);
        create.setOnClickListener(v -> {
            int pos=templates.getSelectedItemPosition();
            boolean addingChurch=pos==0;
            String churchName=addingChurch?customChurch.getText().toString().trim():setupTemplateNames[pos];
            String unitName=unit.getText().toString().trim();
            JSONArray structure=new JSONArray();
            java.util.ArrayList<String> ordered=new java.util.ArrayList<>();
            java.util.HashSet<String> unique=new java.util.HashSet<>();
            for(EditText field:customLevelFields) {
                String name=field.getText().toString().trim();
                if(name.isEmpty()) continue;
                if(!unique.add(name.toLowerCase(Locale.ROOT))) {
                    Toast.makeText(this,"Each church structure level must have a distinct name.",Toast.LENGTH_LONG).show(); return;
                }
                ordered.add(name); structure.put(name);
            }
            int selectedLevelId=levelRadioGroup.getCheckedRadioButtonId();
            String startingLevel=selectedLevelId==View.NO_ID?"":String.valueOf(((RadioButton)findViewById(selectedLevelId)).getText());
            if(churchName.isEmpty()||unitName.isEmpty()||ordered.isEmpty()||startingLevel.isEmpty()) {
                Toast.makeText(this,"Enter the church name, define at least one level, select the starting level and name the branch/unit.",Toast.LENGTH_LONG).show(); return;
            }
            if(!ordered.contains(startingLevel)) {
                Toast.makeText(this,"Select a starting level from the structure you defined.",Toast.LENGTH_LONG).show(); return;
            }
            String templateId=addingChurch?"custom":setupTemplateIds[pos];
            int templateVersion=addingChurch?0:setupTemplateVersions[pos];
            StringBuilder structureText=new StringBuilder();
            for(int i=0;i<ordered.size();i++) structureText.append(i+1).append(". ").append(ordered.get(i)).append("\n");
            new AlertDialog.Builder(this).setTitle("Review your church profile")
                .setMessage("Church: "+churchName+"\n\nChurch structure (highest to lowest):\n"+structureText+"\nStarting level: "+startingLevel+"\nBranch / unit: "+unitName+"\n\nThis branch can begin independently. Other levels of the same church can join later, and this setup does not connect it to a different church.")
                .setNegativeButton("Edit",null).setPositiveButton("Confirm",(dialog,which)->{
                    try { database.saveChurchSetup(churchName,unitName,startingLevel,templateId,structure.toString(),templateVersion); showHome(); }
                    catch(Exception e) { android.util.Log.e("ChurchCare","Setup failed",e); Toast.makeText(this,"Could not save setup. Please try again.",Toast.LENGTH_LONG).show(); }
                }).show();
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
        menuButton(menu,"People & connections","Connect church members with pastors, Follow-up leaders and teams",this::showPeopleConnections);
        menuButton(menu,"Members","Register, search, update and review member records",()->showRecords("members"));
        menuButton(menu,"Church branches & units","Build and view your church structure at every level",()->showRecords("organizations"));
        menuButton(menu,"Appointments & transfers","Track roles and retain previous appointments",()->showRecords("appointments"));
        menuButton(menu,"Leadership & governance","Manage reporting lines, pastoral delegates and team appointments",this::showGovernance);
        menuButton(menu,"Delegation & authority","Define who can act, for whom, and within what scope",()->showRecords("delegations"));
        menuButton(menu,"Visits & follow-ups","Record pastoral visits and next steps",()->showRecords("visits"));
        menuButton(menu,"Care cases & escalation","Track care needs, priorities, assignments and resolution",()->showRecords("care_records"));
        menuButton(menu,"Sync queue & data status","Review local changes awaiting a future cloud sync ("+pending+" pending)",this::showSync);
        menuButton(menu,"Settings / Account","Google account, app version and local storage",this::showSettings);
        scroll.addView(menu); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=new TextView(this); footer.setText("Offline ready • SQLite local database • Cloud sync not connected"); footer.setTextColor(muted); footer.setTextSize(12); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2); fp.topMargin=dp(8); root.addView(footer,fp);
    }

    /** Independent people directory and cross-unit/cross-department connection workspace. */
    private void showPeopleConnections() {
        base("People & connections","People are independent of membership, appointments and app accounts");
        TextView note=new TextView(this);
        note.setText("Add a person even if they are not a member of this parish or a ChurchCare user. Record relevant upward, downward or colleague connections across departments and units. A proposal is local only and does not mean the other person has accepted it or grant access to private records.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(12),dp(10),dp(12),dp(10)); note.setBackgroundColor(0xFFFFFFFF);
        root.addView(note);
        Button add=new Button(this); add.setText("+ Add a person"); add.setAllCaps(false);
        add.setOnClickListener(v->showAddPersonDialog()); root.addView(add);
        Button reviewLegacy=new Button(this); reviewLegacy.setText("Review unmatched legacy connections"); reviewLegacy.setAllCaps(false);
        reviewLegacy.setOnClickListener(v->showLegacyConnectionReview()); root.addView(reviewLegacy);
        EditText search=new EditText(this); search.setSingleLine(true); search.setHint("Search by name, phone or email"); root.addView(search);
        Button find=new Button(this); find.setText("Search people"); find.setAllCaps(false); root.addView(find);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{
            list.removeAllViews();
            try(Cursor c=database.listPeople(search.getText().toString())) {
                boolean found=false;
                while(c.moveToNext()) {
                    found=true;
                    String id=get(c,"id"), name=get(c,"full_name"), phone=get(c,"phone"), email=get(c,"email");
                    Button item=new Button(this); item.setAllCaps(false); item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                    item.setText(name+"\n"+join(phone,email)+"\nView connections");
                    item.setOnClickListener(v->showPersonRelationships(id,name));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(5); list.addView(item,lp);
                }
                if(!found) {
                    TextView empty=new TextView(this);
                    empty.setText("No people found. Add a person to start building your connection network.");
                    empty.setTextColor(muted); empty.setPadding(dp(12),dp(18),dp(12),dp(18)); list.addView(empty);
                }
            }
        };
        find.setOnClickListener(v->render.run());
        search.setOnEditorActionListener((v,action,event)->{render.run();return true;});
        render.run();
    }

    /** Manual, explicit matching for legacy appointments that could not be mapped by name. */
    private void showLegacyConnectionReview() {
        base("Unmatched legacy connections","Review old links that need a deliberate person match");
        TextView note=new TextView(this);
        note.setText("ChurchCare could not safely identify the person behind these older appointments. Select the correct person manually. No name-based guess is made, original legacy records are preserved, and matching does not mean the connection was accepted by both participants.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(12),dp(10),dp(12),dp(10)); note.setBackgroundColor(0xFFFFFFFF);
        root.addView(note);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(Cursor c=database.listUnresolvedLegacyAppointments()) {
            boolean found=false;
            while(c.moveToNext()) {
                found=true;
                String appointmentId=get(c,"appointment_id");
                String recordedName=get(c,"person_name");
                String role=get(c,"role");
                String organization=get(c,"organization_name");
                String count=get(c,"legacy_count");
                Button item=new Button(this); item.setAllCaps(false); item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                item.setText(recordedName+"\n"+role+" — "+organization+"\n"+count+" legacy connection(s) awaiting a match");
                item.setOnClickListener(v->chooseLegacyAppointmentPerson(appointmentId,recordedName,role,organization,count));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(6); list.addView(item,lp);
            }
            if(!found) {
                TextView empty=new TextView(this);
                empty.setText("No unmatched legacy connections remain in this database.");
                empty.setTextColor(muted); empty.setPadding(dp(12),dp(18),dp(12),dp(18)); list.addView(empty);
            }
        } catch(Exception error) {
            TextView problem=new TextView(this);
            problem.setText("Could not load unmatched legacy connections: "+error.getMessage());
            problem.setTextColor(0xFFB00020); list.addView(problem);
        }
    }

    private void chooseLegacyAppointmentPerson(String appointmentId,String recordedName,String role,String organization,String legacyCount) {
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        try(Cursor c=database.listPeople("")) {
            while(c.moveToNext()) {
                ids.add(get(c,"id"));
                labels.add(get(c,"full_name")+" — "+join(get(c,"phone"),get(c,"email")));
            }
        }
        if(ids.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No people available")
                .setMessage("Add the correct person to the People directory first, then return here to match this legacy appointment.")
                .setPositiveButton("Add a person",(d,w)->showAddPersonDialog()).setNegativeButton("Cancel",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Match legacy appointment")
            .setMessage("Old record: "+recordedName+"\nAppointment: "+role+" — "+organization+"\nLegacy connections: "+legacyCount+"\n\nChoose the person who actually held this appointment.")
            .setItems(labels.toArray(new String[0]),(dialog,which)->{
                String selectedId=ids.get(which);
                String selectedName=labels.get(which);
                new AlertDialog.Builder(this).setTitle("Confirm person match")
                    .setMessage("You selected:\n"+selectedName+"\n\nThis will link the appointment recorded as “"+recordedName+"” to this person and migrate its legacy connection rows into the new relationship view. The original records remain preserved. These connections will be marked LEGACY_RECORDED, not accepted.")
                    .setNegativeButton("Go back",null).setPositiveButton("Confirm match",(d,w)->{
                        try {
                            int migrated=database.resolveLegacyAppointmentPerson(appointmentId,selectedId);
                            Toast.makeText(this,"Appointment matched. "+migrated+" legacy connection record(s) retained and linked.",Toast.LENGTH_LONG).show();
                            showLegacyConnectionReview();
                        } catch(Exception error) {
                            Toast.makeText(this,"Could not match appointment: "+error.getMessage(),Toast.LENGTH_LONG).show();
                            showLegacyConnectionReview();
                        }
                    }).show();
            }).setNegativeButton("Cancel",null).show();
    }

    private void showAddPersonDialog() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("Full name (required)"); form.addView(name);
        EditText phone=new EditText(this); phone.setSingleLine(true); phone.setHint("Phone number (optional)"); phone.setInputType(InputType.TYPE_CLASS_PHONE); form.addView(phone);
        EditText email=new EditText(this); email.setSingleLine(true); email.setHint("Email address (optional)"); email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); form.addView(email);
        new AlertDialog.Builder(this).setTitle("Add a person").setMessage("This creates a person identity only. It does not register parish membership or create a ChurchCare account.")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                try {
                    String id=database.addPersonIdentity(name.getText().toString(),phone.getText().toString(),email.getText().toString());
                    Toast.makeText(this,"Person saved on this device",Toast.LENGTH_SHORT).show();
                    showPersonRelationships(id,name.getText().toString().trim());
                } catch(Exception error) {
                    Toast.makeText(this,"Could not save person: "+error.getMessage(),Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void showPersonRelationships(String personId,String personName) {
        base("People connected to",personName);
        TextView note=new TextView(this);
        note.setText("Connections may be upward, downward or lateral, and may cross church units or departments. A proposal remains unaccepted until the other participant can independently verify and respond.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(12),dp(10),dp(12),dp(10)); note.setBackgroundColor(0xFFFFFFFF); root.addView(note);
        Button add=new Button(this); add.setText("+ Propose a connection"); add.setAllCaps(false);
        add.setOnClickListener(v->choosePersonConnection(personId,personName)); root.addView(add);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(Cursor c=database.listPersonRelationships(personId)) {
            if(!c.moveToFirst()) {
                TextView empty=new TextView(this); empty.setText("No connections recorded yet. Propose an overseer, someone under this person's responsibility, or a relevant colleague/coordinator.");
                empty.setTextColor(muted); empty.setPadding(dp(12),dp(18),dp(12),dp(18)); list.addView(empty);
            } else do {
                String source=get(c,"source_name"), target=get(c,"target_name");
                String type=get(c,"relationship_type"), label=get(c,"relationship_label"), status=get(c,"status");
                String sourceRole=get(c,"source_appointment_role"), sourceOrg=get(c,"source_organization_name");
                String targetRole=get(c,"target_appointment_role"), targetOrg=get(c,"target_organization_name");
                String sourceContext=sourceRole.isEmpty()?"Person-level":sourceRole+" — "+sourceOrg;
                String targetContext=targetRole.isEmpty()?"Person-level":targetRole+" — "+targetOrg;
                TextView item=new TextView(this); item.setText(source+"  →  "+target+"\n"+sourceContext+"  →  "+targetContext+"\n"+type+(label.isEmpty()?"":" • "+label)+"\nStatus: "+status.replace('_',' '));
                item.setTextColor(ink); item.setTextSize(14); item.setPadding(dp(12),dp(12),dp(12),dp(12)); item.setBackgroundColor(0xFFFFFFFF);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(6); list.addView(item,lp);
            } while(c.moveToNext());
        }
    }

    private void choosePersonConnection(String personId,String personName) {
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        try(Cursor c=database.listPeople("")) {
            while(c.moveToNext()) {
                String id=get(c,"id");
                if(id.equals(personId)) continue;
                ids.add(id); labels.add(get(c,"full_name")+" — "+join(get(c,"phone"),get(c,"email")));
            }
        }
        if(ids.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Add another person first")
                .setMessage("A connection needs two separate people. Add the other person to the directory before creating the relationship.")
                .setPositiveButton("Add person",(d,w)->showAddPersonDialog()).setNegativeButton("Cancel",null).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("Choose the other person")
            .setItems(labels.toArray(new String[0]),(dialog,which)->{
                String targetId=ids.get(which), targetName=labels.get(which);
                String[] types={"OVERSEER — person responsible for them","RESPONSIBILITY — person under their responsibility","COLLEAGUE — relevant colleague or coordinator","OTHER — another working relationship"};
                new AlertDialog.Builder(this).setTitle("What is the relationship?")
                    .setItems(types,(d,w)->{
                        EditText detail=new EditText(this); detail.setSingleLine(true); detail.setHint("Optional context (e.g. Area Follow-up Coordinator)");
                        String type=new String[]{"OVERSEER","RESPONSIBILITY","COLLEAGUE","OTHER"}[w];
                        new AlertDialog.Builder(this).setTitle("Describe the connection").setView(detail)
                            .setNegativeButton("Cancel",null).setPositiveButton("Propose",(dd,ww)->{
                                FirebaseUser actor=FirebaseAuth.getInstance().getCurrentUser();
                                try {
                                    String sourceId="OVERSEER".equals(type)?targetId:personId;
                                    String destinationId="OVERSEER".equals(type)?personId:targetId;
                                    chooseAppointmentForPerson(sourceId,"Source — "+(sourceId.equals(personId)?personName:targetName),sourceAppointmentId ->
                                        chooseAppointmentForPerson(destinationId,"Target — "+(destinationId.equals(personId)?personName:targetName),targetAppointmentId -> {
                                            try {
                                                database.proposePersonRelationship(sourceId,destinationId,sourceAppointmentId,targetAppointmentId,type,detail.getText().toString(),null,actor==null?null:actor.getUid());
                                                Toast.makeText(this,"Connection proposal saved locally; recipient acceptance is not yet available.",Toast.LENGTH_LONG).show();
                                                showPersonRelationships(personId,personName);
                                            } catch(Exception saveError) {
                                                Toast.makeText(this,"Could not save proposal: "+saveError.getMessage(),Toast.LENGTH_LONG).show();
                                            }
                                        }));
                                } catch(Exception error) {
                                    Toast.makeText(this,"Could not save proposal: "+error.getMessage(),Toast.LENGTH_LONG).show();
                                }
                            }).show();
                    }).setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Cancel",null).show();
    }

    private interface AppointmentChoice { void selected(String appointmentId); }

    /** Appointment selection is optional: relationships may remain person-level. */
    private void chooseAppointmentForPerson(String personId,String heading,AppointmentChoice callback) {
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        ids.add(null); labels.add("Person-level connection (not tied to an appointment)");
        try(Cursor c=database.listPersonAppointments(personId)) {
            while(c.moveToNext()) {
                ids.add(get(c,"id"));
                labels.add(get(c,"role")+" — "+get(c,"organization_name"));
            }
        }
        new AlertDialog.Builder(this).setTitle(heading)
            .setMessage("Choose the appointment relevant to this end of the connection, or keep it at person level.")
            .setItems(labels.toArray(new String[0]),(dialog,which)->callback.selected(ids.get(which)))
            .setNegativeButton("Cancel",null).show();
    }

    /** Local governance workspace. These records do not grant cloud or individual-record permissions. */
    private void showGovernance() {
        base("Leadership & governance","Appointments, reporting relationships and delegated pastoral oversight");
        TextView note=new TextView(this);
        note.setText("Manage the church's leadership structure here. Reporting links are local governance records only; they do not grant access to member or care records, and cloud permissions remain disabled.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(12),dp(10),dp(12),dp(10));
        note.setBackgroundColor(0xFFFFFFFF); root.addView(note);
        Button add=new Button(this); add.setText("+ Add leadership appointment"); add.setAllCaps(false);
        add.setOnClickListener(v->showForm("appointments",null,null)); root.addView(add);
        Button delegates=new Button(this); delegates.setText("Manage pastoral delegates"); delegates.setAllCaps(false);
        delegates.setOnClickListener(v->showPastoralDelegates()); root.addView(delegates);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(Cursor c=database.listCurrentGovernanceAppointments()) {
            if(!c.moveToFirst()) {
                TextView empty=new TextView(this); empty.setText("No current leadership appointments yet. Add an appointment to begin building the reporting structure.");
                empty.setTextColor(muted); empty.setPadding(dp(12),dp(18),dp(12),dp(18)); list.addView(empty);
            } else do {
                String id=c.getString(c.getColumnIndexOrThrow("id"));
                String person=c.getString(c.getColumnIndexOrThrow("person_name"));
                String role=c.getString(c.getColumnIndexOrThrow("role"));
                String org=c.getString(c.getColumnIndexOrThrow("organization_name"));
                Button item=new Button(this); item.setAllCaps(false); item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                item.setText(person+" — "+role+"\n"+org+"\nTap to manage reporting");
                item.setOnClickListener(v->showReportingManagement(id,person,role));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(5); list.addView(item,p);
            } while(c.moveToNext());
        }
    }

    /** Dedicated local interface for pastoral delegation identity, scope and status. */
    private void showPastoralDelegates() {
        base("Pastoral delegates","Record who has been entrusted with oversight, and the scope and duration of that delegation");
        TextView note=new TextView(this);
        note.setText("Delegations are recorded locally. Firebase UIDs identify the overseer and delegate where available. A delegation record does not yet grant access to member or care records, and no cloud permissions are enabled.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(12),dp(10),dp(12),dp(10)); note.setBackgroundColor(0xFFFFFFFF); root.addView(note);
        Button add=new Button(this); add.setText("+ Record pastoral delegation"); add.setAllCaps(false);
        add.setOnClickListener(v->showForm("delegations",null,null)); root.addView(add);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(Cursor c=database.listPastoralDelegationAppointmentLinks()) {
            boolean found=false;
            while(c.moveToNext()) {
                found=true;
                String id=get(c,"id"), overseer=get(c,"overseer"), delegate=get(c,"delegate_name");
                String scope=get(c,"scope"), status=get(c,"status"), orgId=get(c,"organization_id");
                String org=orgId.isEmpty()?"Church-wide / unspecified":database.findNameById("organizations",orgId);
                String overseerLink=delegationAppointmentStatus(c,"overseer");
                String delegateLink=delegationAppointmentStatus(c,"delegate");
                Button item=new Button(this); item.setAllCaps(false); item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                item.setText(overseer+" → "+delegate+"\n"+scope+"\n"+org+" • "+status+
                    "\nOverseer: "+overseerLink+"\nDelegate: "+delegateLink+"\nTap to review or edit");
                item.setOnClickListener(v->{
                    ContentValues snapshot=new ContentValues();
                    try(Cursor rows=database.listRecords("delegations")) {
                        while(rows.moveToNext()) if(id.equals(get(rows,"id"))) {
                            for(int i=0;i<rows.getColumnCount();i++) if(!rows.isNull(i)) snapshot.put(rows.getColumnName(i),rows.getString(i));
                            break;
                        }
                    }
                    if(snapshot.size()>0) new AlertDialog.Builder(this).setTitle("Pastoral delegation")
                        .setMessage(overseer+" → "+delegate+"\n\nScope: "+scope+"\nOrganisation: "+org+"\nStatus: "+status+
                            "\n\nOverseer UID: "+safe(snapshot,"overseer_uid")+"\nDelegate UID: "+safe(snapshot,"delegate_uid")+
                            "\nPermissions: "+safe(snapshot,"permissions_json")+
                            "\n\nAppointment matching is informational only. Multiple matches are not auto-selected and this does not grant access.")
                        .setPositiveButton("Edit",(d,w)->showForm("delegations",id,snapshot))
                        .setNegativeButton("Close",null).show();
                });
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(5); list.addView(item,p);
            }
            if(!found){TextView empty=new TextView(this);empty.setText("No pastoral delegations recorded yet.");empty.setTextColor(muted);empty.setPadding(dp(12),dp(18),dp(12),dp(18));list.addView(empty);}
        }
    }

    private String delegationAppointmentStatus(Cursor row,String endpoint) {
        String uid=get(row,endpoint+"_uid");
        int count=0;
        String label="";
        try { count=row.getInt(row.getColumnIndexOrThrow(endpoint+"_appointment_count")); } catch(Exception ignored) {}
        try { label=get(row,endpoint+"_appointment_label"); } catch(Exception ignored) {}
        if(uid.isEmpty()) return "no Firebase UID linked";
        if(count==0) return "no current appointment matched to UID";
        if(count>1) return "ambiguous — "+count+" current appointments match this UID";
        return label.isEmpty()?"one current appointment matched":label;
    }

    private void showReportingManagement(String appointmentId,String personName,String role) {
        base("Reporting relationships",personName+" • "+role);
        TextView note=new TextView(this);
        note.setText("A person may report to more than one appointment. Automatic organisation-level Follow-up links are maintained by the hierarchy and cannot be removed here. Additional pastoral links can be added separately.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(10),dp(8),dp(10),dp(8)); root.addView(note);
        Button add=new Button(this); add.setText("+ Add reporting relationship"); add.setAllCaps(false);
        add.setOnClickListener(v->chooseReportingParent(appointmentId)); root.addView(add);
        Button editAppointment=new Button(this); editAppointment.setText("Edit this appointment"); editAppointment.setAllCaps(false);
        editAppointment.setOnClickListener(v->{
            ContentValues snapshot=new ContentValues();
            try(Cursor c=database.listRecords("appointments")) {
                while(c.moveToNext()) if(appointmentId.equals(c.getString(c.getColumnIndexOrThrow("id")))) {
                    for(int i=0;i<c.getColumnCount();i++) if(!c.isNull(i)) snapshot.put(c.getColumnName(i),c.getString(i));
                    break;
                }
            }
            if(snapshot.size()>0) showForm("appointments",appointmentId,snapshot);
        }); root.addView(editAppointment);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(Cursor c=database.listReportingForAppointment(appointmentId)) {
            if(!c.moveToFirst()) {
                TextView empty=new TextView(this); empty.setText("No active reporting relationships recorded for this appointment.");
                empty.setTextColor(muted); empty.setPadding(dp(12),dp(18),dp(12),dp(18)); list.addView(empty);
            } else do {
                String linkId=c.getString(c.getColumnIndexOrThrow("id"));
                String type=c.getString(c.getColumnIndexOrThrow("relationship_type"));
                String parent=c.getString(c.getColumnIndexOrThrow("person_name"));
                String parentRole=c.getString(c.getColumnIndexOrThrow("role"));
                String org=c.getString(c.getColumnIndexOrThrow("organization_name"));
                Button link=new Button(this); link.setAllCaps(false); link.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                link.setText(parent+" — "+parentRole+"\n"+org+" • "+("AUTO_ORG_PARENT".equals(type)?"Automatic hierarchy":"Additional reporting"));
                if("DIRECT".equals(type)) link.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Remove reporting relationship?")
                    .setMessage("This retires only the additional reporting link. It does not delete either appointment.")
                    .setNegativeButton("Keep",null)
                    .setPositiveButton("Remove",(d,w)->{
                        FirebaseUser actor=FirebaseAuth.getInstance().getCurrentUser();
                        if(actor==null){Toast.makeText(this,"Sign in with Google before changing governance records.",Toast.LENGTH_LONG).show();return;}
                        try { database.removeExplicitReportingRelationship(linkId,actor.getUid()); Toast.makeText(this,"Reporting link retired",Toast.LENGTH_SHORT).show(); showReportingManagement(appointmentId,personName,role); }
                        catch(Exception error){Toast.makeText(this,"Could not remove the reporting link: "+error.getMessage(),Toast.LENGTH_LONG).show();}
                    }).show());
                else link.setEnabled(false);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(5); list.addView(link,p);
            } while(c.moveToNext());
        }
    }

    private void chooseReportingParent(String appointmentId) {
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        try(Cursor c=database.listCurrentGovernanceAppointments()) {
            while(c.moveToNext()) {
                String id=c.getString(c.getColumnIndexOrThrow("id"));
                if(id.equals(appointmentId)) continue;
                String person=c.getString(c.getColumnIndexOrThrow("person_name"));
                String role=c.getString(c.getColumnIndexOrThrow("role"));
                String org=c.getString(c.getColumnIndexOrThrow("organization_name"));
                ids.add(id); labels.add(person+" — "+role+" ("+org+")");
            }
        }
        if(ids.isEmpty()){Toast.makeText(this,"Add another current leadership appointment first.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Choose who this appointment reports to")
            .setItems(labels.toArray(new String[0]),(dialog,which)->{
                FirebaseUser actor=FirebaseAuth.getInstance().getCurrentUser();
                if(actor==null){Toast.makeText(this,"Sign in with Google before recording a reporting relationship.",Toast.LENGTH_LONG).show();return;}
                try {
                    database.addExplicitReportingRelationship(appointmentId,ids.get(which),actor.getUid());
                    Toast.makeText(this,"Reporting relationship saved on this device",Toast.LENGTH_LONG).show();
                    showGovernance();
                } catch(Exception error) {
                    Toast.makeText(this,"Could not save relationship: "+error.getMessage(),Toast.LENGTH_LONG).show();
                    showReportingManagement(appointmentId,"Leadership appointment", "Reporting");
                }
            }).setNegativeButton("Cancel",null).show();
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
        if (table.equals("delegations")) {
            Button useAsDelegate = new Button(this);
            useAsDelegate.setText("Use my signed-in account as delegate");
            useAsDelegate.setAllCaps(false);
            useAsDelegate.setOnClickListener(v -> {
                FirebaseUser current=FirebaseAuth.getInstance().getCurrentUser();
                if(current==null){Toast.makeText(this,"Sign in with Google first from Settings / Account.",Toast.LENGTH_LONG).show();return;}
                for(int i=0;i<fields.length;i++){
                    if("delegate_uid".equals(fields[i].key)) inputs[i].setText(current.getUid());
                    if("delegate_name".equals(fields[i].key) && inputs[i].getText().toString().trim().isEmpty() && current.getDisplayName()!=null) inputs[i].setText(current.getDisplayName());
                }
            });
            form.addView(useAsDelegate);
            TextView delegationIdentityNote=new TextView(this);
            delegationIdentityNote.setText("The signed-in user is recorded as the grantor automatically. Link the overseer and delegate UIDs where available; identity linking does not activate permissions.");
            delegationIdentityNote.setTextColor(muted); delegationIdentityNote.setTextSize(12); form.addView(delegationIdentityNote);
        }
        if (table.equals("appointments")) {
            Button useAccount = new Button(this);
            useAccount.setText("Use my signed-in Firebase account");
            useAccount.setAllCaps(false);
            useAccount.setOnClickListener(v -> {
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current == null) {
                    Toast.makeText(this, "Sign in with Google first from Settings / Account.", Toast.LENGTH_LONG).show();
                    return;
                }
                for (int i=0;i<fields.length;i++) {
                    if ("person_uid".equals(fields[i].key)) inputs[i].setText(current.getUid());
                    if ("person_name".equals(fields[i].key) && inputs[i].getText().toString().trim().isEmpty() && current.getDisplayName()!=null)
                        inputs[i].setText(current.getDisplayName());
                }
            });
            form.addView(useAccount);
            TextView identityNote = new TextView(this);
            identityNote.setText("A Firebase UID identifies the appointed person. It does not by itself grant cloud access or permissions.");
            identityNote.setTextColor(muted); identityNote.setTextSize(12);
            form.addView(identityNote);
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
                    String savedId;
                    if(editId==null) {
                        if(table.equals("appointments") && "Current".equalsIgnoreCase(values.getAsString("status")))
                            database.closeCurrentAppointments(values.getAsString("person_name"), values.getAsString("role"), values.getAsString("organization_id"), values.getAsString("start_date"));
                        savedId = database.insertRecord(table,values);
                    } else {
                        database.updateRecord(table,editId,values);
                        savedId = editId;
                    }
                    int reportingLinks = 0;
                    if (table.equals("appointments") || table.equals("organizations"))
                        reportingLinks = database.refreshAllAutomaticFollowUpReporting();
                    if(table.equals("visits")) database.trimVisits(values.getAsString("member_name"));
                    String saveMessage = "Saved on this device";
                    if ((table.equals("appointments") || table.equals("organizations")) && reportingLinks > 0)
                        saveMessage += ". " + reportingLinks + " automatic Follow-up reporting link(s) updated";
                    Toast.makeText(this,saveMessage,Toast.LENGTH_LONG).show(); showRecords(table);
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
            if (table.equals("appointments")) {
                String role = v.getAsString("role");
                String code = v.getAsString("position_code");
                if (code == null || code.trim().isEmpty()) {
                    String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
                    v.put("position_code", normalized);
                }
                FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
                if (actor != null) v.put("appointed_by_uid", actor.getUid());
            } else {
                FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
                if (actor != null) v.put("granted_by_uid", actor.getUid());
            }
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
        if(table.equals("appointments"))return join("Position code: "+get(c,"position_code"),get(c,"person_uid").isEmpty()?"Not linked to a Firebase identity":"Firebase identity linked",get(c,"start_date")+" → "+(get(c,"end_date").isEmpty()?"Current":get(c,"end_date")),get(c,"status"),get(c,"notes"));
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
        if(table.equals("appointments"))return join("Position code: "+safe(v,"position_code"),safe(v,"person_uid").isEmpty()?"Not linked to a Firebase identity":"Firebase identity linked",safe(v,"start_date")+" → "+(safe(v,"end_date").isEmpty()?"Current":safe(v,"end_date")),safe(v,"status"),safe(v,"notes"));
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

    private GoogleSignInOptions firebaseGoogleSignInOptions() {
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build();
    }

    private void startFirebaseGoogleSignIn() {
        try {
            Intent signInIntent = GoogleSignIn.getClient(this, firebaseGoogleSignInOptions()).getSignInIntent();
            startActivityForResult(signInIntent, FIREBASE_GOOGLE_SIGN_IN);
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("Google sign-in unavailable")
                .setMessage("Check Google Play services and try again. ("+error.getClass().getSimpleName()+")")
                .setPositiveButton("OK",null).show();
        }
    }

    private void completeFirebaseGoogleSignIn(Intent data) {
        Task<GoogleSignInAccount> accountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = accountTask.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken == null || idToken.isEmpty()) {
                throw new IllegalStateException("Google did not return an identity token. Check the Web OAuth client configuration.");
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, result -> {
                    if (result.isSuccessful()) {
                        Toast.makeText(this, "ChurchCare account connected.", Toast.LENGTH_LONG).show();
                        showSettings();
                    } else {
                        String reason = result.getException() == null ? "Unknown sign-in error" : result.getException().getMessage();
                        new AlertDialog.Builder(this).setTitle("ChurchCare sign-in failed")
                            .setMessage("Your local records and Google Drive backup settings were not changed. "+reason)
                            .setPositiveButton("OK",(d,w)->showSettings()).show();
                    }
                });
        } catch (Exception error) {
            android.util.Log.e("ChurchCare","Firebase Google sign-in failed",error);
            new AlertDialog.Builder(this).setTitle("Google sign-in not completed")
                .setMessage("No ChurchCare account was connected. Please try again. ("+error.getClass().getSimpleName()+")")
                .setPositiveButton("OK",(d,w)->showSettings()).show();
        }
    }

    private void showSettings() {
        base("Settings & account", "Manage your ChurchCare identity and review app information");
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        TextView heading = new TextView(this);
        heading.setText("ChurchCare account");
        heading.setTextColor(blue); heading.setTextSize(18); heading.setTypeface(null,1);
        content.addView(heading);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        TextView account = new TextView(this);
        account.setTextColor(ink); account.setTextSize(15);
        account.setPadding(dp(12),dp(14),dp(12),dp(14));
        account.setBackgroundColor(0xFFFFFFFF);
        if (user == null) {
            account.setText("Not signed in\n\nSign in with the same Google account you use for ChurchCare Google Drive backup and restore. This establishes your ChurchCare identity; it does not upload or replace local records.");
            content.addView(account);
            Button signIn = new Button(this);
            signIn.setText("Continue with Google"); signIn.setAllCaps(false);
            signIn.setOnClickListener(v->startFirebaseGoogleSignIn());
            content.addView(signIn);
        } else {
            String displayName = user.getDisplayName() == null ? "" : user.getDisplayName();
            String email = user.getEmail() == null ? "Email unavailable" : user.getEmail();
            account.setText((displayName.isEmpty() ? "Google account" : displayName)+"\n"+email+"\n\nChurchCare identity connected. Administrative Owner/Reviewer privileges have not yet been assigned.");
            content.addView(account);
            Button signOut = new Button(this);
            signOut.setText("Sign out of ChurchCare account"); signOut.setAllCaps(false);
            signOut.setOnClickListener(v -> {
                // Sign out only from Firebase. Do not clear the separate Google/Drive authorization state.
                FirebaseAuth.getInstance().signOut();
                showSettings();
            });
            content.addView(signOut);
        }

        TextView note = new TextView(this);
        note.setText("Google Drive backup and restore uses its own permission and remains separate from this ChurchCare sign-in. Signing in or out here does not delete local SQLite records or change Drive backups.");
        note.setTextColor(muted); note.setTextSize(13); note.setPadding(dp(4),dp(12),dp(4),dp(12));
        content.addView(note);

        Button about = new Button(this);
        about.setText("About ChurchCare"); about.setAllCaps(false);
        about.setOnClickListener(v->showAbout());
        content.addView(about);
    }

    private void showAbout() {
        String version="Version details unavailable";
        try { PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0); version="Version "+p.versionName+" ("+p.getLongVersionCode()+")\nPackage: "+getPackageName(); }
        catch(PackageManager.NameNotFoundException ignored){}
        new AlertDialog.Builder(this).setTitle("About ChurchCare").setMessage(version+"\n\nOffline-first church and member care. SQLite remains the local source of truth.\n\nGoogle Drive backup/restore is authorized separately. Firebase account sign-in establishes identity; role-based access and cloud record synchronization are separate stages and are not enabled yet.").setPositiveButton("OK",null).show();
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
        if (requestCode == FIREBASE_GOOGLE_SIGN_IN) {
            if (resultCode != RESULT_OK || data == null) {
                Toast.makeText(this,"Google sign-in was cancelled.",Toast.LENGTH_SHORT).show();
                showSettings();
                return;
            }
            completeFirebaseGoogleSignIn(data);
            return;
        }
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
