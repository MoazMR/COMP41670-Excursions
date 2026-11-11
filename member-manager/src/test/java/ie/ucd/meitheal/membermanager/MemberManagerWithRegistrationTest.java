package ie.ucd.meitheal.membermanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import ie.ucd.meitheal.shared.id.MemberId;
import org.junit.jupiter.api.Test;

class MemberManagerWithRegistrationTest {
    private static final CategoryId GARDENING = new CategoryId("gardening");

    @Test
    void publishesAfterCallbackAndReturnsItsExactResult() {
        var lock = new Object();
        var manager = new MemberManager(lock, Set.of(GARDENING));
        var id = id("100001");
        var result = new Object();
        Member[] proposed = new Member[1];

        var actual = manager.withRegistration(id, " Alex ", " Alex@Example.com ", member -> {
            proposed[0] = member;
            assertTrue(Thread.holdsLock(lock));
            assertTrue(manager.findById(id).isEmpty());
            assertTrue(manager.getMembers().isEmpty());
            return result;
        });

        assertSame(result, actual);
        assertSame(proposed[0], manager.findById(id).orElseThrow());
        assertSame(proposed[0], manager.findByEmail("alex@example.com").orElseThrow());
        assertEquals("Alex", proposed[0].name());
        assertEquals("Alex@Example.com", proposed[0].email().value());
    }

    @Test
    void callbackFailureLeavesOldDataAndReservationFreeForRetry() {
        var manager = new MemberManager(Set.of(GARDENING));
        Member existing = manager.withRegistration(id("100001"), "Existing", "old@example.com", member -> member);
        var failure = new IllegalStateException("ledger preparation failed");

        var thrown = assertThrows(IllegalStateException.class,
                () -> manager.withRegistration(id("100002"), "New", "new@example.com", member -> {
                    assertSame(existing, manager.findById(existing.id()).orElseThrow());
                    assertTrue(manager.findById(id("100002")).isEmpty());
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(java.util.List.of(existing), manager.getMembers());
        assertTrue(manager.findByEmail("new@example.com").isEmpty());
        var retry = manager.withRegistration(id("100002"), "New", "new@example.com", member -> member);
        assertSame(retry, manager.findById(id("100002")).orElseThrow());
    }

    @Test
    void callbackChecksEmailBeforeNullOrReusedIdsAndIncludesLegacyReservations() {
        var manager = new MemberManager(Set.of());
        var pending = manager.prepareRegistration(id("100001"), "Pending", "pending@example.com");
        var duplicateEmail = assertThrows(MemberManagerException.class,
                () -> manager.withRegistration(id("100001"), "Other", " PENDING@example.com ", member -> member));
        assertEquals(MemberManagerException.Code.DUPLICATE_EMAIL, duplicateEmail.code());

        var reusedId = assertThrows(MemberManagerException.class,
                () -> manager.withRegistration(id("100001"), "Other", "other@example.com", member -> member));
        assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, reusedId.code());
        var nullId = assertThrows(MemberManagerException.class,
                () -> manager.withRegistration(null, "Other", "other@example.com", member -> member));
        assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, nullId.code());
        assertTrue(manager.getMembers().isEmpty());
        manager.abortRegistration(pending);
    }

    @Test
    void allMutationsAreRejectedInsideCallbackAndGuardClearsAfterward() {
        var manager = new MemberManager(Set.of(GARDENING));
        var existing = manager.withRegistration(id("100001"), "Existing", "old@example.com", member -> member);
        var pending = manager.prepareRegistration(id("100002"), "Pending", "pending@example.com");

        for (var write : java.util.List.<Runnable>of(
                () -> manager.prepareRegistration(id("100003"), "Third", "third@example.com"),
                () -> manager.commitRegistration(pending),
                () -> manager.abortRegistration(pending),
                () -> manager.suspend(existing.id()),
                () -> manager.reinstate(existing.id()),
                () -> manager.publishCategory(existing.id(), GARDENING),
                () -> manager.withRegistration(id("100004"), "Nested", "nested@example.com", member -> member))) {
            var thrown = assertThrows(MemberManagerException.class,
                    () -> manager.withRegistration(id("100003"), "Outer", "outer@example.com", member -> {
                        write.run();
                        return null;
                    }));
            assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, thrown.code());
            assertTrue(manager.findByEmail("outer@example.com").isEmpty());
        }

        assertEquals(1, manager.getMembers().size());
        Integer result = manager.withRegistration(id("100003"), "After", "after@example.com", member -> 1);
        assertEquals(Integer.valueOf(1), result);
        manager.abortRegistration(pending);
    }

    @Test
    void allReadsAndWritesSynchronizeOnInjectedLock() throws Exception {
        var lock = new ReentrantLock();
        var manager = new MemberManager(lock, Set.of(GARDENING));
        manager.withRegistration(id("100001"), "Alex", "alex@example.com", member -> member);

        var enteredRead = new java.util.concurrent.CountDownLatch(1);
        var finishedRead = new java.util.concurrent.CountDownLatch(1);
        synchronized (lock) {
            var reader = new Thread(() -> {
                enteredRead.countDown();
                manager.findById(id("100001"));
                finishedRead.countDown();
            });
            reader.start();
            assertTrue(enteredRead.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(finishedRead.await(50, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
        assertTrue(finishedRead.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(manager.publishCategory(id("100001"), GARDENING));
        assertTrue(manager.getConfiguredCategories().contains(GARDENING));
    }

    private static MemberId id(String value) {
        return new MemberId(value);
    }

}
