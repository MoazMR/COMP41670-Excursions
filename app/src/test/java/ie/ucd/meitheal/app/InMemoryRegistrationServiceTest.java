package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.FailureCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryRegistrationServiceTest {
    @Test
    void registersAndLooksUpMemberWithActualWelcomeBalance() {
        RegistrationService service = InMemoryRegistrationService.create();
        var registered = service.register("  Alex Example  ", "Alex@Example.com");

        assertTrue(registered.isSuccess());
        var member = registered.value().orElseThrow();
        assertEquals("100001", member.id().value());
        assertEquals("Alex Example", member.name());
        assertEquals("Alex@Example.com", member.email());
        assertEquals(120, member.ledgerCredits());
        assertEquals(member, service.findMember(member.id()).value().orElseThrow());
        assertEquals(member, service.findMemberByEmail(" alex@example.COM ").value().orElseThrow());
        var balance = service.balance(member.id()).value().orElseThrow();
        assertEquals(120, balance.ledgerBalance());
        assertEquals(0, balance.held());
        assertEquals(120, balance.available());
    }

    @Test
    void returnsStableFailuresForMissingAndRejectedMembers() {
        RegistrationService service = InMemoryRegistrationService.create();
        assertEquals(FailureCode.MEMBER_NOT_FOUND,
                service.findMember(new MemberId("999999")).failureCode().orElseThrow());
        assertEquals(FailureCode.MEMBER_NOT_FOUND,
                service.balance(new MemberId("999999")).failureCode().orElseThrow());
        assertEquals(FailureCode.MALFORMED_INPUT,
                service.register(" ", "not-an-email").failureCode().orElseThrow());
        service.register("Alex", "alex@example.com").value().orElseThrow();
        assertEquals(FailureCode.DUPLICATE_EMAIL,
                service.register("Alex Two", "ALEX@example.com").failureCode().orElseThrow());
    }
}
