package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Coordinates engagement confirmation and its second-confirmation settlement. */
final class ConfirmationCoordinator {
    @FunctionalInterface
    interface EngagementConfirmation {
        Result<EngagementView> withConfirmation(MemberId participantId, EngagementId engagementId,
                LocalDate today, Function<EngagementView, Result<EngagementView>> settleBeforePublish);
    }

    @FunctionalInterface
    interface LedgerSettlement {
        Result<EngagementView> withSettlement(EngagementId engagementId, LocalDate date,
                Supplier<TransactionId> freshId, Supplier<Result<EngagementView>> beforePublish);
    }

    private final Object commonLock;
    private final SimulatedClock clock;
    private final Supplier<TransactionId> transactionIds;
    private final EngagementConfirmation engagements;
    private final LedgerSettlement ledger;
    private final Consumer<EngagementView> beforePublication;
    private boolean confirming;

    ConfirmationCoordinator(Object commonLock, SimulatedClock clock,
            Supplier<TransactionId> transactionIds, EngagementConfirmation engagements,
            LedgerSettlement ledger, Consumer<EngagementView> beforePublication) {
        this.commonLock = Objects.requireNonNull(commonLock);
        this.clock = Objects.requireNonNull(clock);
        this.transactionIds = Objects.requireNonNull(transactionIds);
        this.engagements = Objects.requireNonNull(engagements);
        this.ledger = Objects.requireNonNull(ledger);
        this.beforePublication = Objects.requireNonNull(beforePublication);
    }

    Result<EngagementView> confirm(MemberId participantId, EngagementId engagementId) {
        synchronized (commonLock) {
            if (confirming) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED,
                        "reentrant confirmation is not allowed");
            }
            confirming = true;
            try {
                LocalDate today = clock.currentDate();
                return engagements.withConfirmation(participantId, engagementId, today,
                        candidate -> ledger.withSettlement(candidate.id(), today, transactionIds,
                                () -> {
                                    Result<EngagementView> success = Result.success(candidate);
                                    beforePublication.accept(candidate);
                                    return success;
                                }));
            } catch (RuntimeException failure) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED,
                        "confirmation preparation failed");
            } finally {
                confirming = false;
            }
        }
    }
}
