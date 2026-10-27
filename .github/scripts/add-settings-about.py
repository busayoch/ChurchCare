#!/usr/bin/env python3
"""Add a safe Settings / About overflow-menu entry to the Android MainActivity."""
from pathlib import Path
import re
import sys

project = Path(sys.argv[1])
matches = list(project.glob("app/src/main/java/**/MainActivity.java"))
if len(matches) != 1:
    raise SystemExit(f"Expected exactly one app MainActivity.java; found {len(matches)}")
path = matches[0]
source = path.read_text(encoding="utf-8")

marker = "CHURCHCARE_SETTINGS_ABOUT_MENU"
if marker in source:
    print("Settings/About menu already present; no patch needed.")
    raise SystemExit(0)

if re.search(r"\bonCreateOptionsMenu\s*\(", source) or re.search(r"\bonOptionsItemSelected\s*\(", source):
    raise SystemExit("MainActivity already defines an options menu; refusing to overwrite existing menu behavior.")

# Insert methods immediately before the class's final closing brace.
end = source.rfind("}")
if end < 0:
    raise SystemExit("Could not locate MainActivity class closing brace.")

addition = r"""
    // CHURCHCARE_SETTINGS_ABOUT_MENU
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 74068, 100, "Settings / About")
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 74068) {
            showChurchCareAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showChurchCareAbout() {
        String versionName = "Unknown";
        long versionCode = 0L;
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null) versionName = info.versionName;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                versionCode = info.getLongVersionCode();
            } else {
                versionCode = info.versionCode;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Keep safe fallback values if package metadata cannot be read.
        }

        String details = "ChurchCare\n"
                + "App version: " + versionName + "\n"
                + "Version code: " + versionCode + "\n"
                + "Package: " + getPackageName() + "\n"
                + "Android: " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")\n\n"
                + "Offline-first church and member care management.";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Settings / About")
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }
"""
path.write_text(source[:end] + addition + "\n" + source[end:], encoding="utf-8")
print(f"Added Settings / About menu to {path}")
