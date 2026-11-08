# Before/after metrics

## Method

IntelliJ Inspect Code was unavailable. The file was inspected through the VS Code workflow and compared using reproducible source measurements. Physical line counts include the method declaration, braces, and wrapped statements. The cyclomatic-complexity proxy is:

```text
1 + count(if, for, while, case, catch, &&, ||, ?)
```

This is the same transparent proxy used in the team's existing Lab 9 evidence. It supports a consistent before/after comparison and is not presented as tool-certified McCabe complexity. Consequently, no IntelliJ warning-count figure is claimed.

## Revisions compared

- Before: `4848de2`, the committed ledger baseline immediately before Lab 9.
- After: `9ca7f58`, the corrected transaction-construction extraction and adversarial test.

## Results

| Measure | Before | After | Change |
| --- | ---: | ---: | ---: |
| `LedgerManager` physical lines | 93 | 107 | +14 |
| `grantWelcome` physical lines | 19 | 12 | -7 (-36.8%) |
| `grantWelcome` CC proxy | 3 | 1 | -2 (-66.7%) |
| Maximum method length in `LedgerManager` | 19 | 14 | -5 (-26.3%) |
| Maximum method CC proxy in `LedgerManager` | 3 | 3 | unchanged |
| Java-history touches to `LedgerManager` at final measurement | 2 at selection | 5 | +3 Lab 9 changes |

The class grew because eligibility and construction responsibilities now have explicit private helper names. The hotspot method became shorter and branch-free, while the class-wide maximum complexity stayed at 3 because the extracted eligibility predicate and the pre-existing balance fold still contain decisions. This is a local improvement rather than a claim that all class debt has been removed.

## Transformation sequence

1. `145165d` extracted `ensureWelcomeGrantDoesNotExist`.
2. `81b3b04` preserved Copilot's raw transaction-construction extraction.
3. Fresh adversarial inversion identified caller-sensitive suppliers as a concrete behavior difference.
4. `9ca7f58` kept supplier evaluation in `grantWelcome`, limited the helper to construction, and added the counterexample characterisation test.

All original characterisation tests remained green after the first extraction and raw proposal. The expanded suite exposed the adversarial difference before the raw proposal was accepted as the final design.
