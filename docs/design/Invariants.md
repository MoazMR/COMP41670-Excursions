| INVARIANTS | Description |
| --- | --- |
| INV-1 | Every session lasts more than 0 and at most 8 hours, at exactly 60 credits per hour. |
| INV-2 | Every member balance is at least -180 credits. |
| INV-3 | Each member has exactly one 120-credit welcome grant, and total system credits equal all welcome grants issued. |
| INV-4 | An engagement's provider and requester are different members. |
| INV-5 | Only ACTIVE members create requests, publish offers, or enter new engagements. |
| INV-6 | Each engagement transfers credits at most once, and only after both parties confirm. |
| INV-7 | Every transfer has exactly one append-only ledger entry. |
| INV-8 | Every engagement date lies within its request's fulfilment window. |
| INV-9 | Every request and offer uses a category from the fixed configured category set. |
| INV-10 | The fund balance is non-negative, receives floor(5% of each transfer), and grants only negative-balance members once per month. |
