# Shared caller contracts

This document describes the contracts visible to callers of `MeithealService` and the console. It maps ownership and consumers, and records stable results and date-sensitive examples. It does not prescribe publication mechanics.

## Ownership and consumers

| Owner | Responsibility and state | Service consumers | Console consumers |
| --- | --- | --- | --- |
| `MemberManager` (membership) | Owns member records and the canonical email lookup. The member register is the membership state: it describes registered members and their status and skills. | Registration, active-member checks, member lookups, and member views. | Registration and member-facing commands through service results; the console does not access the register directly. |
| `CategoryCatalog` (catalogue) | Supplies the fixed startup catalogue of configured categories. It is read-only after startup and supports category queries. | Validates offer and request categories against the configured set; answers category queries. | Displays category query results returned through the service. |
| `LedgerManager` (ledger) | Owns balances, holds, the community fund, and append-only transactions. | Welcome grants, hold checks and releases, settlement, and community grants. | Displays balance and transaction results returned through the service. |
| `EngagementManager` (engagement) | Owns requests, proposals, engagements, and confirmations. | Request and proposal operations, matching candidates, engagement transitions, and history views. | Creates and inspects work through service operations and renders returned views. |
| `SimulatedClock` (clock) | Owns the simulated current date and its forward-only date contract. It is the domain date source. | Supplies dates for date-sensitive registration policy, matching, confirmation, cancellation, and grant decisions. | Requests simulated date advances through the service and displays the returned date or rejection. |

`MeithealService` is the shared caller boundary and coordinates operations that cross owners. The console calls the service and renders its results; it does not read manager collections or publish manager state itself. Managers own their state and expose immutable views. The clock, catalogue, and ID factory are injected dependencies. Domain behavior does not use the machine clock.

## IDs, results, and query snapshots

References have typed IDs, such as `MemberId`, `RequestId`, `ProposalId`, and `EngagementId`. Callers pass the ID for the corresponding record type; a reference of another type is not interchangeable. IDs are stable references, not permission to inspect or mutate an owner's collection.

Operations return typed views or stable rejected results. A rejected result communicates the operation's failure category to the caller and leaves the relevant observable state unchanged. Technical failure across owners is reported as `ATOMIC_OPERATION_FAILED`; callers can retry after an uncommitted technical failure. Callers should use the returned result rather than infer success from console output or an ID.

Query results are immutable snapshots. A matching query returns candidate requests as a read-only result; it creates no proposal, reservation, or engagement. A previously returned view remains unchanged when later operations change owner state. The console may hold a selected member for subsequent commands, but that selection is only caller context: it does not transfer membership ownership or bypass service validation.

## Registration and configured categories

Registration validates the supplied name and email and uses canonical email lookup to reject an already registered canonical email. A successful registration creates one active member and one 120-credit welcome grant. A duplicate canonical email is rejected without registering another member or issuing another grant. Registration is one cross-manager operation from the caller's perspective: if the grant cannot be committed, neither the new membership nor its grant is observable as committed, and the caller may retry after an uncommitted technical failure.

The category catalogue is fixed at startup and read-only. An offer or request must name a category in that configured catalogue. An unknown category is rejected; it is not added to the catalogue by creating an offer or request.

## Suspension

Suspension blocks the affected member from creating requests, publishing skill offers, submitting proposals, or creating engagements. Selection requires both parties to be active. Suspension invalidates pending proposals involving the suspended member. Existing agreed work is preserved: either participant may still confirm or cancel it under the ordinary engagement rules, and completed work credits are received as usual. A failed suspension preserves observable membership and proposal state.

## Holds, matching, confirmation, and cancellation

`LedgerManager` owns each member's signed whole-credit ledger balance and available balance. The ledger balance is the sum of that member's ledger transactions; the available balance is the ledger balance minus credits held for engagements: `available = ledger - held`. Holds reserve credits without appending ledger transactions. For example, a ledger balance of -60 credits and 120 credits held gives an available balance of -180 credits.

A hold must leave the member's available balance at or above -180. If available balance is -180, a proposed hold that would make it -181 is rejected. The rejected selection publishes no hold and does not change engagement state. If selection fails anywhere in the operation, `LedgerManager`'s observable ledger and hold state and `EngagementManager`'s observable engagement state are preserved. A permitted selection coordinates an engagement with its hold; a failure before either is committed preserves both owners' observable state so the caller can retry after an uncommitted technical failure.

Matching is a read-only query for eligible open requests. It returns candidate requests as an immutable snapshot and has no side effects. Proposal submission is a separate operation and rechecks applicable member and request conditions.

Publishing a skill offer records a member's skill in the catalogue-facing member view. Submitting a proposal is a separate action that responds to a request with a proposed date; an offer does not create a proposal.

The first distinct participant confirmation records that actor and leaves the engagement awaiting the other participant. A repeat confirmation by the same actor is rejected as a duplicate and does not count as the second confirmation. The second distinct confirmation settles the hold once: it records the service payment and records a levy entry only when the calculated levy is positive. If settlement encounters an uncommitted technical failure, the second confirmation is not recorded, no settlement transaction is visible, and the hold remains held; the caller can retry. This preserves both engagement and ledger state.

For an engagement scheduled on 4 May, cancellation is early on 2 May, late on 3 May, and closed from 4 May onward. A permitted cancellation releases the hold and records cancellation, with no transfer or settlement transaction. Invalid cancellation or an uncommitted technical failure leaves both engagement and ledger state unchanged, allowing retry after the technical failure.

## Grant and clock date examples

A community grant is eligible only when the member's actual ledger balance is negative, the requested amount is positive, and the community fund has sufficient credits. A community grant approved on 3 May blocks another grant through 3 June. On 4 June, the member may qualify again if all other grant conditions hold. A denied grant does not transfer fund credits or append a grant transaction, and preserves the last-approved date. `LedgerManager` owns grant eligibility, fund credits, grant transactions, and the last-approved date. The `SimulatedClock` supplies the date recorded on a welcome grant transaction.

The simulated clock starts on or after 1 September 2026 and never moves backward. A same-day advance returns the unchanged date. A backward advance is rejected and does not change the date. A forward advance returns the new date. Date-sensitive service operations use this simulated date; `SimulatedClock` owns the date and `MeithealService` supplies it to date-sensitive operations.

## Stable caller-facing failures

| Code | Meaning |
| --- | --- |
| `SUSPENDED_MEMBER` | Suspended status blocks a new action. |
| `INSUFFICIENT_AVAILABLE_CREDITS` | A selection hold would cross the -180 available-balance floor. |
| `INSUFFICIENT_COMMUNITY_FUND_CREDITS` | The requested grant exceeds community fund credits. |
| `NOT_PARTICIPANT` | The actor is outside the engagement's participants. |
| `INVALID_STATE` | The engagement status disallows the action, such as confirming or cancelling completed work. |
| `CANCELLATION_CLOSED` | The cancellation date boundary has passed. |
| `ATOMIC_OPERATION_FAILED` | An uncommitted technical failure occurred. |

These codes are distinct caller results; the service declarations identify the applicable result for each operation.

## Cross-owner failure contract

For an operation coordinated across owners, validation failure or an uncommitted technical failure preserves both owners' observable state. In particular, callers must not observe a member without its welcome grant, an engagement without its corresponding hold, a completed engagement without settlement, or a cancelled engagement with a still-held balance. A technical failure before commit returns a stable failure result and leaves the operation retryable. Successful calls expose the corresponding owner state consistently. These are caller contracts; the mechanism used to prepare, commit, or publish state is an implementation choice.
