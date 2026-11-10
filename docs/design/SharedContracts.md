# Shared caller contracts

A shared contract tells a caller what it can pass in, what comes back, what may change, and what remains true when a call fails. Use the Java declarations for method names and types. Use this document for the behavior and ownership that a signature alone cannot describe. [Architecture.md](Architecture.md) explains the design choices behind these boundaries.

## What is available now

| Surface | Where to build against it | What is still missing from the baseline |
| --- | --- | --- |
| Service API consumed by the CLI | [`MeithealService`](../../app/src/main/java/ie/ucd/meitheal/app/MeithealService.java) declares caller operations, including registration, requests, selection, settlement, and grants. | Concrete service and runtime wiring. CLI parsing, dispatch, and rendering can be tested against a substitute service. |
| Manager interfaces consumed by the service | [`MemberManagerPort`](../../shared/src/main/java/ie/ucd/meitheal/shared/port/MemberManagerPort.java), [`EngagementManagerPort`](../../shared/src/main/java/ie/ucd/meitheal/shared/port/EngagementManagerPort.java), and [`LedgerManagerPort`](../../shared/src/main/java/ie/ucd/meitheal/shared/port/LedgerManagerPort.java). | The ports declare reads and some owner-local writes. Registration, hold and settlement publication, and other coordinated writes still need owner-side Java declarations. A caller method in `MeithealService` does not imply those manager methods exist. |
| Shared vocabulary and dependencies | [Shared Java types](../../shared/src/main/java/ie/ucd/meitheal/shared) provide IDs, views, results, failure codes, the catalogue interface, a simulated clock interface, and an ID factory interface. | Concrete catalogue, clock, and ID providers and their bindings. |


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

The first successful registration receives member ID `100001`. Each subsequent successful registration receives the next six-digit number, up to `999999`. A failed registration leaves the next number available. The service supplies the member ID, and the CLI and all managers use it unchanged.

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

For an operation coordinated across owners, validation failure or an uncommitted technical failure preserves both owners' observable state. In particular, callers must not observe a member without its welcome grant, an engagement without its corresponding hold, a completed engagement without settlement, or a cancelled engagement with a still-held balance. A technical failure before commit returns a stable failure result and leaves the operation retryable. Successful calls expose the corresponding owner state consistently. The service holds one shared lock while the managers prepare their changes and replace their live records. [How managers make a change together](Architecture.md#how-managers-make-a-change-together)

| Joint operation | Successful state visible to callers | Failure case to verify with the real components |
| --- | --- | --- |
| Registration | One active member and one 120-credit welcome grant. | Fail welcome-grant publication and verify that neither the member nor the grant is visible. Retry and verify exactly one of each. |
| Proposal selection | One agreed engagement, one hold for the full charge, and competing proposals closed. | Attempt a hold that would leave -181 available. Verify the request remains open, the proposals are unchanged, and there is no engagement or hold. |
| Second completion confirmation | Both confirmations recorded and the engagement settled once with its matching ledger entries. | Fail settlement after the first confirmation. Verify the first confirmation and hold remain, while the second confirmation and settlement entries are absent. Retry and verify one settlement. |
| Cancellation | The engagement is cancelled, the cancellation is recorded, and the hold is released without a transfer. | Fail hold release. Verify the engagement remains agreed, the original hold remains, and no cancellation record or transfer appears. |
| Suspension | The member is suspended and pending proposals involving that member are invalidated. Existing agreed work remains available under its usual rules. | Fail proposal invalidation. Verify member status and pending proposals are unchanged. |

## Inputs for coordinated changes

| Operation | Data passed between service and managers |
| --- | --- |
| Registration | The service obtains a new member ID and welcome transaction ID from the injected ID factory and reads the simulated date once. Membership receives the member ID, name and email, checks the canonical email and prepares the active member. Ledger receives the same member ID, the transaction ID and the date, and prepares the one-time 120-credit welcome entry. The service returns the member view with the resulting ledger balance. |
| Selection | Engagement checks the caller owns the request and the proposal is pending on that open request. Membership confirms both people are eligible. The service allocates an engagement ID. Engagement prepares the selection using the request, proposal and engagement IDs. It supplies the requester ID, helper ID and validated duration from the selected work. Ledger uses those values to calculate payment and levy, check the available-balance floor and prepare a hold keyed by engagement ID. |
| Confirmation and settlement | The service passes the participant ID, engagement ID and simulated date to engagement. The first valid confirmation updates engagement alone. For the second distinct confirmation, ledger looks up the held amounts and parties by engagement ID. It prepares one dated payment to the helper and a dated levy to the fund only if positive. The service supplies a fresh transaction ID from the factory for each entry. Engagement prepares completion in the same operation. |
| Cancellation | Engagement receives the participant ID, engagement ID and simulated date. It validates the request and prepares the actor, date and late flag. Ledger receives the engagement ID and prepares release of that hold without a transaction. |
| Suspension and eligibility | The service reads membership status through `isEligible(memberId)` and skills through `findById(memberId)`. For suspension, membership prepares the status change and engagement receives the same member ID. Engagement invalidates every pending proposal where that member is the helper or owns the request. Both changes take effect together. Existing agreed engagements retain their ordinary confirmation and cancellation rules. |

## Startup catalogue

Matthew supplies the UTF-8 resource `app/src/main/resources/categories.csv` with the header `id,name`. The initial rows are `gardening,Gardening`, `cooking,Cooking` and `carpentry,Carpentry`. Joe loads and validates it once at startup. A missing resource, blank field or duplicate ID stops startup with a configuration error. Membership and engagement use the same read-only catalogue. The CLI lists the IDs and display names returned by the service. Integration tests use these stable IDs. Manager unit tests may inject a smaller catalogue.

## First console results

| Command outcome | What the CLI displays |
| --- | --- |
| Registration succeeds | Member ID, name, status and ledger credits from the returned `MemberView`. For a new member the status is ACTIVE and the ledger balance is 120 credits. |
| Balance succeeds | Member ID, ledger balance, held credits and available credits from the returned `BalanceView`. Label each amount and preserve its sign. |
| Either command fails | The stable failure code and the service's detail when present. Print one failure message. |

If a future change affects a Java signature, result code or observable result, update the declaration, the affected contract and its focused test together. Include the owner and the callers affected by the change.
