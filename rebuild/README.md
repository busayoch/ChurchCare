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
- Profile thumbnail and two additional photo URI slots. All photo references are stored locally; extra photos are explicitly local-only.

## Important limits — not yet a complete production system

- Google Drive authentication and actual cloud synchronization are **not connected**. The queue records changes for a future sync engine; it does not upload anything yet.
- Account login, role verification, and enforced per-user/branch authorization are **not connected**. Delegation scope is recorded but cannot yet secure data between different users/devices.
- The clean foundation has not yet completed an automated emulator launch or end-to-end UI test. A successful APK compilation does not establish runtime correctness.
- The user-provided ChurchCare Community Emblem still needs to be installed as the actual launcher/adaptive icon on this branch.
- Photo references are stored as local document URIs. A future sync implementation must upload only the profile thumbnail by default and must never automatically upload the two additional photos.

## Data safety and migrations

Schema upgrades are additive. Existing member records are retained when the schema version advances; records are archived using a deleted/tombstone state rather than immediately removed. Visit retention is enforced when a new visit is saved.

## Build

GitHub Actions workflow: [ChurchCare Clean Foundation Build](../.github/workflows/churchcare-foundation-build.yml)

Build command in the workflow: `gradle --no-daemon --stacktrace :app:assembleDebug` from `rebuild/android`.

Do not call a build verified until the latest workflow run has succeeded. Runtime behavior must also be tested on a device before release.
