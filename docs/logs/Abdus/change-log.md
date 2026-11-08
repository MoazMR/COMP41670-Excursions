# Abdus - Change Log

Record each commit made while working on an individual branch. Add the commit before opening or updating a pull request.

| Date | Branch | Commit SHA | Change summary | Verification | PR |
| --- | --- | --- | --- | --- | --- |
| 2026-11-08 | feat/cli-command-loop | 193a15c | Isolated CLI command loop with immutable parsing, quoted and escaped arguments, injected I/O, help/exit/error recovery, focused tests and README guidance; shared startup wiring remains owned by the foundation. | `.\gradlew.bat clean test --console=plain` under Java 21.0.12.1 and Gradle 8.10.2: BUILD SUCCESSFUL in 32s; 10 JUnit tests, zero failures/errors. Evidence: [cli-baseline-test-output.txt](cli-baseline-test-output.txt). | Not opened |
| 2026-11-08 | refactor/abdus-command-parser | cd83222 | 23 new characterisation cases pinning observed parser behaviour; production code unchanged. | `.\gradlew.bat test --console=plain` succeeded; 23 new characterisation tests, 5 original parser tests, and 4 original loop tests passed. Evidence: portfolio `lab9/evidence/characterisation-tests-session.txt` and `lab9/evidence/characterisation-before-tests.txt`. | Not opened |
| 2026-11-08 | refactor/abdus-command-parser | Not committed | One escape-decoding extraction into private `decodeQuotedEscape(String,int)` plus ten candidate tests. | Baseline and refactored full suites each passed 43 cases with zero failures/errors. Evidence: portfolio `lab9/evidence/escape-baseline-expanded-tests.txt` and `lab9/evidence/refactor-escape-tests.txt`. | Not opened |
