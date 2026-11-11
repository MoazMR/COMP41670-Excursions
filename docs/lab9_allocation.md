# Lab 9 hotspot allocation

This file prevents duplicate refactoring work in the shared repository. The subsystem allocations below come from the V3 group handover. A member should still name the narrow method or workflow used as their individual Lab 9 hotspot before refactoring it.

| Team member | Reserved hotspot | Status |
| --- | --- | --- |
| Moaz Refaei | `member-manager/.../MemberManager.java` - registration preparation and commit workflow | Confirmed on `refactor/moaz-member-registration` |
| Joe Biju | Integration and foundation: shared contracts, build, `MeithealService`, simulated clock, composition root, and end-to-end verification | Allocated by V3 handover; choose one narrow Lab 9 workflow |
| Benjamin Long | `LedgerManager`: balances, holds, immutable transactions, fund, settlement, grants, and reports | Allocated by V3 handover; choose one narrow Lab 9 workflow |
| Matthew Gartland | `EngagementManager`: requests, matching, proposals, selection, engagements, confirmations, and cancellation | Allocated by V3 handover; choose one narrow Lab 9 workflow |
| Abdus Samad Bhadmus | CLI: parsing, command dispatch, active-member workflow, rendering, reachability, and scripted CLI tests | Allocated by V3 handover; choose one narrow Lab 9 workflow |

## Collision rule

No other member should use Moaz's `MemberManager` registration path for Lab 9. Each person should choose a distinct narrow hotspot inside their V3-owned subsystem and record the branch before starting. Moaz will not refactor another owner's module.

## Codebase-size note

At allocation time, only the Gradle setup and Moaz's newly implemented `member-manager` production code were present on the branch. The lab brief permits choosing the best available candidate when the codebase is still too small for a mature hotspot. `MemberManager.java` is the strongest available candidate because it has the highest production Java churn count (2) and contains the longest and highest-decision-count workflow in the current codebase.

## Interim parallel-work constraint

The V3 parallel-work warning blocks changes to shared/app contracts, `MeithealService`, shared IDs/results/errors/statuses, cross-manager atomicity, final wiring, and other unresolved rules until Joe's foundation fixes are merged. Moaz's Lab 9 branch changes only private `MemberManager` helpers, focused characterization tests, and personal evidence. It remains unmerged and must be updated from the corrected `main` before any integration or public-contract adaptation.
