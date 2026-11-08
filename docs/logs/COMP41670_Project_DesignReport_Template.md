# COMP41670 SOFTWARE ENGINEERING - TRIMESTER 1, 2026/27

# Group Project - Design Report

Template - maximum 8 pages excluding appendices

## How to use this template

Replace every italic prompt with your own content, and delete the prompts. Keep the section headings - they map onto the marking rubric, and a marker looking for your testing strategy should not have to hunt for it.

Eight pages is a maximum, not a target. Argument is marked; padding is not. A page that explains an approach you rejected and why is worth more than three pages describing what you built.

| Field | Your entry |
| --- | --- |
| Team name |  |
| Repository URL |  |
| Members (name - student number - GitHub username) |  |
| Date submitted |  |

## 1. The invariant list

The heart of the report. List your invariants INV-1 upward. For each: the statement, the rule(s) in the brief it derives from, where it is enforced in your code, and the test that fails if it is violated. Add rows as needed.

| ID | Invariant (true of any valid system state) | From rule(s) | Enforced at | Test |
| --- | --- | --- | --- | --- |
| INV-1 |  |  |  |  |
| INV-2 |  |  |  |  |
| INV-3 |  |  |  |  |
| INV-4 |  |  |  |  |
| INV-5 |  |  |  |  |
| INV-6 |  |  |  |  |

Then, in prose: which rule in Section 3 of the brief yielded no invariant, and why? Which two rules were in tension, how did you resolve the tension, and what did the alternative resolution cost?

### Rules that yielded no invariant, and the tension you found

## 2. Architecture

What are the main components, what is each responsible for, and how do they collaborate? A diagram is welcome - one clear diagram, not five. Then: the two or three decisions you would defend in a review, each with the alternative you rejected and the reason.

### Overview

| Decision | What you chose | Alternative rejected | Why |
| --- | --- | --- | --- |
| D1 |  |  |  |
| D2 |  |  |  |
| D3 |  |  |  |

## 3. Design patterns applied

For each pattern: where you used it, what varies that made it the right answer, and what would have gone wrong without it. Do not list patterns you did not use, and do not apply a pattern to justify listing it - an unnecessary pattern costs marks in this section, it does not earn them.

| Pattern | Where | What varies / why this pattern | What it bought you |
| --- | --- | --- | --- |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |

## 4. Testing strategy

What did you test, at what level, and why that split? Show how the invariant list drove the test suite. Include your coverage position - and, more usefully, name a test that would fail if a specific check were removed, and one place where you know the tests are weaker than you would like.

### Strategy

### Where the suite is weakest, and what you would test next

## 5. AI use - summary

The full record is in your AI Usage Log. Here, in half a page: where AI helped most, where it produced something plausible but wrong, and how you caught it. The specific example is what earns marks.

### Summary

## 6. Known limitations

What does not work, what is unfinished, and what you would change with another two weeks. Honesty is marked positively here; a report claiming a flawless system that the markers can break in five minutes is marked negatively.

### Limitations

## 7. Contribution statement

For each member, two or three lines on what they owned. This is read alongside the commit history and is used in the individual vivas.

| Member | What they owned | Approx. share |
| --- | --- | --- |
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |

## 8. Post-delivery reflection

Half a page, written after the code is finished. What would you do differently if you started this project again on Monday? Answer as engineers, not as students - the interesting answer is about process and judgement, not about time management.

### Reflection
