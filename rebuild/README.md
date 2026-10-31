# ChurchCare clean Android rebuild

This is the isolated native Android implementation on `rebuild/clean-android-foundation`. The `main` branch has not been merged or replaced.

## Current implementation milestone

The Android app uses a local SQLite database as its operational source of truth. It is intended to remain usable without an internet connection.

### Available screens and local workflows
- Dashboard with local record counts.
- Member register, search, edit, archive, and local photo selection.
- Configurable organization units with parent relationships and readable hierarchy paths.
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

## Next milestone: decentralised identity and organisation access

The approved architectural direction is documented in [Account and Organisation Architecture](ACCOUNT-AND-ORGANIZATION-ARCHITECTURE.md). ChurchCare must support independent adoption by any local organisation, optional consent-based connections to higher/lower organisations, organisation-scoped roles and permissions, and safe disconnection. Authentication and cross-organisation authorization are not yet implemented; do not treat local role labels as a security boundary.

## Data safety and migrations

Schema upgrades are additive. Existing member records are retained when the schema version advances; records are archived using a deleted/tombstone state rather than immediately removed. Visit retention is enforced when a new visit is saved.

## Build

GitHub Actions workflow: [ChurchCare Clean Foundation Build](../.github/workflows/churchcare-foundation-build.yml)

Build command in the workflow: `gradle --no-daemon --stacktrace :app:assembleDebug` from `rebuild/android`.

Do not call a build verified until the latest workflow run has succeeded. Runtime behavior must also be tested on a device before release.
