# Engagement Manager handoff (request-creation slice)

## What this slice implements

This slice implements request creation and read/query behavior only.

### Provisional integration contracts

Temporary contracts were added under `ie.ucd.meitheal.engagement.port`:

- `MemberId(UUID value)`
- `CategoryId(String value)`
- `MemberReadAccess`
  - `boolean exists(MemberId id)`
  - `boolean isActive(MemberId id)`
- `CategoryReadAccess`
  - `boolean isKnown(CategoryId id)`

These are intentionally minimal and read-only for this phase.

### Domain added in engagement package

- `RequestId(UUID value)`
- `RequestStatus { OPEN, CLOSED }`
- `Request` immutable record with:
  - `RequestId id`
  - `MemberId requesterId`
  - `CategoryId categoryId`
  - `RequestDuration duration`
  - `RequestWindow window`
  - `RequestStatus status`

All fields are non-null enforced in constructors.

### Manager behavior implemented

`EngagementManager` now supports:

- `createRequest(requestId, requesterId, categoryId, duration, window)`
- `findRequestById(requestId)`
- `getRequests()`

Creation uses provisional `RequestCreationResult`:

- `Success(Request request)`
- `Failure(RequestCreationError code, String message)`

`RequestCreationError` values:

- `NOT_FOUND`
- `SUSPENDED_MEMBER`
- `INVALID_CATEGORY`
- `INVALID_STATE`

Rules enforced:

- All method arguments are required; null is treated as programmer error (`NullPointerException`).
- Unknown requester -> `NOT_FOUND`.
- Suspended requester -> `SUSPENDED_MEMBER`.
- Unknown category -> `INVALID_CATEGORY`.
- Duplicate request id -> `INVALID_STATE`, no overwrite.
- Successful creation stores exactly one request with `OPEN` status.
- No current-date checks are applied to request windows in this slice.
- Queries return immutable snapshots.
- Business rejection paths leave state unchanged.

## Migration notes for shared contracts and Joe's convention

This implementation is intentionally provisional and local to `engagement-manager`.

When shared contracts are available:

1. Replace provisional `port` IDs with Joe's shared ID types.
2. Replace `RequestCreationResult` and `RequestCreationError` with shared result/error contracts.
3. Keep manager read dependencies interface-based, but bind them to shared member/category boundaries.
4. For multi-manager state changes in later slices, follow Joe's prepared-change convention instead of introducing a separate transaction framework.

## Out of scope in this slice

- Request matching
- Proposal lifecycle
- Engagement lifecycle
- Cancellation records
- Financial operations
- CLI wiring
