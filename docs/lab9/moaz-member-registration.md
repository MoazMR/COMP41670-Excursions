# Lab 9 - Moaz member-registration hotspot

Branch: `refactor/moaz-member-registration`

## Scope and ownership

Moaz owns the `MemberManager` registration workflow only: preparation, canonical-email reservation, commit/abort token handling, and member-ID selection. No other teammate's manager module was changed. `docs/lab9_allocation.md` now records every subsystem owner from the V3 group handover; Moaz's row additionally records the exact narrow Lab 9 branch and workflow.

## Hotspot evidence

The repository was very small when measured, so absolute churn was low. At allocation commit `11fceec`, `MemberManager.java` and `MemberManagerTest.java` were the joint-highest-churn Java files in the module at two touching commits each; other production Java files had one. `MemberManager.prepareRegistration` was also the clearest local complexity hotspot: 20 physical lines and cyclomatic-complexity proxy 5.

The proxy used consistently for before/after comparison is `1 + count(if, for, while, case, catch, &&, ||, ?)`, measured per method from the Java source. It is a transparent comparison aid, not a claim of tool-certified McCabe complexity.

## Characterization safety net

Five characterization tests pin:

- invisibility of prepared registrations plus canonical-email reservation;
- exact prepared-member identity and one-shot commit tokens;
- abort token consumption and reservation release;
- rejection of foreign-manager tokens without state change;
- invalid-name precedence over invalid email, including exact code, message, absent cause, and unchanged visible state.

The first four tests were committed before production refactoring in `c0b29ce`. The fifth was added from the adversarial inversion in `43dcf8c` and strengthened in `5d24494`.

## Transformation sequence

1. `286a3e9` - extracted registration checks.
2. `99baec8` - extracted registration publication/consumption mutations.
3. `d4e8e20` - extracted member-ID availability predicate.

Each production commit contains one behavior-preserving Extract Method transformation and was followed by a green focused suite. Raw Copilot output was committed before the human adversarial-test modification.

## Metrics

| Method | Before lines | Before CC proxy | After lines | After CC proxy |
| --- | ---: | ---: | ---: | ---: |
| `prepareRegistration` | 20 | 5 | 12 | 1 |
| `commitRegistration` | 16 | 3 | 13 | 3 |
| `nextMemberId` | 9 | 4 | 8 | 3 |

Extracted helpers are intentionally small: `normalizeAndValidateName` 6/3, `requireEmailAvailable` 7/3, `consumePreparedRegistrationAndPublishMember` 7/1, and `isMemberIdAvailable` 4/2 (lines/CC proxy). Total class length rose from 186 to 202 lines because responsibilities were named explicitly, while the maximum method length fell from 20 to 13 and the maximum method CC proxy fell from 5 to 3.

## Verification

Final `.\gradlew.bat test --rerun-tasks`: `BUILD SUCCESSFUL`; 18 tests, 0 failures, 0 errors, 0 skipped. Full evidence and the debt register are mirrored in the personal portfolio repository under `lab9/`.

## Parallel-work warning compliance

The branch does not edit the shared/app modules, `MeithealService`, shared IDs/results/errors/status enums, grant or cancellation semantics, cross-manager atomicity, or composition-root wiring. Its production diff relative to `feature/moaz-member-manager` is confined to private helper extractions in `MemberManager`. It has not been merged into `main`. Because the underlying feature branch still contains a temporary local `MemberId` and prepared-registration contract, this branch must wait for Joe's V3 foundation fixes, then merge the updated `main` and adapt to the agreed shared contracts before integration.
