# Sequence diagrams

Each sequence was discussed as a team in a meeting, then sketched and hand-drawn. The register-member sketch below is one example.

![Register-member sequence sketch](../../files/attachements/register-member-sequence-sketch.png)

ChatGPT was then used to convert the sketches into PlantUML code, producing cleaner rendered diagrams and preserving them as documentation.

All diagrams were verified by the team for quality. They communicate the intended design and interactions, rather than exact function calls or implementation details.

## Register a member

```plantuml
@startuml
title Register a member

actor User
boundary CLI
control MemberManager
control LedgerManager

User -> CLI : Select "Register member"
CLI -> User : Ask for name and email
User -> CLI : Enter name and email
CLI -> MemberManager : registerMember(name, email)
activate MemberManager

MemberManager -> MemberManager : Check that the email is not registered

alt New member
    MemberManager -> MemberManager : Create member with ACTIVE status
    MemberManager -> LedgerManager : Apply welcome grant (120 credits)
    activate LedgerManager
    LedgerManager -> LedgerManager : Update member balance
    LedgerManager -> LedgerManager : Append welcome-grant entry to ledger
    LedgerManager --> MemberManager : Grant recorded
    deactivate LedgerManager
    MemberManager --> CLI : Registration successful
    CLI --> User : Show success message
else Duplicate email
    MemberManager --> CLI : Registration failed - duplicate member
    CLI --> User : Show duplicate-member error
end

deactivate MemberManager
@enduml
```

## Suspend or reinstate a member

```plantuml
@startuml
title Suspend or reinstate a member

actor User
boundary CLI
control MemberManager

User -> CLI : Select "Suspend / reinstate member"
CLI -> User : Ask for member email address
User -> CLI : Enter email address
CLI -> MemberManager : findMemberByEmail(email)
activate MemberManager
MemberManager --> CLI : Member and current status
deactivate MemberManager
CLI --> User : Show current status and options:\nsuspend or reinstate
User -> CLI : Select status action
CLI -> MemberManager : updateMemberStatus(email, action)
activate MemberManager

alt Suspend selected
    MemberManager -> MemberManager : Set member status to SUSPENDED
else Reinstate selected
    MemberManager -> MemberManager : Set member status to ACTIVE
end

MemberManager --> CLI : Status updated
deactivate MemberManager
CLI --> User : Show updated member status

@enduml
```

## Publish an offer

```plantuml
@startuml
title Publish an offer

actor User
boundary CLI
control MemberManager
control EngagementManager

User -> CLI : Select "Publish an offer"
CLI -> User : Ask for member email address
User -> CLI : Enter email address
CLI -> MemberManager : findMemberByEmail(email)
activate MemberManager
MemberManager -> MemberManager : Confirm member is ACTIVE
MemberManager --> CLI : Active member confirmed
deactivate MemberManager

CLI -> EngagementManager : getSkillCategories()
activate EngagementManager
EngagementManager --> CLI : Available skill categories
deactivate EngagementManager
CLI --> User : Show skill categories
User -> CLI : Select skill to offer
CLI -> EngagementManager : publishOffer(member, skill)
activate EngagementManager
EngagementManager -> EngagementManager : Validate selected skill category

alt Valid skill category
    EngagementManager -> EngagementManager : Associate skill offer with member
    EngagementManager --> CLI : Offer published
    CLI --> User : Show success message
else Invalid skill category
    EngagementManager --> CLI : Offer rejected
    CLI --> User : Show invalid-skill error
end

deactivate EngagementManager
@enduml
```

## Raise a request

```plantuml
@startuml
title Raise a request

actor User
boundary CLI
control MemberManager
control EngagementManager

User -> CLI : Select "Raise a request"
CLI -> User : Ask for member email address
User -> CLI : Enter email address
CLI -> MemberManager : findMemberByEmail(email)
activate MemberManager
MemberManager -> MemberManager : Confirm member is ACTIVE
MemberManager --> CLI : Active member confirmed
deactivate MemberManager

CLI -> EngagementManager : getSkillCategories()
activate EngagementManager
EngagementManager --> CLI : Available skill categories
deactivate EngagementManager
CLI --> User : Show skill categories
User -> CLI : Select category and enter start date,\nend date, and time credits
CLI -> EngagementManager : raiseRequest(member, skill, startDate,\nendDate, timeCredits)
activate EngagementManager
EngagementManager -> EngagementManager : Validate category, dates, and time credits

alt Valid category and dates
    EngagementManager -> EngagementManager : Record request for member
    EngagementManager --> CLI : Request raised
    CLI --> User : Show success message
else Invalid category or dates
    EngagementManager --> CLI : Request rejected
    CLI --> User : Show validation error
end

deactivate EngagementManager
@enduml
```


## Propose an engagement

No sequence diagram is needed. Engagement proposals are created automatically inside `EngagementManager`; they do not involve a user action or CLI interaction.

## Confirm a request

```plantuml
@startuml
title Confirm a request

actor User
boundary CLI
control MemberManager
control EngagementManager
control LedgerManager

User -> CLI : Select "Confirm a request"
CLI -> User : Ask for member email address
User -> CLI : Enter email address
CLI -> MemberManager : findMemberByEmail(email)
activate MemberManager
MemberManager -> MemberManager : Confirm member is ACTIVE
MemberManager --> CLI : Active member confirmed
deactivate MemberManager

CLI -> EngagementManager : getEngagementQueue(member)
activate EngagementManager
EngagementManager --> CLI : Matching proposed engagements
deactivate EngagementManager
CLI --> User : Show matches and ask to confirm or reject
User -> CLI : Select engagement and decision
CLI -> EngagementManager : recordDecision(member, engagement, decision)
activate EngagementManager

alt Confirm selected
    EngagementManager -> EngagementManager : Check whether other party already confirmed
    alt Other party has not confirmed
        EngagementManager -> EngagementManager : Record member confirmation
        EngagementManager --> CLI : Confirmation recorded - awaiting other party
        CLI --> User : Show awaiting-confirmation message
    else Other party already confirmed
        EngagementManager -> LedgerManager : Check requestee has sufficient balance\nfor session cost and fee
        activate LedgerManager
        alt Sufficient balance
            LedgerManager -> LedgerManager : Deduct session cost and fee\nfrom requestee balance
            LedgerManager -> LedgerManager : Append transfer entry to ledger
            LedgerManager -> LedgerManager : Add fee to community fund
            LedgerManager --> EngagementManager : Transfer completed
            EngagementManager --> CLI : Session confirmed
            CLI --> User : Show success message
        else Insufficient balance
            LedgerManager --> EngagementManager : Transfer rejected
            EngagementManager --> CLI : Confirmation failed - insufficient balance
            CLI --> User : Show insufficient-balance error
        end
        deactivate LedgerManager
    end
else Reject selected
    EngagementManager -> EngagementManager : Record rejection
    EngagementManager --> CLI : Rejection recorded
    CLI --> User : Show rejection message
end

deactivate EngagementManager
@enduml
```

## Cancel an engagement

**Not complete - specification decision required.** R4 transfers credits when both parties confirm a session, but R14 says that cancelling a confirmed engagement less than 24 hours before its agreed start transfers no credits. At cancellation time, credits may already have transferred. The team must decide whether a refund or compensating ledger entry is permitted before a sequence diagram can be finalised.

## Apply for a community grant

**Not complete - specification decision required.** The brief does not define whether a community grant is approved automatically, by a member, or by another user type. It also does not define how the grant amount is chosen. The team must decide these points before a sequence diagram can be finalised.

## Query balance and history

```plantuml
@startuml
title Query balance and history

actor User
boundary CLI
control MemberManager
control EngagementManager
control LedgerManager

User -> CLI : Select "Query balance and history"
CLI -> User : Ask for member email address
User -> CLI : Enter email address
CLI -> MemberManager : findMemberByEmail(email)
activate MemberManager
MemberManager --> CLI : Member found
deactivate MemberManager
CLI --> User : Show query options:\nbalance, engagements, or ledger history
User -> CLI : Select query option

alt Balance
    CLI -> MemberManager : getBalance(member)
    activate MemberManager
    MemberManager --> CLI : Balance value
    deactivate MemberManager
else Engagements
    CLI -> EngagementManager : getEngagements(member)
    activate EngagementManager
    EngagementManager --> CLI : Read-only engagement history
    deactivate EngagementManager
else Ledger history
    CLI -> LedgerManager : getLedgerEntries(member)
    activate LedgerManager
    LedgerManager --> CLI : Read-only ledger entries
    deactivate LedgerManager
end

CLI --> User : Show requested information
@enduml
```

## Report

```plantuml
@startuml
title Report

actor User
boundary CLI
control LedgerManager

User -> CLI : Select "Report"
CLI -> LedgerManager : getReport()
activate LedgerManager
LedgerManager --> CLI : Total credits in circulation\nand community fund balance
deactivate LedgerManager
CLI --> User : Show report

@enduml
```
