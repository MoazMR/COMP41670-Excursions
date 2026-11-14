package ie.ucd.meitheal.app;

import ie.ucd.meitheal.engagement.EngagementManager;
import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.result.FailureCode;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionServiceIntegrationTest {
    private static final CategoryId GARDENING = new CategoryId("gardening");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);

    @Test
    void successfulSelectionPublishesAgreementAndHoldButNoLedgerTransaction() {
        Fixture f = new Fixture(286, ignored -> { });
        var setup = f.prepare();
        var transactionHistory = f.ledger.history();
        var requesterHistory = f.ledger.history(setup.requester());

        var selected = f.service.selectProposal(setup.requester(), setup.first().id());
        assertTrue(selected.isSuccess());
        var engagement = selected.value().orElseThrow();
        assertEquals(EngagementStatus.AGREED, engagement.status());
        assertEquals(RequestStatus.CLOSED, f.engagements.findRequest(setup.request().id()).value().orElseThrow().status());
        var proposals = f.service.proposalsForRequest(setup.requester(), setup.request().id()).value().orElseThrow();
        assertEquals(ProposalStatus.SELECTED, f.proposal(proposals, setup.first().id()).status());
        assertEquals(ProposalStatus.REJECTED, f.proposal(proposals, setup.second().id()).status());
        assertEquals(java.util.List.of(engagement), f.engagements.engagementsForMember(setup.requester()).value().orElseThrow());
        assertEquals(java.util.List.of(engagement), f.engagements.engagementsForMember(setup.helper()).value().orElseThrow());
        assertEquals(new BalanceView(setup.requester(), 120, 300, -180),
                f.service.balance(setup.requester()).value().orElseThrow());
        assertEquals(new BalanceView(setup.helper(), 120, 0, 120), f.service.balance(setup.helper()).value().orElseThrow());
        assertEquals(transactionHistory, f.ledger.history());
        assertEquals(requesterHistory, f.ledger.history(setup.requester()));
    }

    @Test
    void failuresPreserveStateAndRetryPostsExactlyOnce() {
        var calls = new AtomicLong();
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<Setup> fixtureSetup = new AtomicReference<>();
        AtomicReference<Snapshot> fixtureBefore = new AtomicReference<>();
        AtomicReference<EngagementId> failedCandidate = new AtomicReference<>();
        Object commonLock = new Object();
        Fixture f = new Fixture(commonLock, 45, candidate -> {
            assertTrue(Thread.holdsLock(commonLock));
            assertEquals(fixtureBefore.get(), fixture.get().snapshot(fixtureSetup.get()));
            if (calls.getAndIncrement() == 0) {
                failedCandidate.set(candidate.id());
                throw new IllegalStateException("injected");
            }
        });
        fixture.set(f);
        var setup = f.prepare();
        fixtureSetup.set(setup);
        var before = f.snapshot(setup);
        fixtureBefore.set(before);
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                f.service.selectProposal(setup.requester(), setup.first().id()).failureCode().orElseThrow());
        f.assertSnapshot(setup, before);

        var successResult = f.service.selectProposal(setup.requester(), setup.first().id());
        assertTrue(successResult.isSuccess());
        var success = successResult.value().orElseThrow();
        assertEquals(failedCandidate.get(), success.id());
        assertEquals(List.of(success), f.engagements.engagementsForMember(setup.requester()).value().orElseThrow());
        assertEquals(List.of(success), f.engagements.engagementsForMember(setup.helper()).value().orElseThrow());
        var after = f.snapshot(setup);
        assertEquals(47, after.requesterBalance().held());
        assertEquals(before.requesterLedger(), after.requesterLedger());
        assertEquals(before.ledger(), after.ledger());
        assertEquals(0, after.helperBalance().held());
        assertEquals(ProposalStatus.SELECTED, f.proposal(after.proposals(), setup.first().id()).status());
        assertEquals(ProposalStatus.REJECTED, f.proposal(after.proposals(), setup.second().id()).status());
        assertEquals(FailureCode.INVALID_STATE,
                f.service.selectProposal(setup.requester(), setup.first().id()).failureCode().orElseThrow());
        assertEquals(after, f.snapshot(setup));
    }

    @Test
    void unauthorizedExistingMemberCannotSelectAndPreservesState() {
        Fixture f = new Fixture(45, ignored -> { });
        var setup = f.prepare();
        MemberId outsider = f.service.register("Lee", "lee@example.org").value().orElseThrow().id();
        var before = f.snapshot(setup);
        assertEquals(FailureCode.NOT_PARTICIPANT,
                f.service.selectProposal(outsider, setup.first().id()).failureCode().orElseThrow());
        f.assertSnapshot(setup, before);
    }

    @Test
    void participantSuspensionAndHoldFloorRejectWithoutPublishing() {
        Fixture floor = new Fixture(287, ignored -> { });
        var high = floor.prepare();
        var highBefore = floor.snapshot(high);
        assertEquals(FailureCode.INSUFFICIENT_AVAILABLE_CREDITS,
                floor.service.selectProposal(high.requester(), high.first().id()).failureCode().orElseThrow());
        floor.assertSnapshot(high, highBefore);

        for (boolean suspendRequester : new boolean[] {true, false}) {
            Fixture f = new Fixture(45, ignored -> { });
            var setup = f.prepare();
            MemberId suspended = suspendRequester ? setup.requester() : setup.helper();
            f.members.suspend(suspended);
            var before = f.snapshot(setup);
            assertEquals(FailureCode.SUSPENDED_MEMBER,
                    f.service.selectProposal(setup.requester(), setup.first().id()).failureCode().orElseThrow());
            f.assertSnapshot(setup, before);
        }
    }

    private static final class Fixture {
        final Object lock;
        final CategoryCatalog catalogue = StartupCatalogue.load(
                InMemoryRegistrationService.class.getResourceAsStream("/categories.csv"));
        final MemberManager members;
        final SimulatedClock clock = new InMemorySimulatedClock(LocalDate.of(2026, 9, 1));
        final AtomicLong transactionSequence = new AtomicLong();
        final Supplier<TransactionId> transactionIds = () -> new TransactionId("tx-test-" + transactionSequence.incrementAndGet());
        final LedgerManager ledger;
        final SelectionService service;
        final InMemoryRegistrationService registration;
        final EngagementManager engagements;
        final int duration;

        Fixture(int duration, java.util.function.Consumer<EngagementView> hook) {
            this(new Object(), duration, hook);
        }

        Fixture(Object lock, int duration, java.util.function.Consumer<EngagementView> hook) {
            this.lock = lock;
            this.duration = duration;
            members = new MemberManager(lock, catalogue.list().stream()
                    .map(category -> new ie.ucd.meitheal.membermanager.CategoryId(category.id().value())).toList());
            ledger = new LedgerManager(lock, transactionIds, clock::currentDate);
            registration = new InMemoryRegistrationService(lock, members, ledger, clock, transactionIds,
                    catalogue, () -> { }, hook);
            service = registration;
            engagements = registration.engagementManagerForTests();
        }

        Setup prepare() {
            MemberId requester = service.register("Pat", "pat@example.org").value().orElseThrow().id();
            MemberId helper = service.register("Mae", "mae@example.org").value().orElseThrow().id();
            MemberId competitor = service.register("Jo", "jo@example.org").value().orElseThrow().id();
            service.offerSkill(helper, GARDENING).value().orElseThrow();
            service.offerSkill(competitor, GARDENING).value().orElseThrow();
            var request = service.createRequest(requester, GARDENING, duration, DATE, DATE).value().orElseThrow();
            var first = service.submitProposal(helper, request.id(), DATE).value().orElseThrow();
            var second = service.submitProposal(competitor, request.id(), DATE).value().orElseThrow();
            return new Setup(requester, helper, request, first, second);
        }

        Snapshot snapshot(Setup s) {
            return new Snapshot(service.balance(s.requester()).value().orElseThrow(),
                    service.balance(s.helper()).value().orElseThrow(), ledger.history(),
                    ledger.history(s.requester()), ledger.history(s.helper()),
                    engagements.findRequest(s.request().id()).value().orElseThrow(),
                    service.proposalsForRequest(s.requester(), s.request().id()).value().orElseThrow(),
                    engagements.engagementsForMember(s.requester()).value().orElseThrow(),
                    engagements.engagementsForMember(s.helper()).value().orElseThrow());
        }
        void assertSnapshot(Setup s, Snapshot expected) { assertEquals(expected, snapshot(s)); }

        ProposalView proposal(List<ProposalView> proposals, ProposalId id) {
            return proposals.stream().filter(proposal -> proposal.id().equals(id)).findFirst().orElseThrow();
        }

    }

    private record Setup(MemberId requester, MemberId helper,
            RequestView request, ProposalView first, ProposalView second) { }
    private record Snapshot(BalanceView requesterBalance, BalanceView helperBalance,
            List<ie.ucd.meitheal.ledger.Transaction> ledger,
            List<ie.ucd.meitheal.ledger.Transaction> requesterLedger,
            List<ie.ucd.meitheal.ledger.Transaction> helperLedger,
            RequestView request, List<ProposalView> proposals,
            List<EngagementView> requesterManagerEngagements, List<EngagementView> helperManagerEngagements) { }
}
