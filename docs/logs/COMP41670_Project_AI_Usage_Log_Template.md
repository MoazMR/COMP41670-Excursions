# COMP41670 Group Software Project - AI Usage Log

One row per significant use of an AI tool. Every team member logs their own use. Graded on critical engagement, not on volume.

| Date | Member | Task / feature | Tool & version | What you asked for (summary) | What the model produced (summary) | Accepted / Modified / Rejected | What you changed, and why | How you verified it | Commit SHA |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-10-27 | A. Murphy | Levy calculation (R10) | GitHub Copilot / IntelliJ | Asked for a method applying the 5% community levy to a transfer. | Returned a method using Math.round on the levy and taking it from the provider's side. | Modified | Two faults: R10 rounds DOWN, not to nearest, and the levy is paid by the requester, not the provider. Rewrote using integer division and moved the deduction to the requester. | Added LevyTest boundary cases at 1, 19 and 20 credits; INV-6 total-conservation test now passes. | a1b2c3d |

## How the AI Usage Log is graded

9 of the 60 deliverable marks. Read this before you start logging.

| Topic | Guidance |
| --- | --- |
| The standard | The log is graded on critical engagement with AI output, exactly like your Lab Portfolio. A complete log of uncritical acceptance scores low even if the code works. |
| What earns marks | Naming the specific output, the specific fault, the mechanism that surfaced it, and the specific fix. "Copilot suggested a method, I fixed it" tells a marker nothing. |
| Log as you go | A log written in one sitting in Week 12 is visible as one - the entries lose their detail and stop matching the commit history. It is also a fair topic for your viva. |
| Every member logs | The log should show all members. A log written entirely by one person suggests one person used the tools, and the vivas will test that. |
| Commit SHA | Link each entry to where the code landed. This is what makes the log verifiable and what protects you if a contribution is queried. |
| Rejected counts too | Entries where you rejected the output are among the most valuable - they show judgement. Log them. |
| Honesty | Undisclosed AI use is academic misconduct under the UCD Academic Integrity Policy. Disclosed use is expected and is never penalised. |
