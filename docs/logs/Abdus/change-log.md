# Abdus - Change Log

Record each commit made while working on an individual branch. Add the commit before opening or updating a pull request.

| Date | Branch | Commit SHA | Change summary | Verification | PR |
| --- | --- | --- | --- | --- | --- |
| 2026-11-08 | feat/cli-command-loop | Not committed | Isolated CLI command loop with immutable parsing, quoted and escaped arguments, injected I/O, help/exit/error recovery, focused tests and README guidance; shared startup wiring remains owned by the foundation. | `.\gradlew.bat clean test --console=plain` under Java 21.0.12.1 and Gradle 8.10.2: BUILD SUCCESSFUL in 32s; 10 JUnit tests, zero failures/errors. Evidence: [cli-baseline-test-output.txt](cli-baseline-test-output.txt). | Not opened |
