# Core sequence flows

These diagrams describe the intended workflows. CLI-to-service calls use the names and argument order in [MeithealService.java](../../app/src/main/java/ie/ucd/meitheal/app/MeithealService.java). Actions entered by users describe intent. Manager messages describe each owner's responsibility. A note spanning two owners shows the outcome that callers must observe together. The service holds one lock while the managers prepare both changes and make them visible together. [Shared caller contracts](SharedContracts.md) and [architecture decisions](Architecture.md#how-managers-make-a-change-together)

## Registration and welcome grant

```mermaid
sequenceDiagram
    actor User
    participant CLI
    participant Service as MeithealService
    participant Members as MemberManager
    participant Ledger as LedgerManager
    participant Clock as SimulatedClock
    participant IDs as IdFactory

    User->>CLI: Register with name and email
    CLI->>Service: register(name, email)
    Service->>IDs: newMemberId()
    Service->>Members: Validate name, email and canonical email uniqueness, prepare ACTIVE member for this ID
    alt Invalid input or duplicate email
        Service-->>CLI: MALFORMED_INPUT or DUPLICATE_EMAIL
    else Valid new member
        Service->>Clock: currentDate()
        Service->>IDs: newTransactionId()
        Service->>Ledger: Prepare 120-credit entry with member ID, transaction ID and simulated date
        alt Registration succeeds
            Note over Members,Ledger: ACTIVE member and one welcome transaction become visible together
            Service-->>CLI: MemberView
        else Uncommitted technical failure
            Note over Members,Ledger: Neither record is visible. Registration can be retried.
            Service-->>CLI: ATOMIC_OPERATION_FAILED
        end
    end
```

## Matching and proposal submission

```mermaid
sequenceDiagram
    actor Helper
    participant CLI
    participant Service as MeithealService
    participant Members as MemberManager
    participant Work as EngagementManager
    participant Clock as SimulatedClock
    participant IDs as IdFactory

    Helper->>CLI: Find matching requests
    CLI->>Service: matchingRequests(helperId)
    Service->>Clock: currentDate()
    Service->>Members: Check member eligibility and skills
    Service->>Work: matchingRequests(helperId)
    Work-->>Service: Immutable snapshot of eligible open requests or rejection
    Service-->>CLI: Matching result
    Note over Work: The query creates no proposal or reservation.
    Helper->>CLI: Propose a date for a request
    CLI->>Service: submitProposal(helperId, requestId, proposedDate)
    Service->>Members: Recheck active helper and requester
    alt Eligible members
        Service->>IDs: newProposalId()
        Service->>Work: submitProposal(helperId, requestId, proposalId, proposedDate)
        Work->>Work: Check request, date and pending proposal uniqueness
        Work-->>Service: PENDING ProposalView or rejection
        Service-->>CLI: Proposal result
    else Member check fails
        Service-->>CLI: Rejection
    end
    Note over Work: A pending proposal reserves no helper hours.
```

## Proposal selection and agreement

```mermaid
sequenceDiagram
    actor Requester
    participant CLI
    participant Service as MeithealService
    participant Members as MemberManager
    participant Work as EngagementManager
    participant Ledger as LedgerManager
    participant IDs as IdFactory

    Requester->>CLI: Select a proposal
    CLI->>Service: selectProposal(requesterId, proposalId)
    Service->>Work: Check requester ownership, open request and pending proposal
    Service->>Members: Check that requester and helper are active
    alt Selection is eligible
        Service->>IDs: newEngagementId()
        Service->>Ledger: Check available balance after holding payment plus levy
        alt Hold would leave less than -180
            Service-->>CLI: INSUFFICIENT_AVAILABLE_CREDITS
        else Selection succeeds
            Note over Work,Ledger: AGREED engagement and HELD reservation become visible together
            Note over Work: Close the request, select this proposal and reject competing pending proposals in the same change.
            Service-->>CLI: EngagementView
        else Uncommitted technical failure
            Service-->>CLI: ATOMIC_OPERATION_FAILED
        end
    else Ownership, state or membership check fails
        Service-->>CLI: Rejection
    end
    Note over Work,Ledger: A failed selection preserves the request, proposals, engagements and holds.
```

## Two confirmations and settlement

```mermaid
sequenceDiagram
    actor Person as Participant
    participant CLI
    participant Service as MeithealService
    participant Work as EngagementManager
    participant Ledger as LedgerManager
    participant Clock as SimulatedClock

    Person->>CLI: Confirm completion
    CLI->>Service: confirmEngagement(participantId, engagementId)
    Service->>Clock: currentDate()
    Service->>Work: Check participant, date, engagement state and prior confirmations
    alt Confirmation is invalid or repeated
        Service-->>CLI: Rejection, including DUPLICATE for a repeated first confirmation
    else First distinct confirmation
        Service->>Work: Record the actor and keep the engagement AGREED
        Service-->>CLI: EngagementView
    else Second distinct confirmation
        Service->>Ledger: Prepare settlement of this engagement's hold
        alt Settlement succeeds
            Note over Work,Ledger: Second confirmation, COMPLETED engagement and SETTLED hold become visible together
            Note over Ledger: Record one service payment and a levy entry only if the levy is positive.
            Service-->>CLI: EngagementView
        else Uncommitted technical failure
            Note over Work,Ledger: Only the first confirmation remains. The hold stays HELD and no settlement entries appear.
            Service-->>CLI: ATOMIC_OPERATION_FAILED
        end
    end
```

## Cancellation

```mermaid
sequenceDiagram
    actor Person as Participant
    participant CLI
    participant Service as MeithealService
    participant Work as EngagementManager
    participant Ledger as LedgerManager
    participant Clock as SimulatedClock

    Person->>CLI: Cancel an engagement
    CLI->>Service: cancelEngagement(participantId, engagementId)
    Service->>Clock: currentDate()
    Service->>Work: Check participant, AGREED state and date before scheduled day
    alt Invalid participant, state or date
        Service-->>CLI: NOT_PARTICIPANT, INVALID_STATE or CANCELLATION_CLOSED
    else Cancellation permitted
        Service->>Ledger: Prepare release of the engagement hold
        alt Cancellation succeeds
            Note over Work,Ledger: CANCELLED engagement, cancellation record and RELEASED hold become visible together
            Note over Work: Record actor and date. Cancellation on the preceding day is late.
            Note over Ledger: Releasing the hold creates no transfer.
            Service-->>CLI: EngagementView
        else Uncommitted technical failure
            Note over Work,Ledger: Engagement stays AGREED, hold stays HELD and no cancellation is recorded.
            Service-->>CLI: ATOMIC_OPERATION_FAILED
        end
    end
```

## Suspension and pending proposals

```mermaid
sequenceDiagram
    participant CLI
    participant Service as MeithealService
    participant Members as MemberManager
    participant Work as EngagementManager

    CLI->>Service: suspendMember(memberId)
    Service->>Members: Check member exists and can be suspended
    alt Unknown or already suspended
        Service-->>CLI: Rejection
    else Eligible for suspension
        Service->>Work: Identify pending proposals involving this helper or requester
        alt Suspension succeeds
            Note over Members,Work: SUSPENDED status and invalidation of those proposals become visible together
            Note over Work: Existing AGREED engagements still allow ordinary confirmation, settlement and cancellation.
            Service-->>CLI: MemberView
        else Uncommitted technical failure
            Note over Members,Work: Member status and pending proposals are preserved.
            Service-->>CLI: ATOMIC_OPERATION_FAILED
        end
    end
```

## Community grant

```mermaid
sequenceDiagram
    actor Member
    participant CLI
    participant Service as MeithealService
    participant Ledger as LedgerManager
    participant Clock as SimulatedClock

    Member->>CLI: Apply for a grant
    CLI->>Service: applyForCommunityGrant(memberId, amount)
    Service->>Clock: currentDate()
    Service->>Ledger: Check positive amount, negative actual ledger balance and sufficient fund credits
    Ledger->>Ledger: For repeat grants, require today strictly after last approval plus one month
    alt Approved
        Ledger->>Ledger: Transfer fund credits, append the grant transaction and update the approval date
        Ledger-->>Service: CommunityGrantView
    else Denied
        Ledger-->>Service: Rejection with balances, history and last approval date preserved
    end
    Service-->>CLI: Grant result
```
