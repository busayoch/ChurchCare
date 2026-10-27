# ChurchCare clean Android foundation

This directory is the isolated, from-the-ground-up Android implementation. It is deliberately on the `rebuild/clean-android-foundation` branch; the existing `main` branch and its source ZIP workflow are left untouched.

## First milestone

- Native Java Android app, package identity `com.churchcare.app`.
- Offline-first SQLite member storage.
- Stable UUID member IDs.
- Local change log records member creation for a future synchronization engine.
- Simple member list, add-member flow, and About/version dialog.
- No network permissions or cloud dependency in this first milestone.
- Non-destructive migration policy: future schema upgrades must preserve data.

## Not yet implemented

This is a foundation milestone, not the complete ChurchCare product. Organization hierarchy, appointments/transfers, delegation and scoped permissions, visits and the five-visit retention rule, care follow-ups, photo handling, and actual cloud synchronization still need implementation and tests. The local change log is groundwork only; it does not claim that synchronization is operational.

## Build and verification

The dedicated GitHub Actions workflow compiles this source separately and uploads a debug APK. A successful compile does not prove runtime stability; install and launch verification on a real Android device or a working emulator remains required before this can be considered a stable release.

## Data safety

Do not uninstall the existing app or replace the current production/checkpoint build as part of evaluating this experimental foundation. The new app deliberately keeps the same application ID for continuity, so install/upgrade testing should be done only after version/signing and data migration strategy are confirmed.
