# ChurchCare cloud governance model (design draft)

**Status:** Design only. This document does not activate Firestore access, change Firestore rules, or enable cloud synchronization.

## 1. Security boundaries

ChurchCare has three separate layers of authority:

1. **Platform identity and governance** — Firebase custom claims administered through the restricted GitHub Actions workflow.
   - `churchcareRole: "owner"`
   - `churchcareRole: "reviewer"`
2. **Church membership and permissions** — a user’s role in one specific church, including the organisational units within which that role applies.
3. **Local application data** — the existing SQLite database, which remains usable offline and is not replaced by cloud data.

A platform Owner or Platform Reviewer does not automatically gain access to a church’s member, pastoral-care, visit, or other private records. Platform claims must never be treated as a substitute for a church membership record.

## 2. Proposed Firestore document layout

The following paths are a proposed contract, not live paths:

- `churches/{churchId}` — registered church identity and lifecycle metadata only.
- `churches/{churchId}/memberships/{uid}` — one user’s membership, role, status, and scope in that church.
- `churches/{churchId}/structureVersions/{versionId}` — immutable published organisational-structure versions.
- `churches/{churchId}/structureProposals/{proposalId}` — proposed structure changes and their review history.
- `churches/{churchId}/control/current` — active structure-version pointer and monotonically increasing version number.

The Firestore church ID must match the stable `church_id` already stored in the local `church_profile`. A display name is never an identity key.

A membership record should contain, at minimum:

- `uid`
- `role`
- `status` (`active`, `suspended`, or `revoked`)
- `unitIds` — explicit unit IDs in the member’s permitted scope
- `grantedByUid`
- `createdAt` and `updatedAt`

Unit scope must be explicit. Firestore security rules cannot safely infer an arbitrary organisational descendant tree by querying the whole structure. When a unit’s descendants change, affected membership scopes must be deliberately recalculated and reviewed.

## 3. Proposed church-level roles

These are proposed application roles, distinct from platform Owner and Platform Reviewer:

| Church role | Intended authority |
|---|---|
| Church Administrator | Manage church-level settings, membership invitations/requests, and the church structure; broad church scope |
| Structure Administrator | Maintain structure proposals within assigned unit scope; cannot publish their own proposal |
| Church Reviewer | Review and approve/reject eligible structure proposals; cannot approve their own submission |
| Records Manager | Manage member and appointment records within assigned unit scope |
| Care Worker | Work with permitted member-care, visit, and follow-up records within assigned unit scope |
| Viewer | Read only the records explicitly permitted by the membership scope |

A user may hold more than one membership only where the combination is explicitly granted. Role names alone do not grant access: the membership must also be active and its unit scope must cover the requested record.

Membership grants and revocations must be auditable. Users must not be able to create or modify their own membership, grant themselves a higher role, or expand their own unit scope.

## 4. Structure proposal and publication lifecycle

The proposed workflow is:

1. An authorised user creates a proposal against the currently published structure version.
2. The proposal records its submitter UID, submission time, base version, proposed changes, and status `PENDING`.
3. A different active Church Reviewer or authorised Church Administrator reviews it.
4. The reviewer records `APPROVED` or `REJECTED`, their UID, time, and decision notes.
5. Approval publishes a **new immutable structure version** and advances the current-version pointer in the same atomic Firestore transaction.
6. Rejected proposals remain available as an audit record. Published versions are never silently overwritten.

The security rules must verify the reviewer’s active membership, prevent self-approval, reject stale base versions, and require the approved proposal, new immutable version, and active pointer to agree through Firestore atomic-write validation (including `getAfter()` where appropriate).

The client must not be able to publish a structure by directly editing the current pointer or overwriting an adopted version.

## 5. Local-first operation and future record synchronization

- SQLite remains the local source of truth while offline.
- Existing local records and the `change_log` remain intact.
- The existing `configuration_suggestions` table is local proposal storage; it is not yet connected to Firestore.
- Future sync must use stable record IDs, idempotent operations, explicit tombstones, and conflict detection. It must not blindly overwrite a newer cloud record with an older local copy.
- A Drive backup is a data backup, not a source of identity, membership, or governance authority.
- Restoring a local backup must never restore or overwrite Firebase authentication, membership, or platform-role records.

## 6. Security rules and rollout gates

The current Firestore rules intentionally deny all client reads and writes. Keep them that way until all of the following exist and pass tests:

1. Finalised document paths and field contracts.
2. Membership grant, suspension, and revocation procedures.
3. Rules for each role and unit scope.
4. Emulator tests for allowed and denied cases, including self-promotion, cross-church access, inactive membership, self-approval, stale proposals, and direct publication attempts.
5. A migration and rollback plan.
6. A tested Android client flow that refreshes Firebase ID tokens and handles offline/permission-denied states without damaging local data.

No production Firestore rules should be opened as part of a UI-only or schema-documentation change.
