package ie.ucd.meitheal.membermanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberManagerRegistrationCharacterisationTest {
    private MemberManager members;

    @BeforeEach
    void setUp() {
        members = new MemberManager(Set.of(new CategoryId("gardening")));
    }

    @Test
    void preparedRegistrationIsInvisibleButReservesItsCanonicalEmail() {
        var prepared = members.prepareRegistration("  Alex Smith  ", " Alex@Example.com ");

        assertTrue(members.getMembers().isEmpty());
        assertTrue(members.findById(prepared.memberId()).isEmpty());
        assertTrue(members.findByEmail("alex@example.com").isEmpty());

        var duplicate = assertThrows(MemberManagerException.class,
                () -> members.prepareRegistration("Other", " ALEX@example.COM "));
        assertEquals(MemberManagerException.Code.DUPLICATE_EMAIL, duplicate.code());
        assertTrue(members.getMembers().isEmpty());
    }

    @Test
    void commitPublishesTheExactPreparedMemberAndConsumesTheToken() {
        var prepared = members.prepareRegistration(" Alex ", " Alex@Example.com ");

        var committed = members.commitRegistration(prepared);

        assertSame(prepared.member(), committed);
        assertSame(committed, members.findById(committed.id()).orElseThrow());
        assertEquals("Alex", committed.name());
        assertEquals("Alex@Example.com", committed.email().value());
        assertEquals(MemberStatus.ACTIVE, committed.status());

        var repeated = assertThrows(MemberManagerException.class,
                () -> members.commitRegistration(prepared));
        assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, repeated.code());
        assertEquals(1, members.getMembers().size());
    }

    @Test
    void abortConsumesTheTokenAndReleasesTheCanonicalEmail() {
        var prepared = members.prepareRegistration("Alex", "alex@example.com");

        members.abortRegistration(prepared);

        var repeated = assertThrows(MemberManagerException.class,
                () -> members.abortRegistration(prepared));
        assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, repeated.code());
        assertTrue(members.getMembers().isEmpty());

        var replacement = members.prepareRegistration("Replacement", " ALEX@EXAMPLE.COM ");
        assertEquals("Replacement", replacement.member().name());
    }

    @Test
    void foreignManagerRejectsPreparedTokenWithoutChangingEitherManager() {
        var prepared = members.prepareRegistration("Alex", "alex@example.com");
        var other = new MemberManager(Set.of(new CategoryId("gardening")));

        var exception = assertThrows(MemberManagerException.class,
                () -> other.commitRegistration(prepared));

        assertEquals(MemberManagerException.Code.INVALID_REGISTRATION, exception.code());
        assertTrue(other.getMembers().isEmpty());
        assertTrue(members.getMembers().isEmpty());
        assertSame(prepared.member(), members.commitRegistration(prepared));
    }

    @Test
    void invalidNameTakesPrecedenceOverInvalidEmailWithoutChangingState() {
        var exception = assertThrows(MemberManagerException.class,
                () -> members.prepareRegistration("   ", "not-an-email"));

        assertEquals(MemberManagerException.Code.INVALID_NAME, exception.code());
        assertEquals("Member name must not be blank", exception.getMessage());
        assertTrue(members.getMembers().isEmpty());
    }
}
