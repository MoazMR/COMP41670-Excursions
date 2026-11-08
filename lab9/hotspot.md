# Lab 9 Hotspot - Benjamin Long

The selected hotspot is `ledger-manager/src/main/java/ie/ucd/meitheal/ledger/LedgerManager.java`, focused narrowly on the `grantWelcome(MemberId)` workflow. At commit `4848de2`, Git history reports two commits touching `LedgerManager.java`, making it joint-highest for Java-file churn on this branch, while its 93 physical lines make it the largest production class currently available. IntelliJ Inspect Code was unavailable, so the source inspection was performed using the project's VS Code workflow and reproducible command-line measurements. `grantWelcome` spans 19 physical lines and has a cyclomatic-complexity proxy of 3, calculated as `1 + count(if, for, while, case, catch, &&, ||, ?)`. More importantly, the method combines several reasons to change: null validation, welcome-grant eligibility checking, ID and date dependency calls, transaction construction, ledger mutation, and returning the recorded transaction. The absolute complexity is modest because the project is still small, but the combination of highest available churn, class size, and responsibility density makes this the best available hotspot and gives the welcome-grant workflow the highest local refactoring interest. The existing team allocation reserves `LedgerManager` for Benjamin, separately from Moaz's member registration, Matthew's engagement workflow, Abdus's CLI work, and Joe's integration/foundation work.

## Measurement method

Churn was measured with the Lab 9 command:

```bash
git log --format= --name-only -- '*.java' | sort | uniq -c | sort -rn | head -20
```

The complexity proxy is the same transparent comparison measure used in the team's existing Lab 9 evidence. It is a before/after comparison aid rather than a claim of tool-certified McCabe complexity.
