package ie.ucd.meitheal.membermanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberManagerWithSuspensionTest {
    private static final CategoryId GARDENING = new CategoryId("gardening");

    @Test
    void invalidMemberAndStatusReturnFailureWithoutCallingCallback() {
        var manager = new MemberManager(Set.of(GARDENING));
        var called = new boolean[1];

        assertEquals(FailureCode.MEMBER_NOT_FOUND,
                manager.withSuspension(id("missing"), member -> {
                    called[0] = true;
                    return Result.success(member);
                }).failureCode().orElseThrow());
        assertEquals(FailureCode.MEMBER_NOT_FOUND,
                manager.withSuspension(null, member -> {
                    called[0] = true;
                    return Result.success(member);
                }).failureCode().orElseThrow());

        var member = manager.withRegistration(id("100001"), "Alex", "alex@example.com", value -> value);
        manager.suspend(member.id());
        assertEquals(FailureCode.INVALID_STATE,
                manager.withSuspension(member.id(), value -> {
                    called[0] = true;
                    return Result.success(value);
                }).failureCode().orElseThrow());
        assertTrue(!called[0]);
    }

    @Test
    void readsSeeCommittedMemberDuringCallbackThenSuccessfulSuspensionPublishesAndCanReinstate() {
        var lock = new Object();
        var manager = new MemberManager(lock, Set.of(GARDENING));
        var member = manager.withRegistration(id("100001"), "Alex", "alex@example.com", value -> value);
        manager.publishCategory(member.id(), GARDENING);
        var committed = manager.findById(member.id()).orElseThrow();
        var result = Result.success("invalidated");

        var actual = manager.withSuspension(member.id(), suspended -> {
            assertTrue(Thread.holdsLock(lock));
            assertEquals(MemberStatus.SUSPENDED, suspended.status());
            assertSame(committed, manager.findById(member.id()).orElseThrow());
            assertSame(committed, manager.findByEmail("ALEX@example.com").orElseThrow());
            assertSame(committed, manager.getMembers().get(0));
            return result;
        });

        assertSame(result, actual);
        var suspended = manager.findById(member.id()).orElseThrow();
        assertEquals(MemberStatus.SUSPENDED, suspended.status());
        assertEquals(member.id(), suspended.id());
        assertEquals(member.name(), suspended.name());
        assertEquals(member.email(), suspended.email());
        assertEquals(Set.of(GARDENING), suspended.offeredCategories());
        assertSame(suspended, manager.findByEmail("alex@example.com").orElseThrow());
        assertEquals(MemberStatus.ACTIVE, manager.reinstate(member.id()).status());
    }

    @Test
    void callbackWritesAreRejectedAndRefusalKeepsStateAndGuardIsReleased() {
        var manager = new MemberManager(Set.of(GARDENING));
        var member = manager.withRegistration(id("100001"), "Alex", "alex@example.com", value -> value);
        var registrationGuard = assertThrows(MemberManagerException.class,
                () -> manager.withRegistration(id("100002"), "Nested", "nested@example.com", nested -> {
                    manager.suspend(member.id());
                    return nested;
                }));
        assertEquals("Writes are not allowed during a registration callback", registrationGuard.getMessage());
        var refusal = Result.<String>failure(FailureCode.BUSINESS_REJECTION, "proposal refused");
        var actual = manager.withSuspension(member.id(), suspended -> {
            assertSame(member, manager.findById(member.id()).orElseThrow());
            for (var write : java.util.List.<Runnable>of(
                    () -> manager.suspend(member.id()),
                    () -> manager.reinstate(member.id()),
                    () -> manager.publishCategory(member.id(), GARDENING),
                    () -> manager.prepareRegistration(id("100002"), "New", "new@example.com"))) {
                var failure = assertThrows(MemberManagerException.class, write::run);
                assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, failure.code());
                assertEquals("Writes are not allowed during a suspension callback", failure.getMessage());
            }
            var nestedRegistration = assertThrows(MemberManagerException.class,
                    () -> manager.withRegistration(id("100003"), "Nested", "nested@example.com", value -> value));
            assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, nestedRegistration.code());
            assertEquals("Writes are not allowed during a suspension callback", nestedRegistration.getMessage());
            var nestedSuspension = assertThrows(MemberManagerException.class,
                    () -> manager.withSuspension(member.id(), value -> Result.success("nested")));
            assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, nestedSuspension.code());
            assertEquals("Writes are not allowed during a suspension callback", nestedSuspension.getMessage());
            return refusal;
        });
        assertSame(refusal, actual);
        assertSame(member, manager.findById(member.id()).orElseThrow());
        assertTrue(manager.withSuspension(member.id(), suspended -> Result.success("retry")).isSuccess());
    }

    @Test
    void thrownOrNullCallbackResultDoesNotPublishAndRetryWorks() {
        var manager = new MemberManager(Set.of());
        var member = manager.withRegistration(id("100001"), "Alex", "alex@example.com", value -> value);
        var thrown = new IllegalStateException("proposal preparation failed");

        assertSame(thrown, assertThrows(IllegalStateException.class,
                () -> manager.withSuspension(member.id(), suspended -> { throw thrown; })));
        assertSame(member, manager.findById(member.id()).orElseThrow());
        assertThrows(NullPointerException.class,
                () -> manager.withSuspension(member.id(), suspended -> null));
        assertSame(member, manager.findById(member.id()).orElseThrow());
        assertThrows(NullPointerException.class, () -> manager.withSuspension(member.id(), null));
        assertSame(member, manager.findById(member.id()).orElseThrow());
        var retry = manager.withSuspension(member.id(), suspended -> Result.success("retry"));
        assertTrue(retry.isSuccess());
        assertEquals("retry", retry.value().orElseThrow());
        assertEquals(MemberStatus.SUSPENDED, manager.findById(member.id()).orElseThrow().status());
    }

    private static MemberId id(String value) {
        return new MemberId(value);
    }
}
