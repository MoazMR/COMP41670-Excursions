package ie.ucd.meitheal.app;

import ie.ucd.meitheal.engagement.EngagementManager;
import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates the engagement and ledger publication boundaries for one selection. */
final class SelectionCoordinator {
    private final Object commonLock;
    private final EngagementManager engagements;
    private final LedgerManager ledger;
    private final Consumer<EngagementView> beforePublication;
    private long nextEngagement = 1;
    private boolean selecting;

    SelectionCoordinator(Object commonLock, EngagementManager engagements, LedgerManager ledger,
            Consumer<EngagementView> beforePublication) {
        this.commonLock = Objects.requireNonNull(commonLock);
        this.engagements = Objects.requireNonNull(engagements);
        this.ledger = Objects.requireNonNull(ledger);
        this.beforePublication = Objects.requireNonNull(beforePublication);
    }

    Result<EngagementView> select(MemberId requesterId, ProposalId proposalId) {
        synchronized (commonLock) {
            if (selecting) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED, "reentrant selection is not allowed");
            }
            selecting = true;
            try {
                EngagementId candidateId = new EngagementId("eng-" + nextEngagement);
                Result<EngagementView> result = engagements.withSelection(requesterId, proposalId, candidateId,
                        candidate -> ledger.withHold(candidate.id(), candidate.requesterId(), candidate.helperId(),
                                candidate.durationMinutes(), balance -> {
                                    Result<EngagementView> success = Result.success(candidate);
                                    beforePublication.accept(candidate);
                                    return success;
                                }));
                if (result.isSuccess()) nextEngagement++;
                return result;
            } catch (RuntimeException failure) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED, "selection preparation failed");
            } finally {
                selecting = false;
            }
        }
    }
}
