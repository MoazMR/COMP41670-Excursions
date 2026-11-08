# Lab 9 hotspot allocation

This file prevents duplicate refactoring work in the shared repository. Each member must confirm a distinct production-code hotspot before starting.

| Team member | Reserved hotspot | Status |
| --- | --- | --- |
| Moaz Refaei | `member-manager/.../MemberManager.java` - registration preparation and commit workflow | Confirmed on `refactor/moaz-member-registration` |
| Joe Biju | Not yet recorded | Confirmation required |
| Benjamin Long | Not yet recorded | Confirmation required |
| Matthew Gartland | Not yet recorded | Confirmation required |
| Abdus Samad Bhadmus | Not yet recorded | Confirmation required |

## Collision rule

Until the table is updated, no other member should use Moaz's `MemberManager` registration path for Lab 9. Moaz will not refactor another owner's module. Unconfirmed rows are deliberately left unassigned rather than presenting an allocation that the team has not agreed.

## Codebase-size note

At allocation time, only the Gradle setup and Moaz's newly implemented `member-manager` production code were present on the branch. The lab brief permits choosing the best available candidate when the codebase is still too small for a mature hotspot. `MemberManager.java` is the strongest available candidate because it has the highest production Java churn count (2) and contains the longest and highest-decision-count workflow in the current codebase.
