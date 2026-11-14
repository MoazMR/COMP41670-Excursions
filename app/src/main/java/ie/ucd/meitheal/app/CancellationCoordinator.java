package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates engagement cancellation and release of its ledger hold. */
final class CancellationCoordinator {
    @FunctionalInterface
    interface EngagementCancellation {
        Result<EngagementView> withCancellation(MemberId participantId, EngagementId engagementId,
                LocalDate today, Function<EngagementView, Result<EngagementView>> beforePublish);
    }

    @FunctionalInterface
    interface LedgerRelease {
        <T> Result<T> withRelease(EngagementId engagementId, Supplier<Result<T>> beforePublish);
    }

    private final Object commonLock;
    private final SimulatedClock clock;
    private final EngagementCancellation engagements;
    private final LedgerRelease ledger;
    private final Consumer<EngagementView> beforePublication;
    private boolean cancelling;

    CancellationCoordinator(Object commonLock, SimulatedClock clock,
            EngagementCancellation engagements, LedgerRelease ledger,
            Consumer<EngagementView> beforePublication) {
        this.commonLock = Objects.requireNonNull(commonLock);
        this.clock = Objects.requireNonNull(clock);
        this.engagements = Objects.requireNonNull(engagements);
        this.ledger = Objects.requireNonNull(ledger);
        this.beforePublication = Objects.requireNonNull(beforePublication);
    }

    Result<EngagementView> cancel(MemberId participantId, EngagementId engagementId) {
        synchronized (commonLock) {
            if (cancelling) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED,
                        "reentrant cancellation is not allowed");
            }
            cancelling = true;
            try {
                LocalDate today = clock.currentDate();
                return engagements.withCancellation(participantId, engagementId, today,
                        candidate -> ledger.withRelease(candidate.id(), () -> {
                            Result<EngagementView> success = Result.success(candidate);
                            beforePublication.accept(candidate);
                            return success;
                        }));
            } catch (RuntimeException failure) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED,
                        "cancellation preparation failed");
            } finally {
                cancelling = false;
            }
        }
    }
}
