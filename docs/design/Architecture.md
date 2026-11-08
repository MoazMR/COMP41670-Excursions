# Component architecture

This is the recommended single diagram for the design report. It shows ownership and dependency direction without implementation detail.

```mermaid
flowchart LR
    User[User] --> CLI[Console CLI]
    CLI --> Service[MeithealService]

    Service --> Members[MemberManager]
    Service --> Engagements[EngagementManager]
    Service --> Ledger[LedgerManager]
    Service --> Clock[SimulatedClock]
    Service --> Catalog[CategoryCatalog]
    Service --> IDs[IdFactory]

    Engagements --> Clock
    Ledger --> Clock
    Members --> Catalog

    Members --- MemberState[(Members, email index, status, skills)]
    Engagements --- WorkState[(Requests, proposals, engagements, confirmations)]
    Ledger --- CreditState[(Balances, holds, fund, transactions)]

    classDef boundary fill:#eef5ff,stroke:#345995,color:#111;
    classDef service fill:#fff2cc,stroke:#8a6d1d,color:#111;
    classDef manager fill:#e8f5e9,stroke:#2e7d32,color:#111;
    classDef infra fill:#f3e5f5,stroke:#6a1b9a,color:#111;
    classDef state fill:#f7f7f7,stroke:#666,color:#111;

    class CLI boundary;
    class Service service;
    class Members,Engagements,Ledger manager;
    class Clock,Catalog,IDs infra;
    class MemberState,WorkState,CreditState state;
```

## Dependency rules

- The CLI depends on `MeithealService`, never on manager collections.
- The service coordinates operations crossing manager boundaries.
- Managers own state and return immutable views.
- The clock, catalog, and ID factory are injected dependencies.
- No component reads the machine clock.
- No manager reaches into another manager's collection.
- No database, network, authentication, or background scheduler component exists in core scope.

## Conservation equation

```text
member balance total + community fund balance
= approved initial external source + 120 * registered member count
```

The V2 policy sets the initial fund to zero, so the external-source term is zero. A non-zero seed requires a binding clarification and recorded amendment.
