# Final CLI test log

Executed on 21 November 2026 (Europe/Dublin) on macOS arm64 with Eclipse Temurin OpenJDK 21.0.13+9-LTS. Recovery main was `b0613e11a3da102249be55d7cfdb5df0d5189a0e`. The last feature merge was `9a4bd5f5eda6cd547ce1c93e7c69608e9ce66872`. Both runs used the installed launcher. The second used **main SHA plus staged changes** listed in [the automated audit](test-audit.md).

With `JAVA_HOME` pointing to Temurin 21, run `./gradlew clean build test`, then `./gradlew :cli:installDist` and `./cli/build/install/cli/bin/cli`. Each replay supplied one input per line on stdin. Start a fresh process for each session A-E, enter its setup, then run its rows in order. Enter 0 at the main menu to exit. Each row was also checked by replaying the inputs from the start of its session.

## Input key

Read left to right. Each code token is one literal input line. Bold actions expand as follows.

- **register Name** enters `1, Name, lowercase-name@example.org, 0`. **login Name** enters `2, 2, member ID`. Pat, Alex, Bea and Cal receive IDs 100001 through 100004 in registration order.
- **offer c** enters `3, c, 0`. **request c/m** enters `4, c, m, 2026-09-01, 2026-09-04, 0`. The **May** suffix changes the window to 2027-05-03 through 2027-05-04. Category numbers 1, 2 and 3 are Gardening, Cooking and Carpentry.
- **propose n date** enters `5, n, date, 0, 0`. **inspect req-n** enters `5, R, req-n, 0`. **select req-n** enters `6, req-n, 1, 0`.
- **balance**, **ledger** and **engagements** enter `11, 1, 0, 0`, `11, 2, 0, 0` and `11, 3, 0, 0` respectively. **report** enters `12, 0`.

## Shared setup

Sessions A and C start at the signed-out main menu.

**Session B.** **register Pat** **register Alex** **register Bea** **login Pat** **request 1/20** **request 1/20** **request 1/20**

**Session D.** **register Pat** **register Alex** **register Bea** **register Cal** **login Pat** **request 1/20** **login Alex** **offer 1** **propose 1 2026-09-02** **login Pat** **select req-1** **login Alex** **request 2/20** **login Pat** **offer 2** **propose 1 2026-09-02** **request 3/20** **login Bea** **offer 3** **propose 1 2026-09-02** **request 1/20** **login Cal** **offer 3** **propose 1 2026-09-02** **offer 1** **propose 2 2026-09-02**

**Session E.** **register Pat** **register Alex** **login Pat** **request 1/20** **request 1/60** **login Alex** **offer 1** **propose 1 2026-09-02** **propose 2 2026-09-02** **login Pat** **select req-1** **select req-2**

## Executed cases

| Rule tested | Actions entered | Expected result | Observed result | Pass/fail | Notes |
|---|---|---|---|---|---|
| 1. Signed-out access | <code>3</code> | Require login. | Login is required to publish a skill offer. | Pass | A. R6. signed out. |
| 2. Register and inspect welcome history | **register Pat** **login Pat** **ledger** **report** | One welcome entry for 120. circulation 120. | 100001 had one WELCOME_GRANT for 120. One member. combined total 120. | Pass | A. R3, INV-4. |
| 3. Canonical email duplicate | <code>1</code> <code>Other</code> <code>&nbsp.PAT@EXAMPLE.ORG&nbsp.</code> <code>0</code> **ledger** **report** | Duplicate refusal. same one entry, one member and total 120. | DUPLICATE_EMAIL. one WELCOME_GRANT, Members: 1, combined total 120. | Pass | A. R3, INV-4. |
| 4. Requester excluded from own matches | **offer 1** <code>5</code> <code>0</code> | No own request appears even with the skill. | No matching requests. | Pass | B. R5, INV-6. |
| 5. Eligible helper matching | **login Alex** <code>5</code> <code>0</code> **offer 1** <code>5</code> <code>0</code> | Publishing Gardening makes the three open requests visible. | No matching requests before publication. req-1, req-2 and req-3 after it. | Pass | B. R13. all requests are Gardening. |
| 6. Outside fulfilment window | **propose 1 2026-09-05** <code>5</code> <code>H</code> <code>0</code> | Refuse the day after the end. helper history stays empty. | BUSINESS_REJECTION for 2026-09-05. The helper then saw “You have no proposals.” | Pass | B. R9, INV-5. |
| 7. Inclusive final day | **propose 1 2026-09-04** <code>5</code> <code>H</code> <code>0</code> | Accept the final day and store the submitted date. | prop-1 PENDING for req-1 on 2026-09-04. | Pass | B. R9, INV-5. |
| 8. Personal proposal visibility | **login Bea** **offer 1** **propose 2 2026-09-02** **login Alex** <code>5</code> <code>H</code> <code>0</code> | Alex sees only prop-1. Bea owns prop-2. | Bea submitted prop-2. Alex’s list contained only prop-1 for req-1. | Pass | B. R12, INV-16. |
| 9. Withdraw a pending proposal | <code>5</code> <code>H</code> <code>1</code> <code>0</code> **login Pat** **inspect req-1** | Requester sees the withdrawn status. | prop-1 was WITHDRAWN in the request owner’s list. | Pass | B. INV-7. |
| 10. Reject a pending proposal | <code>5</code> <code>R</code> <code>req-2</code> <code>1</code> <code>0</code> **login Bea** <code>5</code> <code>H</code> <code>0</code> | Helper sees the rejected status. | prop-2 was REJECTED in Bea’s own list. | Pass | B. INV-7. |
| 11. Unrelated request inspection | **inspect req-2** **login Pat** **inspect req-2** | Refuse Bea access. Pat still sees the same rejected proposal. | NOT_PARTICIPANT. Pat still saw prop-2 REJECTED. | Pass | B. R12. |
| 12. Terminal proposal refusal | <code>5</code> <code>R</code> <code>req-2</code> <code>1</code> <code>0</code> **inspect req-2** | Refuse repeat rejection. preserve status. | INVALID_STATE. prop-2 remained REJECTED. | Pass | B. INV-7. |
| 13. Select and reserve | **login Alex** **propose 3 2026-09-02** **login Pat** **select req-3** **balance** **engagements** | 20-minute agreement holds 21. 99 available. | eng-1 AGREED. posted 120, held 21, available 99. | Pass | B. R2, R10. the 1-credit levy is reserved. |
| 14. Advance past agreed work | <code>13</code> <code>1</code> <code>2026-09-05</code> <code>0</code> <code>0</code> **balance** **engagements** | Advancing alone preserves the agreement and reservation from the previous row. | eng-1 still AGREED on 2026-09-02. posted 120, held 21, available 99. | Pass | B. INV-9, INV-13. |
| 15. Advance while signed out | <code>13</code> <code>0</code> <code>13</code> <code>1</code> <code>2027-05-03</code> <code>0</code> <code>0</code> | Show September 1. allow May 3 without login. | Current simulated date changed from 2026-09-01 to 2027-05-03. | Pass | C. INV-13. |
| 16. Same-day advance | <code>13</code> <code>1</code> <code>2027-05-03</code> <code>0</code> <code>0</code> | Accept the same date. | Simulated date set to 2027-05-03. the date menu still showed May 3. | Pass | C. INV-13. |
| 17. Backward advance | <code>13</code> <code>1</code> <code>2027-05-02</code> <code>0</code> <code>0</code> | Reject and keep May 3. | BUSINESS_REJECTION. current simulated date remained 2027-05-03. | Pass | C. INV-13. |
| 18. Malformed date and Back | <code>13</code> <code>1</code> <code>2027-02-30</code> <code>0</code> <code>0</code> | Reprompt, then return through date menu to main without changing May 3. | Enter a date in YYYY-MM-DD format, or 0 Back. Date menu showed May 3, then main showed option 13. | Pass | C. INV-13. line-oriented Back. |
| 19. First confirmation | **register Pat** **register Alex** **login Pat** **request 1/120 May** **login Alex** **offer 1** **propose 1 2027-05-03** **login Pat** **select req-1** <code>7</code> <code>eng-1</code> <code>0</code> **ledger** **balance** **engagements** | Record one confirmation with no settlement. | Waiting for the other participant. Only WELCOME_GRANT in history. 120 posted, 126 held, -6 available. AGREED. | Pass | C. R4, INV-10. |
| 20. Second confirmation and conservation | **login Alex** <code>7</code> <code>eng-1</code> <code>0</code> **balance** **login Pat** **balance** **ledger** **report** | Transfer 120 to helper and 6 to fund. circulation 240. | Helper 240, requester -6, held 0. SERVICE_PAYMENT 120, LEVY 6, fund 6, combined total 240. | Pass | C. R1, R3, R4, R10. |
| 21. Community grant transfer | <code>10</code> <code>2</code> <code>0</code> **balance** **ledger** **report** | Transfer two fund credits to the negative member and start the wait on May 3. | Approved 2 on 2027-05-03. Pat -4, FUND to MEMBER 100001 in history, fund 4, total 240. | Pass | C. R3, R11. |
| 22. Grant waiting-period boundary | <code>13</code> <code>1</code> <code>2027-06-03</code> <code>0</code> <code>0</code> <code>10</code> <code>2</code> <code>0</code> **balance** **ledger** **report** <code>13</code> <code>1</code> <code>2027-06-04</code> <code>0</code> <code>0</code> <code>10</code> <code>2</code> <code>0</code> **balance** **ledger** **report** | June 3 refuses with identical history, balances and report. June 4 permits a second grant. | June 3 BUSINESS_REJECTION: Pat -4, fund 4, one grant. June 4 approved 2: Pat -2, fund 2, two grants. Both totals 240. | Pass | C. R11, INV-12, INV-15. The previous row supplies May 3 approval. |
| 23. Suspend and clean pending proposals | **login Pat** **inspect req-1** **inspect req-3** **balance** **engagements** **login Alex** **inspect req-2** **login Bea** **inspect req-4** <code>9</code> <code>pat@example.org</code> <code>1</code> <code>0</code> **login Pat** **inspect req-1** **inspect req-3** **balance** **engagements** **login Alex** **inspect req-2** **login Bea** **inspect req-4** | Invalidate only the three affected pending proposals. Keep the separate proposal and agreement. | prop-2, prop-3 and prop-4 INVALIDATED. prop-5 PENDING, prop-1 SELECTED, eng-1 AGREED, held 21. | Pass | D. R6, INV-8, INV-9. |
| 24. Reinstate without reviving proposals | <code>9</code> <code>100001</code> <code>2</code> <code>0</code> **login Pat** **offer 1** **login Pat** **inspect req-1** **inspect req-3** **balance** **engagements** **login Alex** **inspect req-2** **login Bea** **inspect req-4** | Permit a new offer and preserve every invalidation. | Member reinstated. Gardening published. prop-2, prop-3 and prop-4 still INVALIDATED, prop-5 still PENDING. | Pass | D. R6, INV-8. |
| 25. Cancel only one of two agreements | **balance** **ledger** <code>8</code> <code>eng-1</code> <code>0</code> **balance** **ledger** **engagements** **report** | Release 21 of 84 held. preserve the other 63, history and total. | Late cancellation released eng-1. Held 84 to 63, available 36 to 57, posted 120. eng-1 CANCELLED, eng-2 AGREED, no payment, total 240. | Pass | E. R14, INV-9. September 1 is the day before the appointment. |
