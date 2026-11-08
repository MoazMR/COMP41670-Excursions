# MemberManager handoff

## Public inputs and outputs

- Construct `MemberManager` with the fixed set of configured `CategoryId` values.
- `prepareRegistration(name, email)` trims the name and email, validates both, reserves the case-insensitive canonical email, and returns a `PreparedRegistration`. The prepared member has an `ACTIVE` status and a stable typed `MemberId`, but is not visible in member lookups or snapshots.
- `commitRegistration(prepared)` makes the member visible and returns an immutable `Member`. `abortRegistration(prepared)` discards the reservation and staged member. A registration token is single-use and belongs to the manager that created it.
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
- Registration does not create, prepare, or commit credits. The service should prepare the member, ask `LedgerManager` to stage exactly one 120-credit welcome grant for the prepared `MemberId`, then coordinate both commits as one operation. On preparation failure it should abort the member registration; on transaction failure neither the member nor the grant should become visible.
- The repository does not yet contain the shared service, ledger transaction, manager interface, or shared ID/result/error contracts. The prepare/commit/abort API is the member-side boundary only; true cross-manager atomicity remains an integration responsibility once those contracts are defined.
