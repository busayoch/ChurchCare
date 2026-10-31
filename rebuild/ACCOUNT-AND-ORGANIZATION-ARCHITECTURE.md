# ChurchCare identity, church structure and access architecture

Status: approved product direction; conceptual implementation design, not yet production authentication.

## Product boundary

ChurchCare is an independent, denomination-neutral platform. It is not the official application of any church or denomination. Any church may use it without approval or participation from another church.

**Different churches are completely separate.** For example, Grace Chapel and Living Faith Church have separate ChurchCare environments. Their records, members, staff, administration and history stay within their own churches. ChurchCare does not provide a process for transferring or exchanging records between separate churches.

ChurchCare must not hard-code RCCG, Catholic, Anglican or any other denomination's structure. Each church defines the names and levels that fit its own structure.

## Our ChurchCare language

Use these words in product screens, instructions and documentation:

- **Church** — the independent church using ChurchCare.
- **Church branch / unit** — a part of that church's structure, at any level. Examples might include a national office, region, district, parish, assembly, campus or local branch.
- **Church membership** — a person's membership or working relationship with a particular church or one of its branches/units.
- **Church administrator** — a person authorised to administer a church or a defined part of its structure.
- **Church connection** — a recognised vertical link between levels or branches within the same church's structure.
- **Granted authority** — specific permissions given to a person to perform defined actions.
- **Church structure** — the levels and branches that make up one church.

Technical database names may differ where necessary, but the user-facing language must remain simple and consistent.

## Independent adoption at any level

A church may begin using ChurchCare at its top level, or any branch/unit at any level may begin first.

For example, Grace Chapel may have this structure:

- Grace Chapel (national)
  - North Region
    - District A
      - Ikeja Branch
      - Agege Branch
    - District B
  - South Region
    - District C
      - Lekki Branch
      - Ajah Branch

This is only an example. ChurchCare must allow each church to define its own level names, depth and number of branches.

Important rules:

- A branch can start using ChurchCare without waiting for its parent, regional office or national leadership.
- A branch that starts first can manage its own records and local users.
- When another level later adopts ChurchCare, the branch can be connected vertically to its correct place in that same church's structure.
- The system must support several branches or units at the same level under one parent.
- A parent level may itself have a parent, and a branch may have several levels above it.
- A branch must not be forced to create fictional parent accounts or wait for the entire hierarchy to register.
- Connecting a branch to its correct place in the same church structure must preserve its existing identity, records and history. It must not duplicate, overwrite or silently move its records.
- The church must be able to identify the correct parent and approve the connection. A higher level must not claim a branch unilaterally.
- A branch remains responsible for its own local operations. A connection does not automatically give every higher-level administrator unrestricted access to every local record.

The data model must distinguish the identity of the church from the individual branches/units within its structure. It must also represent a branch that has started independently but whose parent level has not yet joined ChurchCare.

## Complete separation between different churches

- Every church has its own separate ChurchCare data boundary.
- A church cannot browse, search, administer or claim another church's records.
- ChurchCare does not support moving or transferring records between separate churches as part of this architecture.
- A person may independently have memberships in more than one church, but each membership and its permissions are separate. Being a member or administrator in one church gives no access to another.
- Similar church names do not make two churches the same church.
- A branch connection is valid only within the structure of its own church. It is not a way to connect two different churches.

## People, church membership and authority

Keep these concepts separate:

1. **Person/account identity** — the human's sign-in identity.
2. **Church** — the independent church and its private ChurchCare environment.
3. **Church branch/unit** — a node in that church's structure.
4. **Church membership** — the person's membership in the church, optionally associated with one or more branches/units.
5. **Role and granted authority** — the actions the person is allowed to perform, within a defined church and scope.

One person may belong to multiple churches and have different responsibilities in each. Those memberships and permissions must remain isolated.

Do not hard-code a universal hierarchy such as system administrator > general overseer > area pastor > member. Each church can define or select role templates appropriate to its own structure.

A role is not proof of authority outside the church or scope in which it was assigned. A granted authority should identify the person receiving it, who granted it, the church, the permitted actions, the relevant branch/unit or records, effective dates, status and audit history.

A person must not grant authority broader than the authority they themselves hold. Revocation and expiry must be enforced by the trusted service and reflected on devices when they reconnect.

## Church structure and access rules

The church structure is a tree or equivalent parent-child structure that supports multiple branches at the same level and any reasonable number of levels.

- Each branch/unit has a stable ID and, where known, one parent branch/unit.
- A parent may have many child branches/units.
- The church defines its own level names and may change them without changing the identity of existing branches.
- A branch that starts before its parent is registered must be representable without inventing a parent.
- A later vertical connection must be approved and audited.
- Access up or down the structure is permission-based. Being higher in the structure does not automatically mean unrestricted access to every record.
- The church may grant higher-level administrators defined oversight or reporting access while leaving local records under local administration.
- Historical records must retain the branch/unit context in which they were created.

## Security boundary

SQLite remains the offline operational store on each device. It is not the authority for cross-user or cross-branch security.

A production multi-user system requires a trusted backend to authenticate sessions and enforce church isolation and permissions on every protected operation. Client-supplied church IDs, branch IDs, role labels or permission claims must never be trusted as sufficient authorisation. The backend should enforce strict church-level data isolation (including database row-level security where supported), HTTPS, audit events, session revocation and least privilege.

Offline mode may use a previously authenticated, device-bound session and a locally cached minimum permission snapshot with an expiry policy. Offline actions must be queued with actor, church, branch/unit, resource, action and timestamp, then re-authorised by the server on sync. The UI must not imply that a queued offline action has been accepted by the server. High-risk actions may require a live connection.

## Data model direction (conceptual)

The current Android database has an existing `organizations` table used for the internal branch/unit hierarchy. Do not confuse that existing table with the independent Church that adopts ChurchCare. Do not rename or rewrite existing tables until a migration plan has been reviewed.

Conceptual future records:

- churches: stable church ID, name, status and metadata;
- church_units: stable branch/unit ID, church ID, optional parent unit ID, church-defined level/type, name, status and metadata;
- accounts: sign-in identity and credential/provider references;
- church_memberships: account/person ID, church ID, status and membership details;
- unit_memberships or scoped assignments: person, church unit, local status and dates;
- role_definitions: church-scoped role templates;
- role_permissions: actions included in each role;
- membership_role_assignments: role, church, optional unit scope and effective dates;
- permission_grants: subject, grantor, church, branch/unit/resource scope, actions, dates and revocation state;
- invitations: church-scoped, single-use/expiring invitation and acceptance audit;
- audit_events: actor, church, branch/unit, action, target, timestamp and outcome.

A branch that starts before its parent is using ChurchCare must retain a stable branch identity and its own church data boundary. The design must define a safe, consent-based way to recognise its place in the same church structure later without treating this as a transfer between different churches or losing its history. This detail must be settled in the migration and backend design before implementation.

Use stable UUIDs and preserve historical assignments and relationships rather than overwriting them. This is a conceptual model; map it to the existing SQLite schema and future server schema only after a migration review.

## Implementation sequence

1. Preserve and checkpoint the working Android baseline and existing records.
2. Finalise the Church, Church branch/unit, membership, structure and granted-authority data model against the current SQLite schema.
3. Agree how a branch that adopts ChurchCare first is later recognised at its correct place in the same church structure, while keeping different churches completely isolated.
4. Add additive local schema migrations and UI for independent church setup, branch/unit setup and local administrator assignment, without claiming client-only security.
5. Design the trusted backend and strict church-level data isolation.
6. Add account authentication and church-scoped invitations/memberships.
7. Add approved vertical connections between branches/units within the same church.
8. Enforce granted-authority checks across all screens and backend operations.
9. Add offline session rules, queued action re-authorisation and multi-device sync.
10. Test independent church adoption, branch-first adoption, later vertical connection, multiple sibling branches, access boundaries, offline use and strict separation between different churches.

## Explicit non-goals

- No denomination is privileged or assumed.
- No church needs another church's approval to start using ChurchCare.
- No automatic connection or exchange of records between different churches.
- No transfer of records between different churches.
- No assumption that a church has only one branch at each level.
- No fixed number or fixed naming of hierarchy levels.
- No automatic unrestricted access to lower-level records merely because an administrator is higher in the structure.
- No claim that local-only role checks provide production security.
- No destructive schema rewrite or migration that risks existing offline data.

## Product acceptance tests

- Grace Chapel can start using ChurchCare independently.
- Living Faith Church can start using ChurchCare independently and cannot see Grace Chapel's records, and vice versa.
- A local branch of Grace Chapel can start before its district, region or national level has joined ChurchCare.
- When the appropriate Grace Chapel parent level later joins, the branch can be connected to its correct place without losing or duplicating its identity, records or history.
- A parent can have several branches at the same level.
- A church can configure its own level names and hierarchy depth.
- A person can have memberships in more than one church with different roles, without those memberships granting cross-church access.
- A higher-level administrator sees only the records and actions allowed by granted authority.
- Disconnecting or changing a vertical branch connection does not delete the church's own records or alter historical ownership.
- Offline work remains available within the device's last authorised scope, and queued actions are rechecked by the backend on reconnection.


## Configurable organisational templates and change governance

ChurchCare provides denomination-specific organisational templates as onboarding suggestions. Templates describe organisational units and their relationships only; they must never mix pastoral titles, office holders or leadership appointments into the hierarchy.

The initial research catalogue and machine-readable seed are:
- `rebuild/CHURCH-ORGANISATION-TEMPLATE-LIBRARY.md`
- `rebuild/assets/church-organisation-templates.v1.json`

Template statuses distinguish community-confirmed working configurations, source-supported drafts, vocabulary-only entries whose sequence is unverified, and research-pending entries. A draft or unverified entry must not be presented as an authoritative fixed hierarchy.

When a church adopts a template, ChurchCare creates a private copy. The church can customise its copy without affecting other churches or the shared template. A shared-template update must be offered as a reviewable suggestion and must never overwrite an active church configuration.

Any user may propose a configuration correction. Only a person with the appropriate granted authority may approve and publish a change to the active configuration. Changes that affect connected branches must follow the relevant church's approval scope. Proposals must have no effect on live records before approval.

The configuration model must support:
- strict parent-child hierarchy;
- cooperative affiliation between autonomous local churches and wider associations/conferences;
- branch networks where no complete nested hierarchy is established;
- mixed structures where the church confirms more than one relationship type;
- standalone churches with no parent unit.

Keep organisational units separate from leadership positions and people. A leadership position may be attached to a unit and occupied by a person for a defined term; changing the office holder does not recreate or rename the unit.

Every published configuration change must retain a version, proposer, reviewer, approver, effective time and audit history. Preview changes before publication. Preserve stable unit IDs and historical record context. Renames, moves, closures, mergers and deletions are distinct operations; none may silently delete subordinate units or records.

Template research is not a substitute for local confirmation. The adopting church must be able to review and adjust every suggested level before activation.
