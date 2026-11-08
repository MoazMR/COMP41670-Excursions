# Suggested work allocation

This split gives each person a clear subsystem while Joe owns integration, shared contracts, the simulated clock, and final verification. Each subsystem owner implements production code, focused tests, and a concise handoff note. Business rules stay inside the service and managers, never in the CLI.

## Team responsibilities

| Person | Primary ownership | Required deliverables | Main rules |
| --- | --- | --- | --- |
| Joe Biju | Integration lead, shared architecture, service coordination, simulated clock, and final verification | Define shared typed IDs, manager interfaces, result and error types, immutable views, `MeithealService`, dependency wiring, and the forward-only simulated clock. Coordinate cross-manager operations, resolve recorded amendments, merge completed work, and run the final unit and end-to-end suite. Maintain the rule-to-test checklist. | R12, deterministic time progression, and cross-cutting verification of R1-R14 |
| Moaz Refaei | `MemberManager` and member domain | Implement registration, canonical unique email lookup, ACTIVE and SUSPENDED state, suspension and reinstatement, active-member checks, and member-category publications. Registration coordinates exactly one welcome grant through the service and ledger contract. | R3 and R6 |
| Benjamin Long | `LedgerManager`, balances, holds, and community fund | Implement balances, append-only transactions, welcome grants, credit holds, atomic engagement settlement, levy calculation, the overdraft floor, community grants, balance/history queries, and reports. Provide boundary and invariant tests for every credit mutation. | R1, R2, R3, R7, R10, and R11 |
| Matthew Gartland | `EngagementManager` and engagement domain | Implement requests, read-only matching, helper-submitted dated proposals, proposal lifecycle, engagements, confirmations, and cancellation records. Use the member contract for eligibility and let `MeithealService` coordinate ledger operations. | R4, R5, R8, R9, R13, and R14 |
| Adbus Samad Bhadmus | CLI and user workflow | Implement the console interface and all Section 5 workflows, including parsing, active-member selection, input errors, and immutable result rendering. The CLI calls `MeithealService` only and contains no balance, levy, status, or engagement business rules. Provide scripted CLI tests for successful and rejected paths. | Presentation of all operations; no independent rule enforcement |

## Rule ownership

| Rule | Primary owner | Required collaboration |
| --- | --- | --- |
| R1 - 60 credits per hour | Benjamin | Matthew supplies the validated session duration. |
| R2 - overdraft floor | Benjamin | Matthew provides validated engagement data; Joe coordinates hold and settlement flows. |
| R3 - permitted credit creation | Benjamin | Moaz stages successful registration; Joe verifies the full registration flow. The initial fund is zero unless a binding clarification and recorded amendment permit otherwise. |
| R4 - two confirmations before transfer | Matthew | Benjamin guarantees settlement is atomic and cannot run twice; Joe coordinates the operation. |
| R5 - no self-fulfilment | Matthew | Moaz supplies stable member identity and active-status checks. |
| R6 - suspended-member restrictions | Moaz | Matthew invalidates pending proposals but preserves agreed work. |
| R7 - append-only ledger | Benjamin | All other modules use ledger contracts and never mutate entries directly. |
| R8 - session duration bounds | Matthew | Benjamin calculates credits only from a validated duration. |
| R9 - fulfilment window | Matthew | Adbus parses and passes dates without enforcing the rule. |
| R10 - 5 percent floor levy and floor check | Benjamin | Matthew provides engagement data; Joe coordinates settlement. |
| R11 - community grants | Benjamin | Joe and Adbus expose the documented grant command. The recorded rolling date policy applies. |
| R12 - read-only queries | Joe | Every manager returns immutable snapshots or copies; Adbus only displays them. |
| R13 - configured categories | Matthew | `CategoryCatalog` is fixed at startup; Moaz owns member-category publications and Adbus displays returned categories. |
| R14 - late cancellation | Matthew | Benjamin releases the hold through the coordinated flow. The date-only late-cancellation policy is recorded in the design. |

## Integration contracts

- Joe defines shared interfaces and domain records before subsystem implementation diverges. Parameter meaning, ownership, errors, and amendments must be agreed.
- `MemberManager` owns member identity, canonical email lookup, status, and member-category publications. Other modules query it and must not duplicate member state.
- `LedgerManager` is the only component that changes balances, holds, transactions, or the community-fund balance.
- Registration succeeds only when the member and one 120-credit welcome grant are both created. A failure leaves neither visible.
- `EngagementManager` owns requests, proposals, engagements, confirmations, and cancellation records. Matching is read-only; a helper explicitly submits one dated pending proposal per request.
- `MeithealService` coordinates every cross-manager operation. It creates the engagement and hold during selection, coordinates the second confirmation with settlement, and coordinates cancellation with hold release.
- Settlement is atomic. It creates one positive service-payment transaction and creates a community-levy transaction only when the floored levy is positive. Failure leaves balances, holds, transactions, and engagement state unchanged.
- `SimulatedClock` is shared and forward-only. Advancing it triggers no automatic settlement, cancellation, grant, or other business operation.
- Managers that use dates receive the shared clock or an explicit current date. They never read the machine clock.
- The CLI calls `MeithealService` only. It parses and renders data but does not repeat domain validation.
- Query methods return immutable values or defensive copies so callers cannot mutate internal collections.

## Testing and handoff

Each subsystem owner must provide:

1. Production code for the owned subsystem.
2. Focused tests covering assigned rules, boundaries, repeated calls, and rejected no-state-change cases.
3. A short handoff note describing inputs, outputs, errors, and assumptions.
4. A small feature branch and reviewable pull request. Peer review is recommended, and a merge commit preserves the attributable commit history.

Joe completes integration by:

1. Wiring the shared clock, catalog, IDs, managers, `MeithealService`, and CLI composition root.
2. Checking every rule and invariant has at least one named test.
3. Running all unit tests together on a clean checkout.
4. Adding end-to-end tests for registration, suspension, offers, requests, matching, proposal selection, two-party confirmation, failed settlement, date movement, queries, and reporting.
5. Verifying duplicate operations do not create extra grants, transfers, fees, or ledger entries.
6. Confirming documented policies and amendments are implemented consistently before final acceptance.

## Lab 9 hotspot allocation

| Member | File | Method | Allocation |
| --- | --- | --- | --- |
| Abdus Samad Bhadmus | `cli/src/main/java/ie/ucd/meitheal/cli/CommandParser.java` | `parse(String)` | confirmed 2026-11-08 |
