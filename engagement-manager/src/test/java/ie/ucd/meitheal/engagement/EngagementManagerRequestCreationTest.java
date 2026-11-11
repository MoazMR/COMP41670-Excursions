package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.engagement.port.CategoryId;
import ie.ucd.meitheal.engagement.port.CategoryReadAccess;
import ie.ucd.meitheal.engagement.port.MemberId;
import ie.ucd.meitheal.engagement.port.MemberReadAccess;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementManagerRequestCreationTest {

    @Test
    void createsRequestAndSupportsRetrieval() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestId requestId = new RequestId(UUID.randomUUID());
        RequestDuration duration = new RequestDuration(45);
        RequestWindow window = new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));

        RequestCreationResult result = manager.createRequest(requestId, requester, categoryId, duration, window);

        RequestCreationResult.Success success = assertInstanceOf(RequestCreationResult.Success.class, result);
        assertEquals(RequestStatus.OPEN, success.request().status());
        assertEquals(Optional.of(success.request()), manager.findRequestById(requestId));
        assertEquals(List.of(success.request()), manager.getRequests());
    }

    @Test
    void rawInputOverloadAcceptsInclusiveDurationAndSameDayWindowBoundaries() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );
        LocalDate day = LocalDate.of(2026, 2, 1);

        RequestCreationResult minimum = manager.createRequest(
            new RequestId(UUID.randomUUID()), requester, categoryId, 1, day, day
        );
        RequestCreationResult maximum = manager.createRequest(
            new RequestId(UUID.randomUUID()), requester, categoryId, 480, day, day
        );

        assertEquals(new RequestDuration(1),
            assertInstanceOf(RequestCreationResult.Success.class, minimum).request().duration());
        assertEquals(new RequestDuration(480),
            assertInstanceOf(RequestCreationResult.Success.class, maximum).request().duration());
        assertEquals(new RequestWindow(day, day),
            assertInstanceOf(RequestCreationResult.Success.class, minimum).request().window());
    }

    @Test
    void malformedRawInputIsRejectedWithoutChangingStoredRequests() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );
        RequestCreationResult existing = manager.createRequest(
            new RequestId(UUID.randomUUID()), requester, categoryId, 30,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)
        );
        List<Request> before = manager.getRequests();
        LocalDate day = LocalDate.of(2026, 2, 1);

        List<RequestCreationResult> rejected = List.of(
            manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, 0, day, day),
            manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, 481, day, day),
            manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, 30, null, day),
            manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, 30, day, null),
            manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, 30, day.plusDays(1), day)
        );

        for (RequestCreationResult result : rejected) {
            assertEquals(RequestCreationError.MALFORMED_INPUT,
                assertInstanceOf(RequestCreationResult.Failure.class, result).code());
        }
        assertInstanceOf(RequestCreationResult.Success.class, existing);
        assertEquals(before, manager.getRequests());
    }

    @Test
    void duplicateIdWithValidRawInputDoesNotOverwriteStoredRequest() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );
        RequestId requestId = new RequestId(UUID.randomUUID());
        LocalDate firstDay = LocalDate.of(2026, 2, 1);
        Request original = assertInstanceOf(RequestCreationResult.Success.class,
            manager.createRequest(requestId, requester, categoryId, 30, firstDay, firstDay)).request();
        List<Request> before = manager.getRequests();

        RequestCreationResult duplicate = manager.createRequest(
            requestId, requester, categoryId, 60, firstDay.plusDays(1), firstDay.plusDays(2)
        );

        assertEquals(RequestCreationError.INVALID_STATE,
            assertInstanceOf(RequestCreationResult.Failure.class, duplicate).code());
        assertEquals(before, manager.getRequests());
        assertEquals(original, manager.findRequestById(requestId).orElseThrow());
    }

    @Test
    void rejectsUnknownRequester() {
        MemberId unknownRequester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of()),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestCreationResult result = manager.createRequest(
            new RequestId(UUID.randomUUID()),
            unknownRequester,
            categoryId,
            new RequestDuration(45),
            new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))
        );

        RequestCreationResult.Failure failure = assertInstanceOf(RequestCreationResult.Failure.class, result);
        assertEquals(RequestCreationError.NOT_FOUND, failure.code());
        assertTrue(manager.getRequests().isEmpty());
    }

    @Test
    void rejectsSuspendedRequester() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, false)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestCreationResult result = manager.createRequest(
            new RequestId(UUID.randomUUID()),
            requester,
            categoryId,
            new RequestDuration(45),
            new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))
        );

        RequestCreationResult.Failure failure = assertInstanceOf(RequestCreationResult.Failure.class, result);
        assertEquals(RequestCreationError.SUSPENDED_MEMBER, failure.code());
        assertTrue(manager.getRequests().isEmpty());
    }

    @Test
    void rejectsUnknownCategory() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId knownCategory = new CategoryId("gardening");
        CategoryId unknownCategory = new CategoryId("painting");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(knownCategory))
        );

        RequestCreationResult result = manager.createRequest(
            new RequestId(UUID.randomUUID()),
            requester,
            unknownCategory,
            new RequestDuration(45),
            new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))
        );

        RequestCreationResult.Failure failure = assertInstanceOf(RequestCreationResult.Failure.class, result);
        assertEquals(RequestCreationError.INVALID_CATEGORY, failure.code());
        assertTrue(manager.getRequests().isEmpty());
    }

    @Test
    void duplicateRequestIdDoesNotOverwriteOriginal() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestId requestId = new RequestId(UUID.randomUUID());
        RequestWindow firstWindow = new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));
        RequestWindow secondWindow = new RequestWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));

        RequestCreationResult first = manager.createRequest(
            requestId,
            requester,
            categoryId,
            new RequestDuration(45),
            firstWindow
        );
        Request original = assertInstanceOf(RequestCreationResult.Success.class, first).request();

        RequestCreationResult second = manager.createRequest(
            requestId,
            requester,
            categoryId,
            new RequestDuration(60),
            secondWindow
        );

        RequestCreationResult.Failure failure = assertInstanceOf(RequestCreationResult.Failure.class, second);
        assertEquals(RequestCreationError.INVALID_STATE, failure.code());

        Request stored = manager.findRequestById(requestId).orElseThrow();
        assertEquals(original, stored);
        assertEquals(new RequestDuration(45), stored.duration());
        assertEquals(firstWindow, stored.window());
    }

    @Test
    void businessRejectionLeavesSnapshotUnchanged() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestId initialId = new RequestId(UUID.randomUUID());
        RequestCreationResult created = manager.createRequest(
            initialId,
            requester,
            categoryId,
            new RequestDuration(45),
            new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))
        );
        Request initial = assertInstanceOf(RequestCreationResult.Success.class, created).request();
        List<Request> before = manager.getRequests();

        RequestCreationResult rejected = manager.createRequest(
            new RequestId(UUID.randomUUID()),
            requester,
            new CategoryId("unknown"),
            new RequestDuration(60),
            new RequestWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10))
        );

        assertInstanceOf(RequestCreationResult.Failure.class, rejected);
        assertEquals(before, manager.getRequests());
        assertEquals(List.of(initial), manager.getRequests());
    }

    @Test
    void returnedRequestListIsImmutable() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestCreationResult created = manager.createRequest(
            new RequestId(UUID.randomUUID()),
            requester,
            categoryId,
            new RequestDuration(45),
            new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))
        );
        Request request = assertInstanceOf(RequestCreationResult.Success.class, created).request();

        List<Request> requests = manager.getRequests();

        assertThrows(UnsupportedOperationException.class, () -> requests.add(request));
    }

    @Test
    void previouslyReturnedListStaysUnchangedAfterLaterCreation() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestCreationResult firstResult = manager.createRequest(
            new RequestId(UUID.randomUUID()),
            requester,
            categoryId,
            new RequestDuration(45),
            new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5))
        );
        Request firstRequest = assertInstanceOf(RequestCreationResult.Success.class, firstResult).request();
        List<Request> snapshot = manager.getRequests();

        manager.createRequest(
            new RequestId(UUID.randomUUID()),
            requester,
            categoryId,
            new RequestDuration(60),
            new RequestWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5))
        );

        assertEquals(List.of(firstRequest), snapshot);
        assertEquals(2, manager.getRequests().size());
    }

    @Test
    void missingRequestLookupReturnsEmpty() {
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of()),
            new FakeCategoryReadAccess(Set.of())
        );

        assertFalse(manager.findRequestById(new RequestId(UUID.randomUUID())).isPresent());
    }

    @Test
    void nullArgumentsAreProgrammerErrors() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
            new FakeMemberReadAccess(Map.of(requester, true)),
            new FakeCategoryReadAccess(Set.of(categoryId))
        );

        RequestDuration duration = new RequestDuration(45);
        RequestWindow window = new RequestWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));

        assertThrows(NullPointerException.class, () -> manager.createRequest(null, requester, categoryId, duration, window));
        assertThrows(NullPointerException.class, () -> manager.createRequest(new RequestId(UUID.randomUUID()), null, categoryId, duration, window));
        assertThrows(NullPointerException.class, () -> manager.createRequest(new RequestId(UUID.randomUUID()), requester, null, duration, window));
        assertThrows(NullPointerException.class, () -> manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, null, window));
        assertThrows(NullPointerException.class, () -> manager.createRequest(new RequestId(UUID.randomUUID()), requester, categoryId, duration, null));
        assertThrows(NullPointerException.class, () -> manager.findRequestById(null));
    }

    @Test
    void unknownRequesterTakesPrecedenceOverUnknownCategory() {
        MemberId requester = new MemberId(UUID.randomUUID());
        CategoryId category = new CategoryId("gardening");
        EngagementManager manager = new EngagementManager(
                new FakeMemberReadAccess(Map.of(requester, true)),
                new FakeCategoryReadAccess(Set.of(category))
        );

        RequestCreationResult created = manager.createRequest(
                new RequestId(UUID.randomUUID()),
                requester,
                category,
                new RequestDuration(30),
                new RequestWindow(
                        LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 12)
                )
        );
        assertInstanceOf(RequestCreationResult.Success.class, created);
        List<Request> before = manager.getRequests();

        RequestCreationResult result = manager.createRequest(
                new RequestId(UUID.randomUUID()),
                new MemberId(UUID.randomUUID()),
                new CategoryId("unknown"),
                new RequestDuration(60),
                new RequestWindow(
                        LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 12)
                )
        );

        RequestCreationResult.Failure failure =
                assertInstanceOf(RequestCreationResult.Failure.class, result);
        assertEquals(RequestCreationError.NOT_FOUND, failure.code());
        assertEquals(before, manager.getRequests());
    }

    private static final class FakeMemberReadAccess implements MemberReadAccess {
        private final Map<MemberId, Boolean> membersByActiveStatus;

        private FakeMemberReadAccess(Map<MemberId, Boolean> membersByActiveStatus) {
            this.membersByActiveStatus = new HashMap<>(membersByActiveStatus);
        }

        @Override
        public boolean exists(MemberId id) {
            return membersByActiveStatus.containsKey(id);
        }

        @Override
        public boolean isActive(MemberId id) {
            return membersByActiveStatus.getOrDefault(id, false);
        }
    }

    private static final class FakeCategoryReadAccess implements CategoryReadAccess {
        private final Set<CategoryId> knownCategories;

        private FakeCategoryReadAccess(Set<CategoryId> knownCategories) {
            this.knownCategories = new HashSet<>(knownCategories);
        }

        @Override
        public boolean isKnown(CategoryId id) {
            return knownCategories.contains(id);
        }
    }
}
