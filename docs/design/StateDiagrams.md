# State machines

## Member

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: registration and welcome grant commit
    ACTIVE --> SUSPENDED: suspend
    SUSPENDED --> ACTIVE: reinstate
```

Suspension prevents new work and invalidates pending proposals involving the member. It does not cancel an agreed engagement or prevent valid completion and settlement.

## Request

```mermaid
stateDiagram-v2
    [*] --> OPEN: valid request raised
    OPEN --> CLOSED: one proposal selected
    CLOSED --> [*]
```

Request terms are immutable after creation. A changed category, duration, or window requires a new request.

## Proposal

```mermaid
stateDiagram-v2
    [*] --> PENDING: helper submits a dated proposal
    PENDING --> SELECTED: requester selects
    PENDING --> REJECTED: requester rejects
    PENDING --> WITHDRAWN: helper withdraws
    PENDING --> INVALIDATED: either participant is suspended
    SELECTED --> [*]
    REJECTED --> [*]
    WITHDRAWN --> [*]
    INVALIDATED --> [*]
```

`PENDING` has no time-capacity reservation. A helper may have work exceeding 480 total minutes on a date, provided each individual engagement remains within the R8 duration bound.

## Engagement

```mermaid
stateDiagram-v2
    [*] --> AGREED: proposal selected and hold created
    AGREED --> AGREED: first distinct confirmation
    AGREED --> COMPLETED: second confirmation and settlement commit
    AGREED --> CANCELLED: valid pre-date cancellation
    COMPLETED --> [*]
    CANCELLED --> [*]
```

A failed second-confirmation settlement leaves the engagement `AGREED`, retains only the first confirmation, and leaves the hold `HELD`.

## Credit hold

```mermaid
stateDiagram-v2
    [*] --> HELD: agreed engagement reserves payment plus levy
    HELD --> SETTLED: successful second confirmation
    HELD --> RELEASED: valid cancellation
    SETTLED --> [*]
    RELEASED --> [*]
```

`HELD` changes available balance but not ledger balance. `RELEASED` creates no transaction. `SETTLED` corresponds to one service-payment transaction and one levy transaction when the levy is positive.

## State equivalence

```mermaid
flowchart LR
    A[AGREED engagement] <--> H[HELD hold]
    C[COMPLETED engagement] <--> S[SETTLED hold]
    X[CANCELLED engagement] <--> R[RELEASED hold]

    C --> T1[SERVICE_PAYMENT transaction]
    C --> T2[COMMUNITY_LEVY transaction]
    X --> N[No settlement transaction]
```
