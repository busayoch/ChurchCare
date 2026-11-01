# ChurchCare People, Organisation & Connections — Phase 2 Architecture

**Status:** Architecture approved; SQLite v8 identity/relationship foundation, appointment-aware connections and manual legacy matching implemented; latest changes require Android build verification; fresh-install smoke testing, backup/restore round-trip verification and secure invitation transport remain pending  
**Date:** 2026-11-01

## Implementation progress — first database milestone

The Android local database has been advanced from schema version 7 to version 8 on the feature branch.

Implemented in `MemberDatabase`:
- Independent `people` identity table, separate from local membership.
- `memberships` mapping that preserves the existing member register while linking each legacy member to a person identity.
- Nullable `appointments.person_id` to associate an appointment with a person without requiring parish membership.
- Typed `person_relationships` supporting source/target people, optional source/target appointments, relationship purpose, scope, proposed/accepted status and history.
- `connection_invitations` local draft table, with explicit local-only status; it does not deliver invitations or create cross-device claims.
- Additive version-7-to-8 migration that backfills existing members into people and membership records, and links appointments only where an exact name match is unique.
- Backup export/restore inclusion for the new tables. Legacy backups are still accepted; after restore, people/membership mappings are rebuilt from restored member records.
- Existing member-to-leader connections are migrated into the new relationship view when the appointment can be matched to one unique person. These are labelled `LEGACY_RECORDED`, not falsely marked as independently accepted.
- Legacy links whose appointment cannot be matched uniquely remain visible as `LEGACY_UNRESOLVED`; the original records are retained. A manual review screen now lists these appointments and lets the user explicitly match each one to an existing person. The appointment mapping and migrated `LEGACY_RECORDED` relationships are written atomically; original `member_connections` rows remain untouched. No relationship is marked accepted by this process.
- People & Connections now uses independent people records, with add/search by name, phone or email and proposals for overseer, responsibility, colleague and other relationships.
- Connection proposals now optionally select a current appointment for each endpoint, independently; person-level relationships remain supported.
- New appointments are linked to an independent person identity automatically; an exact unique existing name is reused, a new local person identity is created when none exists, and duplicate-name ambiguity is rejected rather than guessed.
- Database validation rejects an appointment endpoint that is not current or does not belong to the person at that endpoint.
- Relationship listings show the selected role and organisational unit at both ends, so multi-appointment context is visible.
- An overseer relationship is stored from overseer to the person being overseen; colleague relationships are marked bidirectional.
- The UI clearly describes proposals as local-only and does not claim recipient acceptance.
- The local acceptance API now refuses to mark a relationship accepted based only on a caller-supplied UID.

**Not implemented yet:** recipient-facing invitation acceptance, secure cross-device invitation delivery, identity claiming/merging, scoped permission enforcement, or formal institutional verification. Appointment-specific selection is now available in the connection proposal UI, and new appointments are linked to person identities. Full multi-appointment lifecycle and migration/backup testing remain pending. Firestore remains deny-all and no cloud sync has been enabled. Build #92 and appointment-aware build #96 passed debug and signed-release compilation, signature verification and artifact uploads. Manual legacy matching and consistent-snapshot backup hardening are committed and awaiting their own build verification.


**Repository:** busayoch/ChurchCare  
**Related work:** PR #4 — Establish ChurchCare hierarchy and governance data foundation

## 1. Purpose

ChurchCare is a connected church organisational and pastoral-care system, not merely a parish membership register. Its people, memberships, organisational units, appointments, relationships and permissions must be modelled separately.

This document records the approved direction for the next revision of People & Connections. It is an architecture decision, not a claim that the functionality is already implemented.

## 2. Core principle

A user should be able to connect with the people who are relevant to their actual authority, responsibility and working relationships:

- The person or leader directly above them who has responsibility for them.
- The people directly under their responsibility.
- Relevant colleagues, counterparts and coordinators with whom they work.
- People in other departments or at other organisational levels when the relationship is genuinely relevant.

Connections may be upward, downward or lateral. They may cross parish, area, zone, province or other organisational boundaries. They may also cross departmental boundaries.

**There is no single universal reporting tree.** Pastoral oversight, departmental coordination, follow-up responsibility and other working relationships may overlap without being forced into one chain.

## 3. Real-life examples that must work

1. An Area Pastor connects upward to the leader directly responsible for him, and downward to the Parish Pastors under his responsibility.
2. A Parish Pastor connects to an Area Follow-up Coordinator where that coordinator has a relevant responsibility towards the parish or its follow-up work.
3. A Parish Follow-up Leader connects upward to an Area Follow-up Coordinator.
4. A Parish Follow-up Leader connects to an Area Pastor where there is a genuine pastoral, operational or coordination relationship.
5. An Area Follow-up Coordinator connects to the follow-up leaders they coordinate across several parishes.
6. A person may hold more than one appointment and participate in more than one relationship network.
7. A person can be connected without being a member of the other person's parish, without holding a ChurchCare account yet, and without being entered into a local membership register.

These are examples, not hard-coded role combinations. The model must allow other legitimate relationships.

## 4. People, units, memberships and appointments are separate

### People
A person identity exists independently of parish membership, appointment and app login. A basic person record can be created to initiate an invitation or relationship. Account registration and claiming an identity are separate steps.

### Organisational units
Units can include denomination/church, national, continental, regional, provincial, zonal, area, parish and department/ministry structures. The structure is configurable and can be built progressively. A local church must not have to configure the entire wider organisation before using ChurchCare.

### Memberships
Membership represents a person's relationship to a congregation. It is optional and must never be created automatically just because a leadership or working connection is made.

### Appointments
An appointment represents a person's position or responsibility within an organisational unit, optionally within a department. A person can hold multiple concurrent appointments. Appointments have status and effective dates; ending or changing an appointment must preserve history.

### Relationships
A relationship represents a particular connection between people and/or their relevant appointments. It records its type, purpose, direction, scope, status, effective dates and provenance. It can reference appointments so the connection is grounded in the responsibilities that make it relevant. Personal pastoral or support relationships may reference people directly where appropriate.

### Follow-up assignments
Follow-up and care assignments are distinct from membership and login. They can link a person needing follow-up to a worker, leader or team, with the responsible unit and assignment history.

## 5. Connection experience

The interface should make ordinary connection simple. The proposed entry points are:

- **Connect My Overseer** — connect to the person directly responsible for the user in a relevant relationship.
- **Connect Someone Under My Responsibility** — connect to a person the user directly leads, supervises or coordinates.
- **Connect a Colleague** — connect to a relevant counterpart or working contact.

The person selecting a connection identifies the person, their relevant position or responsibility (if known), and the relationship purpose. ChurchCare should not force the user to configure a complete organisational hierarchy or select an unrelated global role tree.

The feature must not be restricted to the same department. A pastor may connect with a follow-up leader, and a follow-up leader may connect with a pastor, when the relationship is relevant to their work.

The app should prefer direct relationships and immediate responsibility links. It should not encourage arbitrary claims to distant leaders with whom the user has no direct responsibility or working relationship.

## 6. Invitations, acceptance and independent identity

A user may initiate a connection to someone who is not yet using ChurchCare. The proposed flow is:

1. The initiating user selects or enters the person's basic details and the relevant relationship.
2. ChurchCare creates a pending invitation that can be shared through a supported channel (for example, a shareable link, QR code, SMS or messaging app).
3. The recipient installs or opens ChurchCare, creates or signs into their own account, and claims/creates their own identity.
4. The recipient can accept or decline the proposed relationship.
5. Acceptance establishes the relationship between the two identities, without transferring ownership or control of either account.

The inviter does not create, own, administer or control the recipient's account. A subordinate cannot grant, revoke or control a senior person's authority. Equally, the recipient's ability to join ChurchCare must not depend on permission from someone below them.

A pending invitation is not an accepted relationship. The app must show invitation and connection states clearly.

## 7. Verification remains open

Formal institutional verification is deliberately **not a requirement for the initial connection model** and remains an open future decision.

Do not introduce a mandatory central church administrator approval step into ordinary invitations at this stage. Do not block an ordinary relationship solely because the entire church hierarchy has not been configured.

At the same time, a user-entered title or an accepted connection must not be represented as an independently verified official appointment. Keep separate concepts for:
- A person-provided profile or appointment claim.
- A proposed relationship.
- A relationship accepted by both participants.
- Any future independently verified appointment status.

The initial system must communicate what is confirmed by the participants without implying institutional verification that has not occurred.

## 8. Authority, scope and privacy

A connection is not an access-control grant.

- Being connected does not automatically permit access to private member, financial, safeguarding or pastoral-care records.
- Permissions must be separately defined and enforced by role, appointment, organisational scope, purpose and applicable consent.
- Departmental responsibility must not automatically confer authority over unrelated departments or sensitive records.
- A person's pastoral rank alone must not automatically confer unrestricted access to every departmental record.
- The design must support legitimate cross-department coordination without turning every connection into broad administrative authority.

The initial connection feature must not be presented as a secure access-control boundary until the corresponding authorisation enforcement exists.

## 9. Data and technical implications

The eventual data model should distinguish at least:

- People / identity records.
- Organisational units and their configurable structure.
- Memberships.
- Appointments and appointment history.
- Typed relationships/connections and their status/history.
- Invitations and invitation claims.
- Follow-up assignments.
- Scoped roles, permissions and audit events.

Relationship endpoints should be capable of referencing people and, where necessary, specific appointments. A relationship should identify its purpose and organisational context rather than assuming that all connections are simply parent-child links in one hierarchy.

The implementation must support duplicate detection and later identity reconciliation when records are created independently or offline on different devices.

## 10. Offline-first and invitation transport

Preserve the current offline-first SQLite foundation, backup and restore behaviour.

An invitation between separate devices requires a secure communication mechanism. Local SQLite alone cannot deliver invitations to another device. The transport and identity-claiming mechanism must be designed deliberately before enabling it.

Firestore currently remains deny-all. Do not loosen Firestore rules, add client cloud reads/writes, or enable cloud synchronisation as part of this architecture documentation. Any future online invitation service must have its own security design, token expiry/replay protections, identity binding, abuse controls and tests.

## 11. Migration and implementation sequence

1. Inspect the actual current schema and PR #4 implementation before changing code.
2. Design the revised schema and additive migration, preserving existing member connections, appointments, audit data and backup/restore compatibility.
3. Introduce independent people identities and configurable organisational units.
4. Ensure appointments can be multiple, scoped and historically retained.
5. Replace the member-only connection assumption with typed person/appointment relationships.
6. Add the simple upward, downward and lateral connection experience, including cross-department connections.
7. Add invitation and independent identity-claiming flow only after the secure transport design is approved.
8. Implement scoped permissions separately; do not infer access from connections.
9. Test database migration, backups, restoration, duplicate handling, invitation states and privacy boundaries.
10. Preserve the existing Android GitHub Actions workflow and signed release process. Keep PR #4 unmerged until reviewed and explicitly approved.

## 12. Acceptance scenarios

The design is not complete until the implementation can support all of these:

- Area Pastor connects to the leader directly above him.
- Area Pastor connects to several Parish Pastors under his responsibility.
- Parish Pastor connects to an Area Follow-up Coordinator.
- Parish Follow-up Leader connects to an Area Follow-up Coordinator.
- Parish Follow-up Leader connects to an Area Pastor where relevant.
- A department leader can manage relevant connections without requiring the pastor to initiate every one.
- A person has multiple appointments and relationships across units/departments.
- An invitee who does not yet use ChurchCare can join independently and accept or decline.
- A connection does not create parish membership or grant access to confidential records.
- Ending an appointment or relationship preserves history.
- Existing data survives migration and backup/restore.

## 13. Non-goals for this phase

- Requiring a complete national church directory before onboarding a local church.
- Requiring central institutional verification for every initial connection.
- Treating one pastoral hierarchy as the only reporting structure.
- Automatically granting record access through a connection.
- Enabling Firestore or general cloud synchronisation.
- Changing or merging the paused Windows desktop branch.

## 14. Governing statement

**ChurchCare is a multi-dimensional organisational relationship system.** People connect upwards, downwards and laterally according to real authority, responsibility and relevant working relationships. Those connections may cross organisational levels and departments. Membership, appointment, identity, relationship and permission remain distinct concepts.


## Fresh-install and backup validation plan

The initial ChurchCare deployment is a fresh install with no pre-existing church records. Validation must therefore start with an empty version-8 database and confirm that all schema tables and indexes are created without phantom people, memberships, appointments or relationships. Backup/restore round-trip tests should use clearly synthetic fixture records created for testing, restore into a clean database, and compare the restored records and relationships. Older backup compatibility should be exercised with synthetic legacy-format fixtures only; no user or church records are assumed to exist. Backup export now runs inside a SQLite read transaction so the exported tables represent one consistent snapshot, and restore rejects backups declaring a schema version newer than the installed app.