# Lab 9 Copilot transcript - Moaz Refaei

Date: 2026-11-08  
Tool: GitHub Copilot Chat in VS Code, Copilot Fusion (selected GPT-6 Luna)

This record preserves the prompts and the material output used for the Lab 9 refactoring. Routine file reads and command echoes are omitted; the resulting code is preserved by the referenced commits.

## Transformation 1 - extract registration checks

### Prompt

> Lab 9 refactoring transformation 1. Work only in `member-manager/src/main/java/ie/ucd/meitheal/membermanager/MemberManager.java`. Perform one behavior-preserving Extract Method refactoring in `prepareRegistration`: extract name normalization/validation and canonical-email availability checking into clearly named private helpers. Preserve the public API, synchronized locking, validation order, exact exception codes/messages, ID and token generation timing, state mutation order, and returned object identity. Do not edit tests or any other file and do no unrelated cleanup. Run `.\gradlew.bat :member-manager:test` and summarize the exact change.

### Material output

Copilot extracted `normalizeAndValidateName` and `requireEmailAvailable`, leaving orchestration and state publication in `prepareRegistration`. It reported the focused member-manager tests green. The untouched generated diff is commit `286a3e9`.

### Human review and decision

Decision: **Modified**. The extraction itself was retained, but adversarial review identified validation precedence as the most plausible hidden regression. A human-written characterization test was added in `43dcf8c` and strengthened to assert the absent exception cause in `5d24494`.

## Adversarial inversion

### Prompt

> Lab 9 adversarial inversion. Do not edit any files. Compare MemberManager.java before commit 286a3e9 (use c0b29ce:member-manager/src/main/java/ie/ucd/meitheal/membermanager/MemberManager.java) with the current 286a3e9 version. Assume the refactor is wrong and try hard to produce concrete inputs or interleavings that change observable behavior. Check null/blank/whitespace names, malformed emails, inputs that are invalid in more than one way, duplicate committed and reserved canonical emails, trimming and case differences, generated ID timing, token identity/reservation timing, exception code/message/order/cause, synchronization, and concurrent calls. For each candidate, state the exact setup, call, and expected before-versus-after result. Conclude which single candidate is most plausible to add as a characterization test even if you find no actual difference. Report only analysis; make no workspace changes.

### Material output

Copilot found no concrete semantic difference. It examined name null/blank handling, malformed email handling, simultaneous invalid inputs, committed and reserved duplicate emails, trimming/case normalization, ID/token timing, exception details, locking, and concurrent calls. Its strongest candidate was `prepareRegistration(" \t ", "not-an-email")`: both versions should throw `INVALID_NAME` first with message `Member name must not be blank`, no cause, and no state change. This became the added characterization test.

## Transformation 2 - extract registration publication

### Prompt

> Lab 9 refactoring transformation 2. Work only in member-manager/src/main/java/ie/ucd/meitheal/membermanager/MemberManager.java. Perform exactly one behavior-preserving Extract Method in commitRegistration: after the existing conflict check, extract the five map mutation statements that consume the prepared registration and publish its member into one clearly named private helper. Preserve public API, synchronized locking, validation/conflict timing, exact exception behavior, mutation order, object identity, and returned instance. Do not edit tests or any other file and do no unrelated cleanup. Run `.\gradlew.bat :member-manager:test` and summarize the exact change.

### Material output

Copilot correctly noted that the method contained four map mutations, not five. It extracted all four, unchanged and in the same order, to `consumePreparedRegistrationAndPublishMember`. It reported the focused tests green and `git diff --check` clean. Untouched output: commit `99baec8`. Decision: **Accepted** after diff inspection and an independent focused test run.

## Transformation 3 - name member ID availability

### Prompt

> Lab 9 refactoring transformation 3. Work only in member-manager/src/main/java/ie/ucd/meitheal/membermanager/MemberManager.java. Perform exactly one behavior-preserving Extract Method in nextMemberId: extract the existing availability condition into a clearly named private boolean helper taking the candidate MemberId. Preserve the exact condition, loop behavior, ID sequence, public API, synchronization, and exception behavior. Do not edit tests or any other file and do no unrelated cleanup. Run `.\gradlew.bat :member-manager:test` and summarize the exact change.

### Material output

Copilot extracted the original two-part condition verbatim to `isMemberIdAvailable(MemberId candidate)`, changed no other file, and reported the focused tests green. Untouched output: commit `d4e8e20`. Decision: **Accepted** after diff inspection and an independent focused test run.

## Final independent verification

- `.\gradlew.bat :member-manager:test --rerun-tasks`: successful after every transformation and after the human test strengthening.
- `.\gradlew.bat test --rerun-tasks`: successful, 18 tests, 0 failures, 0 errors, 0 skipped.
- `git diff --check`: clean.
