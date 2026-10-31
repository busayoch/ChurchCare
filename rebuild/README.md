# ChurchCare clean Android rebuild

This is the isolated native Android implementation on `rebuild/clean-android-foundation`. The `main` branch has not been merged or replaced.

## Current implementation milestone

The Android app uses a local SQLite database as its operational source of truth. It is intended to remain usable without an internet connection.

### Available screens and local workflows
- First-run Church setup: enter the church name and the starting branch/unit at any level (for example, Local Branch, District, Region or National). A parent level is optional, so a branch can begin before higher levels have adopted ChurchCare.
- One independent ChurchCare data space per church installation. The church profile and its first branch/unit are saved together in SQLite.
- Dashboard identifies the current church and starting branch/unit.
- Dashboard with local record counts.
- Member register, search, edit, archive, and local photo selection.
- Configurable church branches/units with church-defined level names, multiple sibling branches, optional parent relationships and readable hierarchy paths. A parent can be added later when that level is ready.
- Pastoral appointments; adding a new current appointment closes the previous current appointment while preserving its history.
- Explicit overseer-to-delegate records with scope, dates, and status.
- Visit records and follow-up dates, retaining at most the five newest visit records per member.
- Care cases with category, priority, status, assignment, due date, details, and resolution.
- Durable change log for create/update/delete events, including soft-delete tombstones.
- Sync queue screen and Settings/About.
- Export/import of a portable ZIP backup using Android's document picker. The archive contains the full local JSON database plus copies of accessible member profile and extra-photo files. Restore validates the archive and replaces the local records in a single SQLite transaction. Older JSON-only backups remain supported.
- Profile thumbnail and two additional photo URI slots. All photo references are stored locally; extra photos are explicitly local-only.

## Important limits — not yet a complete production system

- Explicit Google Drive full-backup upload and latest-backup restore are implemented in Android using Google authorization and the Drive API. They require Google Cloud configuration (Drive API enabled and an Android OAuth client registered for package `com.churchcare.app` and the build signing certificate). The first Drive milestone is manual backup/restore only; automatic cloud synchronization is **not connected**. Manual ZIP export/import can use any location exposed by Android's document picker, including Google Drive when its provider is available, but this is still a user-managed file backup rather than multi-device sync. ZIP backups bundle accessible profile and extra-photo files; older JSON-only backups contain photo references but not the image bytes. The queue records changes for a future sync engine; it does not upload individual record changes. Drive backup uploads the full database, including sensitive pastoral/care notes, only after a clear user confirmation.
- Account login, role verification, and enforced per-user/branch authorization are **not connected**. Delegation scope is recorded but cannot yet secure data between different users/devices.
- The clean foundation has not yet completed an automated emulator launch or end-to-end UI test. A successful APK compilation does not establish runtime correctness.
- The ChurchCare Community Emblem is configured as the launcher icon. A polished adaptive icon with separately generated foreground/background assets can be refined later.
- Photo references are stored as local document URIs. ZIP export copies accessible referenced images into the local archive. A future cloud-sync implementation must upload only the profile thumbnail by default and must never automatically upload the two additional photos. The explicit full-backup feature is different: it bundles accessible photos and all local tables.

## Current milestone: Church and branch foundation

The approved product direction is documented in [ChurchCare identity, church structure and access architecture](ACCOUNT-AND-ORGANIZATION-ARCHITECTURE.md). User-facing language is Church, Church branch/unit, Church membership, Church administrator, Church connection, Granted authority and Church structure.

Different churches are completely separate. Their data is never exchanged or transferred. Within one church, multiple branches can exist at the same level and at multiple levels. A branch can start at any level before its parent has adopted ChurchCare; its parent can be added later without replacing the branch identity.

The Android implementation currently provides a local Church profile and starting branch/unit setup, plus the existing local branch hierarchy manager. This is an offline foundation only. Cross-device invitations/vertical connections, account authentication, server-side church isolation and enforced per-user/branch authorization are not yet implemented. Do not describe local roles or local database separation as production multi-user security.

## Data safety and database

The local database now stores a distinct Church profile and its starting branch/unit. The existing `organizations` SQLite table remains an internal technical name for church branches/units; it is not the identity of the Church itself. Schema version 4 adds the Church profile table. Records are archived using a deleted/tombstone state rather than immediately removed. Visit retention is enforced when a new visit is saved.

## Build

GitHub Actions workflow: [ChurchCare Clean Foundation Build](../.github/workflows/churchcare-foundation-build.yml)

Build command in the workflow: `gradle --no-daemon --stacktrace :app:assembleDebug` from `rebuild/android`.

Do not call a build verified until the latest workflow run has succeeded. Runtime behavior must also be tested on a device before release.


## Organisational template catalogue

The denomination research catalogue and machine-readable seed are stored in:
- `CHURCH-ORGANISATION-TEMPLATE-LIBRARY.md`
- `assets/church-organisation-templates.v1.json`

These are research/configuration inputs, not yet a claim that every template is fully verified or already selectable in the Android UI. The catalogue distinguishes confirmed working configurations from drafts and unverified vocabulary. Leadership titles remain separate from organisational levels. Template adoption creates a church-specific copy; shared updates never overwrite active church configurations. Any user may propose changes, while only authorised reviewers can publish them.
