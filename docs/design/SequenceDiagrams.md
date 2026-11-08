# Core sequence flows

## Registration and welcome grant

```mermaid
sequenceDiagram
    actor User
    participant CLI
    participant Service as MeithealService
    participant Members as MemberManager
    participant Ledger as LedgerManager

    User->>CLI: register name and email
    CLI->>Service: registerMember(name, email)
    Service->>Members: validate and prepare ACTIVE member
    Members-->>Service: prepared MemberId
    Service->>Ledger: prepare welcome grant(MemberId)
    alt all preparation succeeds
        Service->>Members: commit member
        Service->>Ledger: commit 120-credit transaction
        Service-->>CLI: MemberView
    else validation or grant fails
        Service-->>CLI: failure with no state change
    end
    CLI-->>User: render result
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

    Helper->>CLI: find-matches
    CLI->>Service: findMatchingRequests(helperId)
    Service->>Clock: currentDate()
    Service->>Members: require active helper and requesters
    Service->>Work: calculate eligible open requests
    Work-->>Service: immutable candidates
    Service-->>CLI: candidates
    Note over Work: Read-only. No proposal or reservation exists yet.
    Helper->>CLI: submit-proposal(requestId, date)
    CLI->>Service: submitProposal(helperId, requestId, date)
    Service->>Members: recheck active members
    Service->>Work: validate and store PENDING proposal
    Note over Work: No aggregate helper daily-hours reservation applies.
    Service-->>CLI: ProposalView or failure
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

    Requester->>CLI: select-proposal(proposalId)
    CLI->>Service: selectProposal(requesterId, proposalId)
    Service->>Members: recheck active requester and helper
    Service->>Work: validate open request and pending proposal
    Service->>IDs: new EngagementId
    Service->>Ledger: prepare hold(payment + levy)
    Ledger->>Ledger: require available balance >= -180
    Service->>Work: prepare selection, closure, rejection, engagement
    alt all preparation succeeds
        Service->>Ledger: commit HELD hold
        Service->>Work: commit prepared work state
        Service-->>CLI: EngagementView
    else any check or staging fails
        Service-->>CLI: failure with no state change
    end
```

## Two confirmations and settlement

```mermaid
sequenceDiagram
    actor Participant
    participant CLI
    participant Service as MeithealService
    participant Work as EngagementManager
    participant Ledger as LedgerManager
    participant Clock as SimulatedClock

    Participant->>CLI: confirm-completion(engagementId)
    CLI->>Service: confirmCompletion(engagementId, actorId)
    Service->>Clock: currentDate()
    Service->>Work: validate participant, date, state, duplicate
    alt first confirmation
        Service->>Work: record actor only
        Service-->>CLI: awaiting other participant
    else second confirmation
        Service->>Ledger: prepare settlement by engagement ID
        Ledger->>Ledger: prepare service payment and any positive levy transaction
        Service->>Work: prepare second confirmation and COMPLETED
        alt settlement preparation and commit succeed
            Service->>Ledger: commit prepared transactions and SETTLED hold
            Service->>Work: commit confirmation and COMPLETED
            Service-->>CLI: completed EngagementView
        else technical failure
            Service-->>CLI: ATOMIC_OPERATION_FAILED
            Note over Work,Ledger: No second confirmation, no transaction, hold remains HELD
        end
    end
```

## Cancellation

```mermaid
sequenceDiagram
    actor Participant
    participant CLI
    participant Service as MeithealService
    participant Work as EngagementManager
    participant Ledger as LedgerManager
    participant Clock as SimulatedClock

    Participant->>CLI: cancel-engagement(engagementId)
    CLI->>Service: cancelEngagement(engagementId, actorId)
    Service->>Clock: currentDate()
    Service->>Work: validate participant, AGREED, today before date
    Service->>Ledger: prepare hold release
    Service->>Work: prepare cancellation record and state change
    alt valid and all preparation succeeds
        Service->>Ledger: commit RELEASED hold
        Service->>Work: commit CANCELLED and cancellation record
        Service-->>CLI: cancelled EngagementView
    else invalid or technical failure
        Service-->>CLI: failure with no state change
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

    Member->>CLI: apply-grant(amount)
    CLI->>Service: applyForCommunityGrant(memberId, amount)
    Service->>Clock: currentDate()
    Service->>Ledger: validate positive amount, actual balance, fund, date
    alt approved
        Ledger->>Ledger: transfer fund to member and append transaction
        Ledger-->>Service: CommunityGrantView
    else denied
        Ledger-->>Service: failure and no state change
    end
    Service-->>CLI: result
```
