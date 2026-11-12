package ie.ucd.meitheal.membermanager;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.result.FailureCode;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberDirectoryTest {
    private static final MemberId HELPER = new MemberId("100001");
    private static final CategoryId GARDENING = new CategoryId("gardening");
    private final MemberManager members = new MemberManager(Set.of(
            new ie.ucd.meitheal.membermanager.CategoryId(GARDENING.value())));
    private final MemberManagerPort directory = new MemberDirectory(members);

    @Test
    void directoryReadsCommittedMembersAndCanonicalizesEmail() {
        var prepared = members.prepareRegistration(HELPER, "Alex", " Alex@Example.com ");

        assertEquals(0, directory.memberCount());
        assertEquals(FailureCode.MEMBER_NOT_FOUND, directory.findById(HELPER).failureCode().orElseThrow());
        assertEquals(FailureCode.MEMBER_NOT_FOUND, directory.isEligible(HELPER).failureCode().orElseThrow());
        assertEquals(FailureCode.MEMBER_NOT_FOUND,
                directory.findByCanonicalEmail("alex@example.com").failureCode().orElseThrow());

        members.commitRegistration(prepared);

        var profile = directory.findById(HELPER).value().orElseThrow();
        assertEquals(profile, directory.findByCanonicalEmail(" ALEX@EXAMPLE.COM ").value().orElseThrow());
        assertEquals("alex@example.com", profile.canonicalEmail());
        assertEquals(MemberStatus.ACTIVE, profile.status());
        assertEquals(1, directory.memberCount());
        assertTrue(directory.isEligible(HELPER).value().orElseThrow());
        assertEquals(FailureCode.MALFORMED_INPUT, directory.findByCanonicalEmail("bad").failureCode().orElseThrow());
    }

    @Test
    void offersUseExactConfiguredIdsAndReturnImmutableSnapshots() {
        members.commitRegistration(members.prepareRegistration(HELPER, "Alex", "alex@example.com"));
        var before = directory.findById(HELPER).value().orElseThrow();

        assertEquals(FailureCode.CATEGORY_NOT_FOUND,
                directory.offer(HELPER, new CategoryId("cooking")).failureCode().orElseThrow());
        assertEquals(FailureCode.CATEGORY_NOT_FOUND,
                directory.offer(HELPER, new CategoryId(" gardening ")).failureCode().orElseThrow());
        assertEquals(before, directory.findById(HELPER).value().orElseThrow());

        var offered = directory.offer(HELPER, GARDENING).value().orElseThrow();
        assertTrue(before.categories().isEmpty());
        assertEquals(Set.of(GARDENING), offered.categories());
        assertThrows(UnsupportedOperationException.class, () -> offered.categories().clear());
        assertEquals(FailureCode.DUPLICATE, directory.offer(HELPER, GARDENING).failureCode().orElseThrow());
        assertEquals(offered, directory.findById(HELPER).value().orElseThrow());
    }

    @Test
    void eligibilityTracksSuspensionAndReinstatementWithSkillsPreserved() {
        members.commitRegistration(members.prepareRegistration(HELPER, "Alex", "alex@example.com"));
        directory.offer(HELPER, GARDENING).value().orElseThrow();
        members.suspend(HELPER);

        assertFalse(directory.isEligible(HELPER).value().orElseThrow());
        assertEquals(FailureCode.SUSPENDED_MEMBER, directory.offer(HELPER, GARDENING).failureCode().orElseThrow());
        var suspended = directory.findById(HELPER).value().orElseThrow();
        assertEquals(MemberStatus.SUSPENDED, suspended.status());
        assertEquals(Set.of(GARDENING), suspended.categories());

        var reinstated = directory.reinstate(HELPER).value().orElseThrow();
        assertEquals(MemberStatus.ACTIVE, reinstated.status());
        assertEquals(Set.of(GARDENING), reinstated.categories());
        assertTrue(directory.isEligible(HELPER).value().orElseThrow());
        assertEquals(FailureCode.INVALID_STATE, directory.reinstate(HELPER).failureCode().orElseThrow());
    }
}
