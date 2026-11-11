package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.MemberView;

/** Narrow public boundary for registration and member balance queries. */
public interface RegistrationService {
    /** Invalid input returns MALFORMED_INPUT. A duplicate canonical email returns DUPLICATE_EMAIL.
     * A successful call publishes an active member and exactly one 120-credit welcome grant together. */
    Result<MemberView> register(String name, String email);

    Result<MemberView> findMember(MemberId memberId);

    /** Applies the membership owner's canonical email rules. */
    Result<MemberView> findMemberByEmail(String email);

    Result<BalanceView> balance(MemberId memberId);
}
