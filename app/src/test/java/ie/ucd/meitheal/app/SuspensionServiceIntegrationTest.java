package ie.ucd.meitheal.app;

import ie.ucd.meitheal.engagement.EngagementManager;
import ie.ucd.meitheal.ledger.AccountReference;
import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.ledger.Transaction;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.membermanager.MemberManagerException;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SuspensionServiceIntegrationTest {
    private static final CategoryId GARDENING = new CategoryId("gardening");
    private static final CategoryId COOKING = new CategoryId("cooking");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 3);

    @Test
    void suspensionInvalidatesOnlyInvolvingPendingProposalsAndReinstatementAllowsNewWork() {
        Fixture f = new Fixture(() -> { });
        Setup s = f.prepare();
        Snapshot before = f.snapshot(s);
        assertEquals(126, f.service.balance(s.target()).value().orElseThrow().held());

        MemberView suspended = f.service.suspendMember(s.target()).value().orElseThrow();
        assertEquals(f.service.findMember(s.target()).value().orElseThrow(), suspended);
        assertEquals(MemberStatus.SUSPENDED, suspended.status());
        assertEquals(f.service.balance(s.target()).value().orElseThrow().ledgerBalance(), suspended.ledgerCredits());
        assertEquals(MemberStatus.SUSPENDED,
                f.service.findMemberByEmail(" TARGET@example.org ").value().orElseThrow().status());
        assertEquals(before.requests(), f.requestViews(s));
        assertEquals(before.engagements(), f.engagementHistories(s));

        assertStatus(f, s, s.otherRequest(), s.targetOutgoing(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.firstIncoming(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.secondIncoming(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.unrelatedRequest(), s.unrelatedPending(), ProposalStatus.PENDING);
        assertStatus(f, s, s.selectedRequest(), s.selectedProposal(), ProposalStatus.SELECTED);
        assertEquals(RequestStatus.OPEN, f.engagements.findRequest(s.openRequest().id()).value().orElseThrow().status());
        assertEquals(RequestStatus.CLOSED, f.engagements.findRequest(s.selectedRequest().id()).value().orElseThrow().status());
        assertEquals(s.agreement(), f.engagements.findEngagement(s.target(), s.agreement().id()).value().orElseThrow());
        assertEquals(before.balances(), f.allBalances(s));
        assertEquals(before.history(), f.ledger.history());
        assertEquals(before.fund(), f.ledger.balance(AccountReference.FUND));

        assertEquals(FailureCode.SUSPENDED_MEMBER, f.service.offerSkill(s.target(), COOKING).failureCode().orElseThrow());
        assertEquals(FailureCode.SUSPENDED_MEMBER, f.service.createRequest(s.target(), GARDENING, 60, DATE, DATE).failureCode().orElseThrow());
        assertEquals(FailureCode.SUSPENDED_MEMBER, f.service.submitProposal(s.target(), s.otherRequest().id(), DATE).failureCode().orElseThrow());
        assertEquals(FailureCode.SUSPENDED_MEMBER, f.service.submitProposal(s.helperOne(), s.openRequest().id(), DATE).failureCode().orElseThrow());

        assertEquals(MemberStatus.ACTIVE, f.service.reinstateMember(s.target()).value().orElseThrow().status());
        assertTrue(f.service.createRequest(s.target(), GARDENING, 60, DATE, DATE).isSuccess());
        assertStatus(f, s, s.otherRequest(), s.targetOutgoing(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.firstIncoming(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.secondIncoming(), ProposalStatus.INVALIDATED);
        assertTrue(f.service.offerSkill(s.target(), COOKING).isSuccess());
        assertTrue(f.service.submitProposal(s.helperOne(), s.openRequest().id(), DATE).isSuccess());
        assertTrue(f.service.submitProposal(s.target(), s.otherRequest().id(), DATE).isSuccess());
        assertStatus(f, s, s.otherRequest(), s.targetOutgoing(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.firstIncoming(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.secondIncoming(), ProposalStatus.INVALIDATED);
    }

    @Test
    void thrownMembershipFailureMapsToAtomicFailureAndPreservesFullSnapshotOnRetry() {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<Snapshot> baseline = new AtomicReference<>();
        AtomicLong calls = new AtomicLong();
        Fixture f = new Fixture(() -> {
            Fixture current = fixture.get();
            assertTrue(Thread.holdsLock(current.lock));
            assertEquals(baseline.get(), current.snapshot(current.currentSetup));
            if (calls.getAndIncrement() == 0) {
                throw new MemberManagerException(MemberManagerException.Code.INVALID_STATE, "injected");
            }
        });
        fixture.set(f);
        Setup s = f.prepare();
        f.currentSetup = s;
        Snapshot before = f.snapshot(s);
        baseline.set(before);
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED, f.service.suspendMember(s.target()).failureCode().orElseThrow());
        assertEquals(before, f.snapshot(s));
        assertStatus(f, s, s.otherRequest(), s.targetOutgoing(), ProposalStatus.PENDING);
        assertEquals(MemberStatus.ACTIVE, f.service.findMember(s.target()).value().orElseThrow().status());

        MemberView suspended = f.service.suspendMember(s.target()).value().orElseThrow();
        assertEquals(f.service.findMember(s.target()).value().orElseThrow(), suspended);
        assertEquals(MemberStatus.SUSPENDED, suspended.status());
        assertEquals(2, calls.get());
        assertStatus(f, s, s.otherRequest(), s.targetOutgoing(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.firstIncoming(), ProposalStatus.INVALIDATED);
        assertStatus(f, s, s.openRequest(), s.secondIncoming(), ProposalStatus.INVALIDATED);
    }

    @Test
    void suspendedHelperCanConfirmOnScheduledDateAndReleaseHold() {
        Fixture f = new Fixture(() -> { });
        Setup s = f.prepare();
        assertEquals(126, f.service.balance(s.target()).value().orElseThrow().held());
        assertTrue(f.service.suspendMember(s.helperOne()).isSuccess());
        assertTrue(f.clock.advanceTo(s.agreement().scheduledDate()).isSuccess());
        assertTrue(f.service.confirmEngagement(s.target(), s.agreement().id()).isSuccess());
        assertEquals(EngagementStatus.COMPLETED,
                f.service.confirmEngagement(s.helperOne(), s.agreement().id()).value().orElseThrow().status());
        assertEquals(EngagementStatus.COMPLETED,
                f.engagements.findEngagement(s.target(), s.agreement().id()).value().orElseThrow().status());
        assertEquals(0, f.service.balance(s.target()).value().orElseThrow().held());
        assertEquals(-6, f.service.balance(s.target()).value().orElseThrow().ledgerBalance());
        assertEquals(240, f.service.balance(s.helperOne()).value().orElseThrow().ledgerBalance());
        assertEquals(6, f.ledger.balance(AccountReference.FUND));
    }

    @Test
    void suspendedRequesterCanCancelBeforeScheduledDateAndReleaseHoldWithoutHistoryChange() {
        Fixture f = new Fixture(() -> { }, LocalDate.of(2026, 9, 5));
        Setup s = f.prepare(LocalDate.of(2026, 9, 7));
        List<Transaction> history = f.ledger.history();
        assertEquals(126, f.service.balance(s.target()).value().orElseThrow().held());
        assertTrue(f.service.suspendMember(s.target()).isSuccess());
        EngagementView cancelled = f.service.cancelEngagement(s.target(), s.agreement().id()).value().orElseThrow();
        assertEquals(EngagementStatus.CANCELLED, cancelled.status());
        assertEquals(s.target(), cancelled.cancellationActor());
        assertEquals(LocalDate.of(2026, 9, 5), cancelled.cancellationDate());
        assertFalse(cancelled.lateCancellation());
        assertEquals(0, f.service.balance(s.target()).value().orElseThrow().held());
        assertEquals(history, f.ledger.history());
    }

    @Test
    void suspensionAndReinstatementReportMissingAndInvalidStates() {
        Fixture f = new Fixture(() -> { });
        Setup s = f.prepare();
        MemberId unknown = new MemberId("unknown-member");
        assertEquals(FailureCode.MEMBER_NOT_FOUND, f.service.suspendMember(unknown).failureCode().orElseThrow());
        assertTrue(f.service.suspendMember(s.target()).isSuccess());
        assertEquals(FailureCode.INVALID_STATE, f.service.suspendMember(s.target()).failureCode().orElseThrow());
        assertTrue(f.service.reinstateMember(s.target()).isSuccess());
        assertEquals(FailureCode.INVALID_STATE, f.service.reinstateMember(s.target()).failureCode().orElseThrow());
        assertEquals(FailureCode.MEMBER_NOT_FOUND, f.service.reinstateMember(unknown).failureCode().orElseThrow());
    }

    private record Setup(MemberId target, MemberId helperOne, MemberId helperTwo, MemberId other,
            MemberId unrelatedOwner, MemberId otherOwner, RequestView openRequest, RequestView selectedRequest,
            RequestView otherRequest, RequestView unrelatedRequest, ProposalView targetOutgoing,
            ProposalView firstIncoming, ProposalView secondIncoming, ProposalView unrelatedPending,
            ProposalView selectedProposal, EngagementView agreement) { }

    private record Snapshot(List<MemberView> members, List<RequestView> requests, List<ProposalView> proposals,
            List<EngagementView> engagements, List<BalanceView> balances, List<Transaction> history, long fund) { }

    private static final class Fixture {
        final Object lock = new Object();
        final CategoryCatalog catalogue = StartupCatalogue.load(InMemoryRegistrationService.class.getResourceAsStream("/categories.csv"));
        final MemberManager members = new MemberManager(lock, catalogue.list().stream()
                .map(row -> new ie.ucd.meitheal.membermanager.CategoryId(row.id().value())).toList());
        final SimulatedClock clock;
        final AtomicLong sequence = new AtomicLong();
        final Supplier<TransactionId> tx = () -> new TransactionId("suspension-" + sequence.incrementAndGet());
        final LedgerManager ledger;
        final InMemoryRegistrationService service;
        final EngagementManager engagements;
        Setup currentSetup;

        Fixture(Runnable hook) { this(hook, LocalDate.of(2026, 9, 1)); }
        Fixture(Runnable hook, LocalDate start) {
            clock = new InMemorySimulatedClock(start);
            ledger = new LedgerManager(lock, tx, clock::currentDate);
            service = new InMemoryRegistrationService(lock, members, ledger, clock, tx, catalogue,
                    () -> { }, ignored -> { }, ignored -> { }, ignored -> { }, hook);
            engagements = service.engagementManagerForTests();
        }

        MemberId register(String name, String email) { return service.register(name, email).value().orElseThrow().id(); }
        RequestView request(MemberId owner, LocalDate date) { return service.createRequest(owner, GARDENING, 120, date, date).value().orElseThrow(); }
        ProposalView propose(MemberId helper, RequestView request) { return service.submitProposal(helper, request.id(), request.windowStart()).value().orElseThrow(); }

        Setup prepare() { return prepare(DATE); }
        Setup prepare(LocalDate workDate) {
            MemberId target = register("Target", "target@example.org");
            MemberId helperOne = register("Helper One", "helper-one@example.org");
            MemberId helperTwo = register("Helper Two", "helper-two@example.org");
            MemberId other = register("Other", "other@example.org");
            MemberId unrelatedOwner = register("Unrelated Owner", "unrelated-owner@example.org");
            MemberId otherOwner = register("Other Owner", "other-owner@example.org");
            for (MemberId member : List.of(target, helperOne, helperTwo, other)) service.offerSkill(member, GARDENING).value().orElseThrow();
            RequestView open = request(target, workDate);
            RequestView selected = request(target, workDate);
            RequestView otherRequest = request(otherOwner, workDate);
            RequestView unrelatedRequest = request(unrelatedOwner, workDate);
            ProposalView outgoing = propose(target, otherRequest);
            ProposalView first = propose(helperOne, open);
            ProposalView second = propose(helperTwo, open);
            ProposalView unrelated = propose(helperOne, unrelatedRequest);
            ProposalView selectedProposal = propose(helperOne, selected);
            EngagementView agreement = service.selectProposal(target, selectedProposal.id()).value().orElseThrow();
            return new Setup(target, helperOne, helperTwo, other, unrelatedOwner, otherOwner, open, selected,
                    otherRequest, unrelatedRequest, outgoing, first, second, unrelated, selectedProposal, agreement);
        }

        List<ProposalView> currentProposals(Setup s, RequestView request) {
            return service.proposalsForRequest(request.requesterId(), request.id()).value().orElseThrow();
        }
        List<MemberId> memberIds(Setup s) { return List.of(s.target(), s.helperOne(), s.helperTwo(), s.other(), s.unrelatedOwner(), s.otherOwner()); }
        List<MemberView> allMembers(Setup s) { return memberIds(s).stream().map(id -> service.findMember(id).value().orElseThrow()).toList(); }
        List<BalanceView> allBalances(Setup s) { return memberIds(s).stream().map(id -> service.balance(id).value().orElseThrow()).toList(); }
        List<RequestView> requestViews(Setup s) {
            return List.of(s.openRequest(), s.selectedRequest(), s.otherRequest(), s.unrelatedRequest()).stream()
                    .map(request -> engagements.findRequest(request.id()).value().orElseThrow()).toList();
        }
        List<EngagementView> engagementHistories(Setup s) {
            return memberIds(s).stream().flatMap(id -> engagements.engagementsForMember(id).value().orElseThrow().stream())
                    .distinct().toList();
        }
        Snapshot snapshot(Setup s) {
            List<ProposalView> proposals = List.of(currentProposals(s, s.openRequest()), currentProposals(s, s.selectedRequest()),
                    currentProposals(s, s.otherRequest()), currentProposals(s, s.unrelatedRequest())).stream().flatMap(List::stream).toList();
            return new Snapshot(allMembers(s), requestViews(s), proposals, engagementHistories(s), allBalances(s),
                    ledger.history(), ledger.balance(AccountReference.FUND));
        }
    }

    private static void assertStatus(Fixture f, Setup s, RequestView request, ProposalView expected, ProposalStatus status) {
        assertEquals(status, f.currentProposals(s, request).stream()
                .filter(proposal -> proposal.id().equals(expected.id())).findFirst().orElseThrow().status());
    }
}
