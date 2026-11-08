# Domain model and ownership

```mermaid
classDiagram
    class Member {
      MemberId id
      String name
      EmailAddress email
      MemberStatus status
      Set~CategoryId~ offeredCategories
    }

    class Request {
      RequestId id
      MemberId requesterId
      CategoryId categoryId
      int durationMinutes
      LocalDate earliestDate
      LocalDate latestDate
      RequestStatus status
    }

    class Proposal {
      ProposalId id
      RequestId requestId
      MemberId helperId
      LocalDate proposedDate
      ProposalStatus status
    }

    class Engagement {
      EngagementId id
      ProposalId selectedProposalId
      EngagementStatus status
      Set~MemberId~ confirmations
      CancellationRecord cancellation
    }

    class CreditHold {
      EngagementId engagementId
      MemberId requesterId
      long amountCredits
      CreditHoldStatus status
    }

    class Transaction {
      TransactionId id
      LocalDate date
      TransactionType type
      AccountRef debtor
      AccountRef creditor
      long amountCredits
      DomainReference reference
    }

    class CategoryCatalog {
      Set~CategoryView~ categories
    }

    Member "1 requester" --> "0..*" Request
    Member "1 helper" --> "0..*" Proposal
    Request "1" --> "0..*" Proposal
    Proposal "0..1 selected" --> "0..1" Engagement
    Engagement "1" --> "1" CreditHold
    Engagement "1 completed" --> "1..2 settlement" Transaction
    Member "0..*" --> "0..*" CategoryCatalog : publishes configured category
```

## Cardinality rules

- One member can publish many different categories.
- One member-category pair is unique.
- One request can receive many proposals.
- One helper has at most one pending proposal for one request.
- One request has at most one selected proposal.
- One selected proposal creates exactly one engagement.
- One engagement has exactly one hold keyed by engagement ID.
- One completed engagement has one service-payment transaction and, when levy is positive, one levy transaction with the same engagement reference.

## Normalization rules

- Relationships store typed IDs, not copied mutable entities.
- A proposal stores its request ID and helper ID.
- An engagement stores its selected proposal ID.
- Request details remain the authoritative category, duration, window, and requester.
- A hold is a financial lock, not a transaction.
- Transactions are immutable transfer facts and are never edited or deleted.

## Ownership diagram

```mermaid
flowchart TB
    MM[MemberManager] --> M[Member]
    MM --> E[Canonical email index]
    MM --> O[Published member-category associations]

    EM[EngagementManager] --> R[Request]
    EM --> P[Proposal]
    EM --> G[Engagement]
    EM --> C[Lifecycle validation]

    LM[LedgerManager] --> B[Member balances]
    LM --> F[Community fund]
    LM --> H[Credit holds]
    LM --> T[Append-only transactions]
```
