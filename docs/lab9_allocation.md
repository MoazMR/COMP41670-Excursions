# Lab 9 hotspot allocation

This file prevents duplicate refactoring work in the shared repository. The subsystem allocations below come from the V3 group handover. Each member must still choose a distinct narrow workflow inside the allocated subsystem before refactoring it.

| Team member | Reserved hotspot | Status |
| --- | --- | --- |
| Moaz Refaei | `member-manager/.../MemberManager.java` - registration preparation and commit workflow | Confirmed on `refactor/moaz-member-registration` |
| Joe Biju | Integration and foundation: shared contracts, build, `MeithealService`, simulated clock, composition root, and end-to-end verification | Allocated by V3 handover; choose one narrow Lab 9 workflow |
| Benjamin Long | `ledger-manager/.../LedgerManager.java` - `grantWelcome(MemberId)` workflow | Confirmed on `refactor/benjamin-ledger-welcome-grant` |
| Matthew Gartland | `EngagementManager`: requests, matching, proposals, selection, engagements, confirmations, and cancellation | Allocated by V3 handover; choose one narrow Lab 9 workflow |
| Abdus Samad Bhadmus | CLI: parsing, command dispatch, active-member workflow, rendering, reachability, and scripted CLI tests | Allocated by V3 handover; choose one narrow Lab 9 workflow |

## Collision rule

Each member must remain inside the allocated subsystem and must not refactor another member's narrow workflow. Benjamin's Lab 9 work is limited to the ledger welcome-grant path and must not change shared contracts or another manager.

## Codebase-size note

The codebase is still small, so absolute churn and complexity are low. The Lab 9 brief permits selecting the best available candidate when a mature hotspot does not yet exist. Benjamin's quantified selection and inspection method are recorded in `lab9/hotspot.md`.
