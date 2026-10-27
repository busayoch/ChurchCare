package com.churchcare.app;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;

public final class MainActivity extends Activity {
    private MemberDatabase database;
    private LinearLayout memberList;
    private TextView emptyState;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            database = new MemberDatabase(this);
            buildScreen();
            refreshMembers();
        } catch (Throwable error) {
            android.util.Log.e("ChurchCare", "Startup failed", error);
            new AlertDialog.Builder(this).setTitle("ChurchCare could not start")
                .setMessage("The app encountered a startup problem. Your existing data has not been intentionally deleted. Details: " + error.getClass().getSimpleName())
                .setPositiveButton("Close", (d, w) -> finish()).show();
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(12));
        root.setBackgroundColor(0xFFF4F7FA);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("ChurchCare"); title.setTextSize(28); title.setTextColor(0xFF0B4F85); title.setTypeface(null, 1);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button about = new Button(this); about.setText("About"); about.setOnClickListener(v -> showAbout());
        top.addView(about);
        root.addView(top);

        TextView subtitle = new TextView(this);
        subtitle.setText("Member care • Offline-first"); subtitle.setTextSize(14); subtitle.setTextColor(0xFF526273);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2); subParams.topMargin = dp(4); subParams.bottomMargin = dp(18);
        root.addView(subtitle, subParams);

        Button add = new Button(this); add.setText("+  Add member"); add.setOnClickListener(v -> showAddMember());
        root.addView(add, new LinearLayout.LayoutParams(-1, -2));

        TextView section = new TextView(this); section.setText("MEMBERS"); section.setTextSize(12); section.setTypeface(null, 1); section.setTextColor(0xFF526273);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2); sectionParams.topMargin = dp(22); sectionParams.bottomMargin = dp(8);
        root.addView(section, sectionParams);

        ScrollView scroll = new ScrollView(this);
        memberList = new LinearLayout(this); memberList.setOrientation(LinearLayout.VERTICAL);
        emptyState = new TextView(this); emptyState.setText("No members yet. Add a member to begin.\nYour records are stored on this device."); emptyState.setTextColor(0xFF526273); emptyState.setTextSize(15); emptyState.setPadding(dp(14), dp(18), dp(14), dp(18));
        memberList.addView(emptyState);
        scroll.addView(memberList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView footer = new TextView(this); footer.setText("Works offline • Local device database"); footer.setGravity(Gravity.CENTER); footer.setTextColor(0xFF526273); footer.setTextSize(12);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2); footerParams.topMargin = dp(10);
        root.addView(footer, footerParams);
        setContentView(root);
    }

    private void showAddMember() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = field("Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); form.addView(name);
        EditText phone = field("Phone number (optional)", InputType.TYPE_CLASS_PHONE); form.addView(phone);
        EditText organization = field("Church / organization (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); form.addView(organization);
        new AlertDialog.Builder(this).setTitle("Add member").setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (dialog, which) -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) { Toast.makeText(this, "Enter the member's name", Toast.LENGTH_LONG).show(); return; }
                try { database.addMember(n, phone.getText().toString().trim(), organization.getText().toString().trim()); refreshMembers(); Toast.makeText(this, "Member saved on this device", Toast.LENGTH_SHORT).show(); }
                catch (Exception e) { android.util.Log.e("ChurchCare", "Saving member failed", e); Toast.makeText(this, "Could not save member. Please try again.", Toast.LENGTH_LONG).show(); }
            }).show();
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setInputType(inputType);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(8); e.setLayoutParams(p); return e;
    }

    private void refreshMembers() {
        if (memberList == null || database == null) return;
        memberList.removeAllViews();
        Cursor c = database.listMembers();
        try {
            if (c.getCount() == 0) {
                emptyState = new TextView(this); emptyState.setText("No members yet. Add a member to begin.\nYour records are stored on this device."); emptyState.setTextColor(0xFF526273); emptyState.setTextSize(15); emptyState.setPadding(dp(14), dp(18), dp(14), dp(18)); memberList.addView(emptyState); return;
            }
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("full_name"));
                String phone = c.getString(c.getColumnIndexOrThrow("phone"));
                String org = c.getString(c.getColumnIndexOrThrow("organization"));
                LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(12)); card.setBackgroundColor(0xFFFFFFFF);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(8); card.setLayoutParams(cp);
                TextView n = new TextView(this); n.setText(name); n.setTextSize(17); n.setTypeface(null, 1); n.setTextColor(0xFF172B3A); card.addView(n);
                String details = (phone.isEmpty() ? "" : phone) + (phone.isEmpty() || org.isEmpty() ? "" : " • ") + org;
                if (!details.isEmpty()) { TextView d = new TextView(this); d.setText(details); d.setTextSize(13); d.setTextColor(0xFF526273); card.addView(d); }
                memberList.addView(card);
            }
        } finally { c.close(); }
    }

    private void showAbout() {
        String version = "Version details unavailable";
        try { PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0); version = "Version " + p.versionName + " (" + p.versionCode + ")\nPackage: " + getPackageName(); }
        catch (PackageManager.NameNotFoundException ignored) { }
        new AlertDialog.Builder(this).setTitle("About ChurchCare").setMessage(version + "\n\nOffline-first member care. This foundation build stores member records locally on this device.").setPositiveButton("OK", null).show();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onDestroy() { if (database != null) database.close(); super.onDestroy(); }
}
