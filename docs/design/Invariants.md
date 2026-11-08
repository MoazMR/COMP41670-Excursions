# Invariants and traceability

## Report treatment

The agreed baseline has 13 invariants, which is within the brief's expected range. The following V2 corrections apply before implementation and report submission:

- R12 is best treated as an interface and encapsulation contract rather than a state invariant. The state portion of INV-11 remains the append-only, balance-derived ledger property from R7. R12 still requires direct tests.
- A valid 1 to 19 minute session has a zero-credit levy. The V2 amendment requires one service-payment transaction and a levy transaction only when the calculated levy is positive.

This treatment answers the report template's question about which rule yields no invariant while preserving all required behaviour.

## Invariant map

| ID | State predicate | Brief source | Primary enforcement | Named failing test |
| --- | --- | --- | --- | --- |
| INV-1 | Every request and engagement duration is 1 to 480 whole minutes, and service payment equals duration. | R1, R8 | Request creation and immutable propagation | `requestDurationBoundariesAndValuation` |
| INV-2 | Every ledger balance and available balance is at least -180. | R2, R10 | `LedgerManager` hold and debit validation | `holdAtMinus180SucceedsAndMinus181FailsAtomically` |
| INV-3 | Member total plus fund equals approved initial sources plus 120 times registered member count. | R3, R10, R11 | Initialization, registration, settlement, grant | `systemCreditsRemainConservedAcrossAllTransfers` |
| INV-4 | Each member has exactly one welcome grant, initialization occurs at most once, and no other transaction type creates credits. | R3 | Initialization, registration, transaction restrictions | `repeatedIssuanceCannotMintCredits` |
| INV-5 | Every reference resolves to the correct record type, categories are configured, publications are unique, windows are ordered, and proposal dates lie inside them. | R9, R13 | Typed IDs, catalog, creation and selection checks | `invalidReferencesCategoriesAndWindowsLeaveNoState` |
| INV-6 | Helper and requester are different for every proposal and engagement. | R5 | Matching, proposal submission, selection | `selfFulfilmentIsNeverVisibleOrAccepted` |
| INV-7 | One helper has at most one pending proposal per request, one request has at most one selected proposal, and one selected proposal creates one engagement. | R4, R5, R6, R9 | Map uniqueness and atomic selection | `selectingOneProposalClosesAllCompetingPaths` |
| INV-8 | A pending proposal always has an active requester and active helper. Suspended members may remain only in historical or already agreed work. | R6 | Suspension cleanup and revalidation | `suspensionInvalidatesPendingButPreservesAgreedWork` |
| INV-9 | AGREED maps to HELD, COMPLETED maps to SETTLED, and CANCELLED maps to RELEASED with a valid cancellation record and no settlement transactions. | R2, R4, R10, R14 | Selection, cancellation, settlement | `engagementStatusAndHoldStatusStayEquivalent` |
| INV-10 | COMPLETED exists exactly when both participants confirmed, one service-payment transaction references the engagement, and one levy transaction also references it when the levy is positive. AGREED has at most one confirmation and no settlement transaction. | R1, R4, R7, R10 | Confirmation and idempotent settlement | `secondDistinctConfirmationSettlesExactlyOnce` |
| INV-11 | Transaction IDs are unique, transactions are immutable and append-only, and balances equal the fold of applicable transactions. | R7 | `LedgerManager` | `ledgerHistoryCannotBeChangedOrDivergeFromBalances` |
| INV-12 | The fund is non-negative, every grant is positive and goes to a negative-balance member, and the approved date exclusion is respected. | R3, R11 | Grant validation and transfer | `grantEligibilityAndMonthBoundaryProtectFund` |
| INV-13 | The simulated date starts on or after 1 September 2026, never decreases, and is the only domain date source. | Team time decision supporting R9, R11, R14 | `SimulatedClock` and constructor wiring | `allDateRulesUseOneForwardOnlyClock` |

Test names are recommended names. Replace them with the final names in the report once the suite exists.

The INV-10 wording above is the V2 amendment. Update the design, contracts, diagrams, and tests together when implementing settlement.

## R12 contract tests

R12 remains assessed even if it yields no state invariant. Add tests that:

- Attempt to mutate every returned list and set.
- Confirm the mutation is rejected or affects only the caller's copy.
- Confirm a previously returned view does not change after later service operations.
- Confirm a member can query only their own engagement history if that access rule is implemented at the service boundary.

## Rule-to-owner traceability

| Rule | Short meaning | Primary owner | Collaborators | Main invariant or contract |
| --- | --- | --- | --- | --- |
| R1 | Equal time value | Benjamin | Matthew | INV-1, INV-10 |
| R2 | -180 floor | Benjamin | Matthew, Joe | INV-2, INV-9 |
| R3 | Valid credit sources and conservation | Benjamin | Moaz, Joe | INV-3, INV-4, INV-12 |
| R4 | Two confirmations | Matthew | Benjamin, Joe | INV-7, INV-9, INV-10 |
| R5 | No self-fulfilment | Matthew | Moaz | INV-6 |
| R6 | Suspension restrictions | Moaz | Matthew, Joe | INV-7, INV-8 |
| R7 | Append-only ledger | Benjamin | Joe | INV-10, INV-11 |
| R8 | 0 to 8 hour per-engagement bound | Matthew | Benjamin | INV-1 |
| R9 | Fulfilment window | Matthew | Joe | INV-5, INV-13 |
| R10 | 5 percent floor levy paid on top | Benjamin | Matthew | INV-2, INV-3, INV-9, INV-10 |
| R11 | Community grants | Benjamin | Joe | INV-3, INV-12, INV-13 |
| R12 | Read-only inspection | Joe coordinates | All managers, Adbus | Interface contract tests |
| R13 | Fixed configured categories | Matthew | Moaz, Adbus | INV-5 |
| R14 | Late cancellation record | Matthew | Benjamin, Joe | INV-9, INV-13 |

## Required mutation evidence

For the viva and report, each invariant needs more than a green test. At least once during development:

1. Remove or invert the relevant check locally.
2. Run the named focused test and observe it fail.
3. Restore the correct code.
4. Record the enforcement method and final test name in the report table.

Do not commit intentional mutations to the shared branch. A mutation-testing tool is optional. Manual deliberate mutations are sufficient if the team can explain them.

## High-value boundary set

- Duration: 0, 1, 480, 481.
- Levy: 0, 1, 19, 20, 59, 60, 480.
- Available balance after a new hold: -179, -180, -181.
- Multiple engagements on one date: no aggregate hours cap, with each individual duration still tested at 1 and 480 minutes.
- Window: before earliest, earliest, latest, after latest.
- Confirmation date: day before, scheduled date, day after.
- Cancellation for 4 May: 2 May early, 3 May late, 4 May closed.
- Grant after 3 May: through 3 June blocked, 4 June allowed under the approved rolling policy.
- Clock: start before minimum, start at minimum, same-day advance, backward move, forward move.
- Duplicate operations: registration, initialization, proposal, confirmation, settlement, and grant retry.
