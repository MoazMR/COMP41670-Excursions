package ie.ucd.meitheal.membermanager;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MemberDirectory implements MemberManagerPort {
    private final MemberManager members;

    public MemberDirectory(MemberManager members) {
        this.members = Objects.requireNonNull(members, "members");
    }

    @Override
    public Result<MemberProfile> findById(MemberId memberId) {
        try {
            return members.findById(memberId)
                    .map(member -> Result.success(profile(member)))
                    .orElseGet(() -> Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member does not exist"));
        } catch (MemberManagerException failure) {
            return rejection(failure);
        }
    }

    @Override
    public Result<MemberProfile> findByCanonicalEmail(String email) {
        try {
            return members.findByEmail(email)
                    .map(member -> Result.success(profile(member)))
                    .orElseGet(() -> Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member does not exist"));
        } catch (MemberManagerException failure) {
            return rejection(failure);
        }
    }

    @Override
    public Result<Boolean> isEligible(MemberId memberId) {
        try {
            return Result.success(members.isActive(memberId));
        } catch (MemberManagerException failure) {
            return rejection(failure);
        }
    }

    @Override
    public long memberCount() {
        return members.getMembers().size();
    }

    @Override
    public Result<MemberProfile> offer(MemberId memberId, ie.ucd.meitheal.shared.id.CategoryId categoryId) {
        try {
            // Shared IDs use exact values. Preserve the manager's member validation order.
            var localId = categoryId == null || !categoryId.value().equals(categoryId.value().trim())
                    ? null : new CategoryId(categoryId.value());
            return members.publishCategoryAndRead(memberId, localId)
                    .map(member -> Result.success(profile(member)))
                    .orElseGet(() -> Result.failure(FailureCode.DUPLICATE, "Category is already offered"));
        } catch (MemberManagerException failure) {
            return rejection(failure);
        }
    }

    @Override
    public Result<MemberProfile> reinstate(MemberId memberId) {
        try {
            return Result.success(profile(members.reinstate(memberId)));
        } catch (MemberManagerException failure) {
            return rejection(failure);
        }
    }

    private static MemberProfile profile(Member member) {
        var status = switch (member.status()) {
            case ACTIVE -> MemberStatus.ACTIVE;
            case SUSPENDED -> MemberStatus.SUSPENDED;
        };
        var categories = member.offeredCategories().stream()
                .map(category -> new ie.ucd.meitheal.shared.id.CategoryId(category.value()))
                .collect(Collectors.toUnmodifiableSet());
        return new MemberProfile(member.id(), member.name(), member.email().canonicalValue(), status, categories);
    }

    private static <T> Result<T> rejection(MemberManagerException failure) {
        var code = switch (failure.code()) {
            case INVALID_NAME, INVALID_EMAIL -> FailureCode.MALFORMED_INPUT;
            case DUPLICATE_EMAIL -> FailureCode.DUPLICATE_EMAIL;
            case MEMBER_NOT_FOUND -> FailureCode.MEMBER_NOT_FOUND;
            case MEMBER_SUSPENDED -> FailureCode.SUSPENDED_MEMBER;
            case INVALID_CATEGORY -> FailureCode.CATEGORY_NOT_FOUND;
            case INVALID_STATE, INVALID_REGISTRATION -> FailureCode.INVALID_STATE;
        };
        return Result.failure(code, failure.getMessage());
    }
}
