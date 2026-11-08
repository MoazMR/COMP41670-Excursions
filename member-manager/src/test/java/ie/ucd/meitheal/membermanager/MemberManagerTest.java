package ie.ucd.meitheal.membermanager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberManagerTest {
    private static final CategoryId GARDENING = new CategoryId("gardening");
    private static final CategoryId COOKING = new CategoryId("cooking");

    private MemberManager members;

    @BeforeEach
    void setUp() {
        members = new MemberManager(Set.of(GARDENING, COOKING));
    }

    @Test
    void registrationTrimsDataAndKeepsPreparedMemberInvisibleUntilCommit() {
        var prepared = members.prepareRegistration("  Alex Smith  ", "  Alex@Example.com ");

        assertEquals("Alex Smith", prepared.member().name());
        assertEquals("Alex@Example.com", prepared.member().email().value());
        assertEquals(MemberStatus.ACTIVE, prepared.member().status());
        assertTrue(members.findById(prepared.memberId()).isEmpty());
        assertTrue(members.findByEmail("alex@example.com").isEmpty());

        var committed = members.commitRegistration(prepared);

        assertEquals(prepared.memberId(), committed.id());
        assertEquals(committed, members.findById(committed.id()).orElseThrow());
        assertEquals(committed, members.findByEmail(" ALEX@example.COM ").orElseThrow());
    }

    @Test
    void registrationRejectsInvalidNamesAndEmailsWithoutChangingState() {
        assertCode(MemberManagerException.Code.INVALID_NAME,
                () -> members.prepareRegistration(" \t ", "alex@example.com"));
        assertCode(MemberManagerException.Code.INVALID_NAME,
                () -> members.prepareRegistration(null, "alex@example.com"));

        for (var email : List.of(
                "", "   ", "not-an-email", "alex@", "@example.com", "alex@example",
                "a..b@example.com", "alex@-example.com")) {
            assertCode(MemberManagerException.Code.INVALID_EMAIL,
                    () -> members.prepareRegistration("Alex", email));
        }

        assertTrue(members.getMembers().isEmpty());
        var valid = members.prepareRegistration("Alex", "alex@example.com");
        assertNotNull(valid);
        members.abortRegistration(valid);
    }

    @Test
    void duplicateCanonicalEmailIsRejectedForCommittedAndPreparedMembers() {
        var prepared = members.prepareRegistration("Alex", "  Alex@Example.com ");
        assertCode(MemberManagerException.Code.DUPLICATE_EMAIL,
                () -> members.prepareRegistration("Other", "alex@example.COM"));
        assertTrue(members.getMembers().isEmpty());
        members.commitRegistration(prepared);

        assertCode(MemberManagerException.Code.DUPLICATE_EMAIL,
                () -> members.prepareRegistration("Other", " ALEX@example.com "));
        assertEquals(1, members.getMembers().size());
        assertEquals("Alex", members.findByEmail(" alex@EXAMPLE.com ").orElseThrow().name());
    }

    @Test
    void abortingRegistrationReleasesCanonicalEmailReservation() {
        var prepared = members.prepareRegistration("Alex", "alex@example.com");
        members.abortRegistration(prepared);

        var replacement = members.prepareRegistration("Another Alex", " ALEX@EXAMPLE.COM ");
        assertNotEquals(prepared.memberId(), replacement.memberId());
        assertTrue(members.getMembers().isEmpty());
        members.abortRegistration(replacement);
    }

    @Test
    void registrationTokensCannotBeCommittedOrAbortedMoreThanOnceOrByAnotherManager() {
        var prepared = members.prepareRegistration("Alex", "alex@example.com");
        var otherManager = new MemberManager(Set.of(GARDENING));

        assertCode(MemberManagerException.Code.INVALID_REGISTRATION,
                () -> otherManager.commitRegistration(prepared));
        assertCode(MemberManagerException.Code.INVALID_REGISTRATION,
                () -> otherManager.abortRegistration(prepared));

        members.commitRegistration(prepared);
        assertCode(MemberManagerException.Code.INVALID_REGISTRATION,
                () -> members.commitRegistration(prepared));
        assertCode(MemberManagerException.Code.INVALID_REGISTRATION,
                () -> members.abortRegistration(prepared));
        assertEquals(1, members.getMembers().size());
    }

    @Test
    void suspensionAndReinstatementEnforceOnlyValidTransitions() {
        var member = register("alex@example.com");
        var activeSnapshot = members.findById(member.id()).orElseThrow();

        var suspended = members.suspend(member.id());
        assertEquals(MemberStatus.SUSPENDED, suspended.status());
        assertFalse(members.isActive(member.id()));
        assertCode(MemberManagerException.Code.MEMBER_SUSPENDED, () -> members.requireActive(member.id()));
        assertCode(MemberManagerException.Code.INVALID_STATE, () -> members.suspend(member.id()));
        assertEquals(MemberStatus.SUSPENDED, members.findById(member.id()).orElseThrow().status());

        var reinstated = members.reinstate(member.id());
        assertEquals(MemberStatus.ACTIVE, reinstated.status());
        assertTrue(members.isActive(member.id()));
        assertEquals(MemberStatus.ACTIVE, members.requireActive(member.id()).status());
        assertCode(MemberManagerException.Code.INVALID_STATE, () -> members.reinstate(member.id()));
        assertEquals(MemberStatus.ACTIVE, members.findById(member.id()).orElseThrow().status());
        assertEquals(MemberStatus.ACTIVE, activeSnapshot.status());
    }

    @Test
    void failedMemberLookupsAndStateTransitionsDoNotChangeState() {
        var member = register("alex@example.com");
        var unknown = new MemberId(UUID.randomUUID());

        assertCode(MemberManagerException.Code.MEMBER_NOT_FOUND, () -> members.suspend(unknown));
        assertCode(MemberManagerException.Code.MEMBER_NOT_FOUND, () -> members.reinstate(unknown));
        assertCode(MemberManagerException.Code.MEMBER_NOT_FOUND, () -> members.isActive(unknown));
        assertCode(MemberManagerException.Code.MEMBER_NOT_FOUND, () -> members.requireActive(unknown));
        assertEquals(List.of(member), members.getMembers());
        assertTrue(members.isActive(member.id()));
    }

    @Test
    void categoryPublicationRequiresConfiguredCategoryAndIsUnique() {
        var member = register("alex@example.com");

        assertTrue(members.publishCategory(member.id(), GARDENING));
        assertFalse(members.publishCategory(member.id(), GARDENING));
        assertTrue(members.publishCategory(member.id(), COOKING));
        assertEquals(Set.of(GARDENING, COOKING),
                members.findById(member.id()).orElseThrow().offeredCategories());

        assertCode(MemberManagerException.Code.INVALID_CATEGORY,
                () -> members.publishCategory(member.id(), new CategoryId("unconfigured")));
        assertCode(MemberManagerException.Code.INVALID_CATEGORY,
                () -> members.publishCategory(member.id(), null));
        assertEquals(Set.of(GARDENING, COOKING),
                members.findById(member.id()).orElseThrow().offeredCategories());
    }

    @Test
    void categoryIdsMustBeNonblankAndConfiguredCategoriesAreImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new CategoryId("  "));
        assertThrows(IllegalArgumentException.class, () -> new CategoryId(null));
        assertEquals("gardening", new CategoryId(" gardening ").value());
        assertThrows(UnsupportedOperationException.class,
                () -> members.getConfiguredCategories().add(new CategoryId("music")));
    }

    @Test
    void returnedListsSetsAndMemberViewsAreImmutableSnapshots() {
        var member = register("alex@example.com");
        var listSnapshot = members.getMembers();
        var originalMember = members.findById(member.id()).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> listSnapshot.add(member));
        assertThrows(UnsupportedOperationException.class,
                () -> originalMember.offeredCategories().add(GARDENING));

        members.publishCategory(member.id(), GARDENING);
        members.suspend(member.id());

        assertTrue(originalMember.offeredCategories().isEmpty());
        assertEquals(MemberStatus.ACTIVE, originalMember.status());
        assertTrue(listSnapshot.getFirst().offeredCategories().isEmpty());
        assertEquals(MemberStatus.ACTIVE, listSnapshot.getFirst().status());
        assertEquals(Set.of(GARDENING), members.findById(member.id()).orElseThrow().offeredCategories());
    }

    @Test
    void emailLookupRejectsMalformedInputRatherThanTreatingItAsMissing() {
        assertCode(MemberManagerException.Code.INVALID_EMAIL, () -> members.findByEmail("invalid"));
        assertTrue(members.findByEmail("valid@example.com").isEmpty());
    }

    private Member register(String email) {
        return members.commitRegistration(members.prepareRegistration("Alex", email));
    }

    private void assertCode(MemberManagerException.Code expected, Runnable action) {
        var exception = assertThrows(MemberManagerException.class, action::run);
        assertEquals(expected, exception.code());
    }
}
