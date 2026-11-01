# ChurchCare church hierarchy and governance model

## Purpose

ChurchCare models the way each church actually organises leadership, member care and accountability. It does not impose one fixed denominational chart.

## Two connected structures

1. **Organisational hierarchy** is represented by the existing `organizations` parent-child tree. Churches may define levels such as international, national, regional, district, area and local church, or use their own names and a simpler structure.
2. **Leadership appointments** assign an authenticated person to a position within an organisational unit. A person is identified by Firebase Authentication UID, never by display name alone.

The organisation tree and appointment tree are related, but they are not the same thing. A person may hold more than one appointment where the church permits it.

## Follow-up leadership

A church may appoint an Overseeing Pastor and one or more pastoral delegates. The pastor may delegate member-care oversight to a trusted person.

At each organisational level, a Head of Follow-up leads the department at that level. Where the next higher organisational level also has a Head of Follow-up, the lower-level Head automatically has an upward reporting relationship to that higher-level Head. This is derived from the configured organisation tree and matching position code; it is not hard-coded to a fixed list of levels.

The Head of Follow-up may appoint and manage subordinate department or team heads within the authority of that appointment. The pastor does not need to approve each subordinate appointment unless the church's own governance configuration requires it.

A Head of Follow-up may also have additional reporting relationships to the Overseeing Pastor and/or a pastoral delegate. Reporting is therefore many-to-many, not a single-parent manager field.

## People and connections

The Android app now includes a plain-language **People & connections** workspace. A church worker can select a person from the local member directory, choose a current leadership appointment, and describe the relationship using familiar labels such as My Pastor, My Follow-up Leader, My Team Leader or My Pastoral Delegate. A person may have multiple connections. People can be added and connected without having a ChurchCare account; account linking remains optional.

These links are stored separately from leadership reporting relationships in the local `member_connections` table. Retiring a connection does not delete the member or the leader's appointment. The new table is included in logical backup and restore, and database version 7 creates it through an additive migration.

**Important current limitation:** this first People & connections interface provides local relationship recording, not role-based administration. The current Android app does not yet enforce that a Follow-up Head can manage only their own branch or that an administrator alone can change the whole structure. Do not treat these local links as permissions or use this interface as an access-control boundary. The next security milestone must establish authenticated administrator/leader roles, verify each leader's active appointment and delegated organisational scope, and enforce those checks before decentralized management is enabled.

## Appointment and reporting data

- `appointments.person_uid`: Firebase UID for the appointed person.
- `appointments.position_code`: stable position key used for matching equivalent positions across organisational levels.
- `appointments.appointed_by_uid`: authenticated person who made the appointment.
- `appointment_reporting`: explicit reporting edges between appointments. Multiple active edges are allowed, with a uniqueness constraint preventing duplicate pairs.
- `delegations`: records who delegated to whom, the organisational scope, dates and status.
- `delegations.permissions_json`: explicit delegated permissions; a delegation is not an unrestricted transfer of the grantor's authority.

Existing name fields remain for human-readable display and backward compatibility.

## Access principle

A higher-level Follow-up leader can inspect individual follow-up records throughout the subordinate organisational units covered by their active appointment. The same principle applies to authorised pastoral oversight and explicit pastoral delegates.

Access is determined by active authenticated identity, active appointment, organisational ancestry and any explicit delegation scope. A person does not gain authority over unrelated branches merely by holding a similarly named position.

The local schema introduced in database version 6 is a foundation for this model. Local management now supports automatic upward Follow-up links, explicit additional reporting links, and a dedicated pastoral delegation workspace. Reporting links are checked for circular chains before a new explicit edge is saved. Changes to organisation parentage trigger a recalculation of automatic Follow-up reporting. These are local governance records and do not themselves grant cloud access. Firestore remains deny-all until server-enforced rules and trusted authorization logic are implemented and tested. Client-side checks alone are not a security boundary.

## Accountability and audit

`governance_audit` records the actor UID, action, affected entity, organisational scope, structured details and timestamp. The planned cloud implementation must audit sensitive individual-record access as well as creation, editing, assignment, appointment, delegation, revocation and publication actions.

Audit records should be append-only to ordinary users. Any administrative correction must itself create a traceable audit event.

## Migration and backup compatibility

Database version 6 is additive:
- Existing appointments and delegations are retained.
- New identity and position fields are nullable/defaulted for old records.
- Reporting and audit tables are created without deleting existing data.
- The new tables are included in the logical backup and restore format.
- Older backups remain valid because the new tables are optional on import; new backups include them.

## Not included in this milestone

The local Android milestone now includes the Leadership & Governance and People & connections management screens, automatic reporting-edge generation, explicit reporting links, pastoral delegation review, and circular-reporting checks. It still does not implement role-based branch administration, inherited individual-record queries, Firebase custom-claim changes, Firestore rules, cloud sync, or server-side audit enforcement. Those are subsequent stages and must be verified independently.
