package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.MemberView;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Coordinates atomic member registration and its welcome grant. The exact {@code commonLock}
 * object must also be injected into both owners, and both owners must use it for their live state.
 * An adapter aborts by throwing if the nested callback fails; it must not return a failure result
 * from that callback or publish its candidate state.
 */
public final class RegistrationCoordinator {
    @FunctionalInterface
    public interface MembershipRegistration {
        /**
         * Prepare member state privately. Invoke callback with the prepared member projector,
         * and publish only after it returns successfully. No fallible work may follow that return.
         */
        Result<MemberView> withRegistration(MemberId id, String name, String email,
                Function<LongFunction<MemberView>, Result<MemberView>> callback);
    }

    @FunctionalInterface
    public interface WelcomeGrant {
        /**
         * Prepare grant state privately. Invoke callback with the prepared credit balance,
         * and publish only after it returns successfully. No fallible work may follow that return.
         */
        Result<MemberView> withWelcomeGrant(MemberId id, TransactionId transactionId,
                LocalDate date, LongFunction<Result<MemberView>> callback);
    }

    /** Adapter signal for the only expected registration rejections. */
    public static final class RegistrationRejectedException extends RuntimeException {
        private final FailureCode code;

        public RegistrationRejectedException(FailureCode code, String message) {
            super(message);
            if (code != FailureCode.MALFORMED_INPUT && code != FailureCode.DUPLICATE_EMAIL) {
                throw new IllegalArgumentException("unsupported registration rejection code");
            }
            this.code = code;
        }

        public FailureCode code() {
            return code;
        }
    }

    private final Object commonLock;
    private final SimulatedClock clock;
    private final Supplier<TransactionId> transactionIds;
    private final MembershipRegistration membership;
    private final WelcomeGrant ledger;
    private long nextSequence;
    private boolean registering;

    public RegistrationCoordinator(Object commonLock, SimulatedClock clock,
            Supplier<TransactionId> transactionIds, MembershipRegistration membership,
            WelcomeGrant ledger) {
        this(commonLock, clock, transactionIds, membership, ledger, 100001);
    }

    RegistrationCoordinator(Object commonLock, SimulatedClock clock,
            Supplier<TransactionId> transactionIds, MembershipRegistration membership,
            WelcomeGrant ledger, long initialSequence) {
        this.commonLock = Objects.requireNonNull(commonLock);
        this.clock = Objects.requireNonNull(clock);
        this.transactionIds = Objects.requireNonNull(transactionIds);
        this.membership = Objects.requireNonNull(membership);
        this.ledger = Objects.requireNonNull(ledger);
        if (initialSequence < 100001 || initialSequence > 1000000) {
            throw new IllegalArgumentException("initial sequence outside registration range");
        }
        this.nextSequence = initialSequence;
    }

    public Result<MemberView> register(String name, String email) {
        synchronized (commonLock) {
            if (registering) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED, "reentrant registration is not allowed");
            }
            if (nextSequence > 999999) {
                return Result.failure(FailureCode.LIMIT_EXCEEDED, "member ID sequence exhausted");
            }
            registering = true;
            try {
                MemberId id = new MemberId(Long.toString(nextSequence));
                Result<MemberView> result = membership.withRegistration(id, name, email,
                        buildMemberView -> prepareWelcome(id, buildMemberView));
                if (result == null || result.isFailure()) {
                    return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED,
                            "owner preparation returned an invalid result");
                }
                nextSequence++;
                return result;
            } catch (RegistrationRejectedException rejection) {
                return Result.failure(rejection.code(), rejection.getMessage());
            } catch (RuntimeException failure) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED,
                        "registration preparation failed");
            } finally {
                registering = false;
            }
        }
    }

    private Result<MemberView> prepareWelcome(MemberId memberId, LongFunction<MemberView> buildMemberView) {
        TransactionId transactionId = Objects.requireNonNull(transactionIds.get());
        LocalDate date = Objects.requireNonNull(clock.currentDate());
        Result<MemberView> result = ledger.withWelcomeGrant(memberId, transactionId, date,
                credits -> Result.success(buildMemberView.apply(credits)));
        // Throw before the membership callback returns so a refused ledger operation cannot publish a member.
        if (result == null || result.isFailure()) {
            throw new IllegalStateException("welcome grant preparation rejected");
        }
        return result;
    }
}
