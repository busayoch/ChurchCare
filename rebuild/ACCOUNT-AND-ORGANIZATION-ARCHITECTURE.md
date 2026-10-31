# ChurchCare identity, organisation and access architecture

Status: approved architectural direction; implementation design, not yet production authentication.

## Product boundary

ChurchCare is an independent, denomination-neutral platform. It is not the official application of a church or denomination. RCCG, Catholic, Anglican and other structures are configurations, not hard-coded product assumptions.

## Core design principle: decentralised adoption

Any church organisation or local unit may adopt ChurchCare and operate independently. It does not need a parent organisation to be registered or online.

A unit may later:
- connect to a higher organisation that has also adopted ChurchCare;
- invite or connect lower organisational units;
- maintain its own records and local administration while connected;
- disconnect from a relationship without losing its own operational records.

A connection is optional and must never silently transfer ownership of an organisation or expose all its data.

## Separate identity, organisation and authority

Do not combine these concepts into one role field.

1. **Person/account identity** — the human's sign-in identity, independent of any one church.
2. **Organisation** — an independently administered church body, branch, parish, assembly, ministry or other unit. Every organisation can be a root organisation.
3. **Organisation membership** — a person's membership in an organisation, including their status and organisation-specific role assignments.
4. **Organisation relationship** — an explicit, consent-based relationship between two organisations, such as parent/child, affiliation, oversight or cooperation.
5. **Permission grant** — specific actions and data scope granted to a person or organisation. Delegation is a time-bounded permission grant, not a global role.

One person may belong to multiple organisations and have different responsibilities in each. An organisation may exist without any connected parent or child.

## Organisation relationship and consent

- An organisation creates its own identity and appoints its first local administrator.
- A higher organisation cannot claim an existing independent unit unilaterally.
- A proposed connection requires acceptance by authorised administrators on both sides.
- The relationship records its type, status, proposing organisation, accepting organisation, effective dates, and permitted information/actions.
- Parent/child links are not assumed to grant unrestricted access to child records.
- Each organisation remains responsible for its own users, local records and local operations.
- A relationship can be suspended or ended. Ending it revokes relationship-derived access; it must not delete either organisation or rewrite historical records.
- Any shared-data retention/export rules must be explicit and auditable.

## Roles and permissions

Do not hard-code a universal hierarchy such as system administrator > general overseer > area pastor > member.

- Platform operations roles, if needed, are separate from church organisation roles.
- Each organisation can define or select role templates appropriate to its structure.
- Roles are bundles of permissions, not proof of authority outside that organisation.
- Permission checks must include organisation context and resource scope.
- A permission grant should identify subject, granting authority, organisation, allowed actions, resource scope, start/end dates, status and audit metadata.
- Delegation must be limited to the scope explicitly granted and must not allow a delegate to grant broader permissions than the delegator possesses.
- Revocation and expiry must take effect in the authoritative service, and be reflected on devices when they reconnect.

## Security boundary

SQLite remains the offline operational store on each device. It is not an authority for cross-user or cross-organisation security.

A production multi-user system requires a trusted backend to authenticate sessions and enforce organisation isolation and permissions on every protected operation. Client-supplied organisation IDs, role labels or permission claims must never be trusted as sufficient authorization. The backend should enforce tenant/organisation boundaries (including database row-level security where supported), HTTPS, audit events, session revocation and least privilege.

Offline mode may use a previously authenticated, device-bound session and a locally cached minimum permission snapshot with an expiry policy. Offline actions must be queued with actor, organisation, resource, action and timestamp, then re-authorised by the server on sync. The UI must not imply that a queued offline action has been accepted by the server. High-risk actions may require a live connection.

## Data model direction (conceptual)

- accounts: global sign-in identity and credential/provider references;
- organisations: stable ID, name, type/configuration, status, created/updated metadata;
- organisation_memberships: account/person ID, organisation ID, local status;
- role_definitions: organisation-scoped role templates;
- role_permissions: actions included in each role;
- membership_role_assignments: role, organisation, scope and effective dates;
- organisation_relationships: two organisation IDs, relationship type, bilateral consent/status and effective dates;
- permission_grants: subject, grantor, organisation, resource scope, actions, dates and revocation state;
- invitations: organisation-scoped, single-use/expiring invitation and acceptance audit;
- audit_events: actor, organisation, action, target, timestamp and outcome.

Use stable UUIDs and preserve historical assignments/relationships rather than overwriting them. This is a conceptual model; map it to the existing SQLite schema and future server schema only after a migration review.

## Implementation sequence

1. Preserve and checkpoint the working Android baseline.
2. Finalise the identity, organisation, relationship and permission data model against the current SQLite schema.
3. Add additive local schema migrations and UI for independent organisation setup and local administrator assignment, without claiming client-only security.
4. Design the trusted backend and organisation-isolation enforcement.
5. Add account authentication and organisation-scoped invitations/memberships.
6. Add bilateral organisation connection requests and scoped sharing.
7. Enforce delegation and permission checks across all screens and backend operations.
8. Add offline session rules, queued action re-authorisation and multi-device sync.
9. Test independent adoption, later connection, lower-unit onboarding, disconnection, revocation, offline use and cross-organisation data isolation.

## Explicit non-goals for the first implementation

- No denomination is privileged or assumed.
- No central headquarters is required for a local unit to start.
- No automatic sharing of all records on connection.
- No global role hierarchy baked into the application.
- No claim that local-only role checks provide production security.
- No destructive schema rewrite or migration that risks existing offline data.

## Product acceptance tests

- A standalone branch can create an organisation and operate without a parent.
- A second independent organisation can do the same without seeing the first organisation's records.
- A person can hold memberships in multiple organisations with different roles.
- Two organisations can propose and accept a scoped relationship.
- A connected parent sees only data/actions explicitly shared with it.
- A lower unit can join later without losing its existing identity or history.
- Disconnecting a relationship revokes inherited access but preserves each organisation's own records.
- Delegation is limited by organisation, action, resource scope and dates.
- Offline work remains available within the device's last authorised scope, and queued actions are rechecked by the backend on reconnection.
