package ie.ucd.meitheal.app;

import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.membermanager.Member;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.membermanager.MemberManagerException;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.MemberView;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** In-memory implementation of registration backed by the membership and ledger owners. */
public final class InMemoryRegistrationService implements RegistrationService {
    private final Object lock;
    private final MemberManager members;
    private final LedgerManager ledger;
    private final RegistrationCoordinator coordinator;

    public static InMemoryRegistrationService create() {
        Object lock = new Object();
        var ids = new AtomicLong(1);
        Supplier<TransactionId> generator = () -> new TransactionId("tx-" + ids.getAndIncrement());
        MemberManager members = new MemberManager(lock, List.of());
        SimulatedClock clock = new InMemorySimulatedClock(LocalDate.of(2026, 9, 1));
        LedgerManager ledger = new LedgerManager(lock, generator, clock::currentDate);
        return new InMemoryRegistrationService(lock, members, ledger, clock, generator, () -> { });
    }

    InMemoryRegistrationService(Object lock, MemberManager members, LedgerManager ledger,
            SimulatedClock clock, Supplier<TransactionId> transactionIds, Runnable beforePublication) {
        this.lock = Objects.requireNonNull(lock, "lock");
        this.members = Objects.requireNonNull(members, "members");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(beforePublication, "beforePublication");
        RegistrationCoordinator.MembershipRegistration membership = (id, name, email, callback) -> {
            try {
                return members.withRegistration(id, name, email, member ->
                        callback.apply(credits -> toView(member, credits)));
            } catch (MemberManagerException failure) {
                if (failure.code() == MemberManagerException.Code.DUPLICATE_EMAIL) {
                    throw new RegistrationCoordinator.RegistrationRejectedException(
                            FailureCode.DUPLICATE_EMAIL, failure.getMessage());
                }
                if (failure.code() == MemberManagerException.Code.INVALID_NAME
                        || failure.code() == MemberManagerException.Code.INVALID_EMAIL) {
                    throw new RegistrationCoordinator.RegistrationRejectedException(
                            FailureCode.MALFORMED_INPUT, failure.getMessage());
                }
                throw failure;
            }
        };
        RegistrationCoordinator.WelcomeGrant welcomeGrant = (id, transactionId, date, callback) ->
                ledger.withWelcomeGrant(id, transactionId, date, transaction -> {
                    Result<MemberView> candidate = callback.apply(transaction.amount());
                    beforePublication.run();
                    return candidate;
                });
        this.coordinator = new RegistrationCoordinator(this.lock, clock,
                Objects.requireNonNull(transactionIds, "transactionIds"), membership, welcomeGrant);
    }

    @Override
    public Result<MemberView> register(String name, String email) {
        return coordinator.register(name, email);
    }

    @Override
    public Result<MemberView> findMember(MemberId memberId) {
        synchronized (lock) {
            if (memberId == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member ID must not be null");
            }
            return members.findById(memberId).map(member -> Result.success(toView(member,
                    ledger.balanceOf(memberId)))).orElseGet(() -> Result.failure(
                    FailureCode.MEMBER_NOT_FOUND, "Member does not exist"));
        }
    }

    @Override
    public Result<MemberView> findMemberByEmail(String email) {
        synchronized (lock) {
            try {
                return members.findByEmail(email).map(member -> Result.success(toView(member,
                        ledger.balanceOf(member.id())))).orElseGet(() -> Result.failure(
                        FailureCode.MEMBER_NOT_FOUND, "Member does not exist"));
            } catch (MemberManagerException failure) {
                if (failure.code() == MemberManagerException.Code.INVALID_EMAIL) {
                    return Result.failure(FailureCode.MALFORMED_INPUT, failure.getMessage());
                }
                throw failure;
            }
        }
    }

    @Override
    public Result<BalanceView> balance(MemberId memberId) {
        synchronized (lock) {
            if (memberId == null || members.findById(memberId).isEmpty()) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member does not exist");
            }
            long value = ledger.balanceOf(memberId);
            return Result.success(new BalanceView(memberId, value, 0, value));
        }
    }

    private static MemberView toView(Member member, long ledgerCredits) {
        var skills = member.offeredCategories().stream()
                .map(category -> new CategoryId(category.value()))
                .collect(Collectors.toSet());
        return new MemberView(member.id(), member.name(), member.email().value(),
                MemberStatus.valueOf(member.status().name()),
                skills, ledgerCredits);
    }
}
