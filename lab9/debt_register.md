# Lab 9 debt register

The refactoring deliberately preserved current behavior. The following debt remains and should not be silently treated as resolved.

| Remaining debt | Classification | Why it remains / future action |
| --- | --- | --- |
| `MemberId` and `TransactionId` are temporary ledger-local contracts rather than Joe's shared project IDs. | Deliberate and prudent | The V3 parallel-work warning reserves shared contracts and integration for Joe. Replacing these types now would create conflicting contracts. Adapt the ledger after the shared IDs are merged. |
| The unrestricted `Supplier` dependencies can observe call stacks, which constrains otherwise harmless helper extraction. | Inadvertent and prudent to defer | Adversarial inversion exposed this behavior and the new characterisation test pins it. A later behavioral change should replace unrestricted suppliers with narrow ID/date provider interfaces and explicitly define their observable contract. That is outside a behavior-preserving refactor. |
| `LedgerManager` uses coarse-grained intrinsic synchronization and mutable in-memory collections. | Deliberate and prudent | It is adequate for the current in-memory baseline and keeps operations atomic, but scalability and persistence requirements are unresolved. Revisit only when the integration architecture defines concurrency and storage expectations. |
| `transactions()` duplicates the global `history()` view. | Inadvertent and prudent to defer | Removing or renaming a public method would change the API during a refactoring-only lab. Resolve the preferred query contract during integration and migrate callers with separate compatibility tests. |
| The private `append` method remains synchronized even though current calls already hold the `grantWelcome` monitor. | Inadvertent and prudent to defer | The synchronization is redundant for the current call path but removing it could affect future or reflective call assumptions. Reassess after the final ledger publication API is agreed. |

No functional debt above was fixed during Lab 9 because each item requires either an agreed shared contract or an explicit behavior-changing task with new specification tests.
