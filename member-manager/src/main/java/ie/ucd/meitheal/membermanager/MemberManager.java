package ie.ucd.meitheal.membermanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MemberManager {
    private final Map<MemberId, Member> membersById = new LinkedHashMap<>();
    private final Map<String, MemberId> memberIdsByCanonicalEmail = new HashMap<>();
    private final Map<UUID, PreparedRegistration> preparedRegistrations = new HashMap<>();
    private final Map<String, UUID> reservationTokensByCanonicalEmail = new HashMap<>();
    private final Set<CategoryId> configuredCategories;

    public MemberManager(Collection<CategoryId> configuredCategories) {
        Objects.requireNonNull(configuredCategories, "configuredCategories");
        var categories = new HashSet<CategoryId>();
        for (var category : configuredCategories) {
            if (category == null) {
                throw new IllegalArgumentException("Configured categories must not contain null");
            }
            categories.add(category);
        }
        this.configuredCategories = Set.copyOf(categories);
    }

    public synchronized PreparedRegistration prepareRegistration(String name, String email) {
        if (name == null || name.isBlank()) {
            throw failure(MemberManagerException.Code.INVALID_NAME, "Member name must not be blank");
        }

        var normalizedName = name.trim();
        var address = parseEmail(email);
        var canonicalEmail = address.canonicalValue();
        if (memberIdsByCanonicalEmail.containsKey(canonicalEmail)
                || reservationTokensByCanonicalEmail.containsKey(canonicalEmail)) {
            throw failure(MemberManagerException.Code.DUPLICATE_EMAIL,
                    "A member with this email address already exists or is being registered");
        }

        var member = new Member(nextMemberId(), normalizedName, address, MemberStatus.ACTIVE, Set.of());
        var registration = new PreparedRegistration(UUID.randomUUID(), member);
        preparedRegistrations.put(registration.token(), registration);
        reservationTokensByCanonicalEmail.put(canonicalEmail, registration.token());
        return registration;
    }

    public synchronized Member commitRegistration(PreparedRegistration registration) {
        var pending = requirePreparedRegistration(registration);
        var member = pending.member();
        var canonicalEmail = member.email().canonicalValue();
        if (membersById.containsKey(member.id())
                || memberIdsByCanonicalEmail.containsKey(canonicalEmail)) {
            throw failure(MemberManagerException.Code.INVALID_REGISTRATION,
                    "Prepared registration conflicts with existing member state");
        }

        preparedRegistrations.remove(pending.token());
        reservationTokensByCanonicalEmail.remove(canonicalEmail);
        membersById.put(member.id(), member);
        memberIdsByCanonicalEmail.put(canonicalEmail, member.id());
        return member;
    }

    public synchronized void abortRegistration(PreparedRegistration registration) {
        var pending = requirePreparedRegistration(registration);
        preparedRegistrations.remove(pending.token());
        reservationTokensByCanonicalEmail.remove(pending.member().email().canonicalValue());
    }

    public synchronized Optional<Member> findById(MemberId memberId) {
        if (memberId == null) {
            throw failure(MemberManagerException.Code.MEMBER_NOT_FOUND, "Member ID must not be null");
        }
        return Optional.ofNullable(membersById.get(memberId));
    }

    public synchronized Optional<Member> findByEmail(String email) {
        var address = parseEmail(email);
        var memberId = memberIdsByCanonicalEmail.get(address.canonicalValue());
        return memberId == null ? Optional.empty() : Optional.of(membersById.get(memberId));
    }

    public synchronized List<Member> getMembers() {
        return List.copyOf(new ArrayList<>(membersById.values()));
    }

    public synchronized Member suspend(MemberId memberId) {
        var member = requireMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw failure(MemberManagerException.Code.INVALID_STATE, "Only an active member can be suspended");
        }
        var suspended = member.withStatus(MemberStatus.SUSPENDED);
        membersById.put(memberId, suspended);
        return suspended;
    }

    public synchronized Member reinstate(MemberId memberId) {
        var member = requireMember(memberId);
        if (member.status() != MemberStatus.SUSPENDED) {
            throw failure(MemberManagerException.Code.INVALID_STATE,
                    "Only a suspended member can be reinstated");
        }
        var active = member.withStatus(MemberStatus.ACTIVE);
        membersById.put(memberId, active);
        return active;
    }

    public synchronized boolean isActive(MemberId memberId) {
        return requireMember(memberId).status() == MemberStatus.ACTIVE;
    }

    public synchronized Member requireActive(MemberId memberId) {
        var member = requireMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw failure(MemberManagerException.Code.MEMBER_SUSPENDED, "Member is suspended");
        }
        return member;
    }

    public synchronized boolean publishCategory(MemberId memberId, CategoryId categoryId) {
        var member = requireActive(memberId);
        if (categoryId == null || !configuredCategories.contains(categoryId)) {
            throw failure(MemberManagerException.Code.INVALID_CATEGORY,
                    "Category is not in the configured category catalog");
        }
        if (member.offeredCategories().contains(categoryId)) {
            return false;
        }
        membersById.put(memberId, member.withOfferedCategory(categoryId));
        return true;
    }

    public synchronized Set<CategoryId> getConfiguredCategories() {
        return configuredCategories;
    }

    private Member requireMember(MemberId memberId) {
        if (memberId == null) {
            throw failure(MemberManagerException.Code.MEMBER_NOT_FOUND, "Member ID must not be null");
        }
        var member = membersById.get(memberId);
        if (member == null) {
            throw failure(MemberManagerException.Code.MEMBER_NOT_FOUND, "Member does not exist");
        }
        return member;
    }

    private PreparedRegistration requirePreparedRegistration(PreparedRegistration registration) {
        if (registration == null || preparedRegistrations.get(registration.token()) != registration) {
            throw failure(MemberManagerException.Code.INVALID_REGISTRATION,
                    "Registration is unknown, already completed, or belongs to another manager");
        }
        return registration;
    }

    private MemberId nextMemberId() {
        while (true) {
            var candidate = MemberId.create();
            if (!membersById.containsKey(candidate)
                    && preparedRegistrations.values().stream().noneMatch(reg -> reg.memberId().equals(candidate))) {
                return candidate;
            }
        }
    }

    private EmailAddress parseEmail(String email) {
        try {
            return new EmailAddress(email);
        } catch (IllegalArgumentException exception) {
            throw new MemberManagerException(MemberManagerException.Code.INVALID_EMAIL,
                    "Email address is invalid", exception);
        }
    }

    private MemberManagerException failure(MemberManagerException.Code code, String message) {
        return new MemberManagerException(code, message);
    }
}
