# ChurchCare GitHub-based Firebase administration

This is a manually triggered administrative utility. It is separate from the
Android build workflow and does not run on ordinary pushes.

## Security model

- The workflow refuses to run unless the GitHub actor is `busayoch`, the
  repository owner account.
- The Firebase service-account key is read only from the GitHub Actions secret
  `FIREBASE_SERVICE_ACCOUNT_JSON`; never commit the key or place it in the APK.
- The service account should have only:
  - **Firebase Authentication Admin** (`roles/firebaseauth.admin`) to look up
    users and manage custom claims.
  - **Cloud Datastore User** (`roles/datastore.user`) for the Firestore role
    records used by this utility.
- Do not grant Project Owner, Editor, or Firebase Admin to this service account.
- Firestore client rules remain deny-all until the application-specific rules
  are reviewed and deliberately published.
- The initial Owner is reserved in `system/admin`. Bootstrap is idempotent for
  that same UID and refuses to replace a different registered Owner.
- The initial Owner account must exist in Firebase Authentication and have a
  verified email address.
- The Owner claim is `churchcareRole: "owner"`. A Reviewer claim is
  `churchcareRole: "reviewer"`. Unrelated existing custom claims are preserved.

## One-time Google Cloud setup

1. In Google Cloud Console, select project `churchcare-cloud-2026`.
2. Create a dedicated service account named `churchcare-github-admin`.
3. Grant it **Firebase Authentication Admin** and **Cloud Datastore User** at
   the project level. Do not grant broad project-owner/editor roles.
4. Create a JSON key for that service account and download it securely.
5. In the GitHub repository, open **Settings → Secrets and variables →
   Actions → New repository secret**.
6. Name the secret exactly `FIREBASE_SERVICE_ACCOUNT_JSON` and paste the
   complete JSON key contents. Never add this JSON file to the repository.
7. Delete any unneeded local copies of the key after confirming the secret is
   saved. Rotate/revoke the key if it is ever exposed.

## Find the Firebase UID

1. Open Firebase Console → Authentication → Users.
2. Find the Google account that must become the initial ChurchCare Owner.
3. Copy its **User UID** (not the email address and not the Google OAuth client ID).

## Bootstrap the initial Owner

1. Open GitHub → Actions → **ChurchCare Governance Administration**.
2. Select **Run workflow** on the branch containing this workflow.
3. Choose `bootstrap-owner`.
4. Paste the verified Firebase User UID into `target_uid`.
5. Leave `requester_uid` empty.
6. Run the workflow and inspect its log for the success message.

The workflow reserves the first Owner UID before applying the claim. If a run
is interrupted, rerun bootstrap with the *same UID*. It will not let a different
UID take over an existing reservation.

## Appoint or revoke a Reviewer

Only after the initial Owner bootstrap succeeds:

- `grant-reviewer`: set `target_uid` to the verified reviewer's Firebase UID
  and `requester_uid` to the registered Owner's Firebase UID.
- `revoke-reviewer`: use the same UID fields to remove the Reviewer claim.

The script checks that the requester currently has the Owner claim and matches
the registered Owner UID. The initial Owner cannot be demoted through this tool.

## Important limitations before production access is opened

This utility establishes trusted platform-level Owner/Reviewer identity only.
It does **not** yet publish Firestore rules, implement church-scoped staff
permissions, sync SQLite records, or authorize proposal publication. Keep the
existing deny-all Firestore rules until those data paths and rules have been
implemented and tested together.

Custom claims appear in newly issued Firebase ID tokens. Users should sign in
again or force-refresh their ID token after a role change. Revocation does not
magically erase claims from an already-issued token; Firestore rules and the
application must account for token refresh and the short remaining token
lifetime. For immediate high-risk revocation, disable the Firebase user or
revoke refresh tokens through a separately reviewed emergency procedure.
