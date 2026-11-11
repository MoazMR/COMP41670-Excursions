# MemberManager handoff

## Public inputs and outputs

- Construct `MemberManager` with the fixed set of configured `CategoryId` values.
- The legacy constructor creates a private lock. Coordinating application code can use `MemberManager(lock, configuredCategories)` so every manager read and write synchronizes on the same lock.
- `prepareRegistration(memberId, name, email)` accepts the service-assigned shared `MemberId`, trims and validates the name and email, rejects an already committed or reserved canonical email with `DUPLICATE_EMAIL`, then rejects a null, committed, or pending ID with `INVALID_REGISTRATION`. The supplied ID is carried unchanged into `PreparedRegistration`, which holds an `ACTIVE` member that is not visible in member lookups or snapshots. A failed attempt reserves no email.
- `commitRegistration(prepared)` makes the member visible and returns an immutable `Member`. `abortRegistration(prepared)` discards the reservation and staged member. A registration token is single-use and belongs to the manager that created it.
- `withRegistration(id, name, email, beforePublish)` validates name, email syntax, canonical email uniqueness, then null/committed/pending ID availability. It constructs the member and replacement committed indexes before calling `beforePublish` under the shared lock. The callback can read the old state. If it throws, the same throwable propagates and no member is published; a normal return publishes the prepared indexes and returns the callback's exact result (including `null`).
- Writes through any `MemberManager` mutation, including legacy prepare/commit/abort, are rejected while its registration callback is active. Reads remain available, and the callback guard clears on both success and failure.
- `findById` and `findByEmail` return `Optional<Member>`. Email lookup uses the same trim-and-case-fold canonicalization as registration. `getMembers` returns an immutable snapshot.
- `suspend` and `reinstate` return updated immutable member snapshots. `isActive` checks a known member; `requireActive` returns the member or rejects a suspended member.
- `publishCategory` returns `true` for a new publication and `false` for a category already published by that member. Only configured categories are accepted. Member category sets and the configured-category set are immutable.

## Errors

`MemberManagerException` supplies a `Code` for rejected operations:

- `INVALID_NAME`, `INVALID_EMAIL`: missing/blank name or malformed email on registration; malformed email on lookup.
- `DUPLICATE_EMAIL`: the canonical email is already committed or reserved by a prepared registration.
- `MEMBER_NOT_FOUND`: a required member lookup or operation references an unknown/null member ID.
- `MEMBER_SUSPENDED`: `requireActive` is called for a suspended member.
- `INVALID_STATE`: suspension or reinstatement is attempted from the wrong status.
- `INVALID_CATEGORY`: publication references a null or unconfigured category.
- `INVALID_REGISTRATION`: a registration token is foreign, unknown, consumed, or conflicts with committed state.

`CategoryId` rejects null or blank values with `IllegalArgumentException`; configured-category construction rejects null collections and null entries with `NullPointerException` or `IllegalArgumentException`, respectively.

## Assumptions and future integration boundary

- Email lookup and uniqueness deliberately use case-insensitive comparison after trimming. The displayed `EmailAddress` retains the trimmed input spelling.
- Invalid status transitions are rejected rather than treated as idempotent; rejected calls leave the member state unchanged.
- Registration IDs are allocated by the service owner. Registration does not create, prepare, or commit credits. The service can call `withRegistration` with its assigned ID and stage the matching ledger change in the callback; if preparation fails or the callback throws, the member remains unpublished. Cross-manager atomic publication still requires the service and participating managers to share the same lock and prepare their changes before assignment.
- The manager uses `ie.ucd.meitheal.shared.id.MemberId` from `:shared`; internal registration reservation tokens remain UUIDs. The prepare/commit/abort API is the member-side boundary only; cross-manager atomicity remains an integration responsibility.
