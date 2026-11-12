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
import java.util.function.Function;
import ie.ucd.meitheal.shared.id.MemberId;

public final class MemberManager {
    private final Object lock;
    private Map<MemberId, Member> membersById = new LinkedHashMap<>();
    private Map<String, MemberId> memberIdsByCanonicalEmail = new HashMap<>();
    private final Map<UUID, PreparedRegistration> preparedRegistrations = new HashMap<>();
    private final Map<String, UUID> reservationTokensByCanonicalEmail = new HashMap<>();
    private final Set<CategoryId> configuredCategories;
    private boolean registrationCallbackActive;

    public MemberManager(Collection<CategoryId> configuredCategories) {
        this(new Object(), configuredCategories);
    }

    public MemberManager(Object lock, Collection<CategoryId> configuredCategories) {
        this.lock = Objects.requireNonNull(lock, "lock");
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

    public PreparedRegistration prepareRegistration(MemberId memberId, String name, String email) {
        synchronized (lock) {
            rejectReentrantWrite();
            var member = proposedMember(memberId, name, email);
            var canonicalEmail = member.email().canonicalValue();
            var registration = new PreparedRegistration(UUID.randomUUID(), member);
            preparedRegistrations.put(registration.token(), registration);
            reservationTokensByCanonicalEmail.put(canonicalEmail, registration.token());
            return registration;
        }
    }

    public <T> T withRegistration(MemberId id, String name, String email,
            Function<Member, T> beforePublish) {
        synchronized (lock) {
            rejectReentrantWrite();
            var member = proposedMember(id, name, email);
            var canonicalEmail = member.email().canonicalValue();
            Objects.requireNonNull(beforePublish, "beforePublish");

            var nextMembersById = new LinkedHashMap<>(membersById);
            nextMembersById.put(id, member);
            var nextMemberIdsByCanonicalEmail = new HashMap<>(memberIdsByCanonicalEmail);
            nextMemberIdsByCanonicalEmail.put(canonicalEmail, id);

            registrationCallbackActive = true;
            try {
                var result = beforePublish.apply(member);
                membersById = nextMembersById;
                memberIdsByCanonicalEmail = nextMemberIdsByCanonicalEmail;
                return result;
            } finally {
                registrationCallbackActive = false;
            }
        }
    }

    private Member proposedMember(MemberId id, String name, String email) {
        var normalizedName = normalizeAndValidateName(name);
        var address = parseEmail(email);
        var canonicalEmail = address.canonicalValue();
        requireEmailAvailable(canonicalEmail);
        if (id == null || membersById.containsKey(id)
                || preparedRegistrations.values().stream().anyMatch(reg -> reg.memberId().equals(id))) {
            throw failure(MemberManagerException.Code.INVALID_REGISTRATION,
                    "Member ID is null, committed, or already reserved");
        }
        return new Member(id, normalizedName, address, MemberStatus.ACTIVE, Set.of());
    }

    private String normalizeAndValidateName(String name) {
        if (name == null || name.isBlank()) {
            throw failure(MemberManagerException.Code.INVALID_NAME, "Member name must not be blank");
        }
        return name.trim();
    }

    private void requireEmailAvailable(String canonicalEmail) {
        if (memberIdsByCanonicalEmail.containsKey(canonicalEmail)
                || reservationTokensByCanonicalEmail.containsKey(canonicalEmail)) {
            throw failure(MemberManagerException.Code.DUPLICATE_EMAIL,
                    "A member with this email address already exists or is being registered");
        }
    }

    public Member commitRegistration(PreparedRegistration registration) {
        synchronized (lock) {
            rejectReentrantWrite();
            var pending = requirePreparedRegistration(registration);
            var member = pending.member();
            var canonicalEmail = member.email().canonicalValue();
            if (membersById.containsKey(member.id())
                    || memberIdsByCanonicalEmail.containsKey(canonicalEmail)) {
                throw failure(MemberManagerException.Code.INVALID_REGISTRATION,
                        "Prepared registration conflicts with existing member state");
            }

            var nextMembersById = new LinkedHashMap<>(membersById);
            nextMembersById.put(member.id(), member);
            var nextMemberIdsByCanonicalEmail = new HashMap<>(memberIdsByCanonicalEmail);
            nextMemberIdsByCanonicalEmail.put(canonicalEmail, member.id());
            preparedRegistrations.remove(pending.token());
            reservationTokensByCanonicalEmail.remove(canonicalEmail);
            membersById = nextMembersById;
            memberIdsByCanonicalEmail = nextMemberIdsByCanonicalEmail;
            return member;
        }
    }

    public void abortRegistration(PreparedRegistration registration) {
        synchronized (lock) {
            rejectReentrantWrite();
            var pending = requirePreparedRegistration(registration);
            preparedRegistrations.remove(pending.token());
            reservationTokensByCanonicalEmail.remove(pending.member().email().canonicalValue());
        }
    }

    public Optional<Member> findById(MemberId memberId) {
        synchronized (lock) {
            if (memberId == null) {
                throw failure(MemberManagerException.Code.MEMBER_NOT_FOUND, "Member ID must not be null");
            }
            return Optional.ofNullable(membersById.get(memberId));
        }
    }

    public Optional<Member> findByEmail(String email) {
        synchronized (lock) {
            var address = parseEmail(email);
            var memberId = memberIdsByCanonicalEmail.get(address.canonicalValue());
            return memberId == null ? Optional.empty() : Optional.of(membersById.get(memberId));
        }
    }

    public List<Member> getMembers() {
        synchronized (lock) {
            return List.copyOf(new ArrayList<>(membersById.values()));
        }
    }

    public Member suspend(MemberId memberId) {
        synchronized (lock) {
            rejectReentrantWrite();
            var member = requireMember(memberId);
            if (member.status() != MemberStatus.ACTIVE) {
                throw failure(MemberManagerException.Code.INVALID_STATE, "Only an active member can be suspended");
            }
            var suspended = member.withStatus(MemberStatus.SUSPENDED);
            membersById.put(memberId, suspended);
            return suspended;
        }
    }

    public Member reinstate(MemberId memberId) {
        synchronized (lock) {
            rejectReentrantWrite();
            var member = requireMember(memberId);
            if (member.status() != MemberStatus.SUSPENDED) {
                throw failure(MemberManagerException.Code.INVALID_STATE,
                        "Only a suspended member can be reinstated");
            }
            var active = member.withStatus(MemberStatus.ACTIVE);
            membersById.put(memberId, active);
            return active;
        }
    }

    public boolean isActive(MemberId memberId) {
        synchronized (lock) {
            return requireMember(memberId).status() == MemberStatus.ACTIVE;
        }
    }

    public Member requireActive(MemberId memberId) {
        synchronized (lock) {
            var member = requireMember(memberId);
            if (member.status() != MemberStatus.ACTIVE) {
                throw failure(MemberManagerException.Code.MEMBER_SUSPENDED, "Member is suspended");
            }
            return member;
        }
    }

    public boolean publishCategory(MemberId memberId, CategoryId categoryId) {
        synchronized (lock) {
            rejectReentrantWrite();
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
    }

    public Set<CategoryId> getConfiguredCategories() {
        synchronized (lock) {
            return configuredCategories;
        }
    }

    Optional<Member> publishCategoryAndRead(MemberId memberId, CategoryId categoryId) {
        synchronized (lock) {
            return publishCategory(memberId, categoryId)
                    ? Optional.of(membersById.get(memberId))
                    : Optional.empty();
        }
    }

    private void rejectReentrantWrite() {
        if (registrationCallbackActive) {
            throw failure(MemberManagerException.Code.INVALID_REGISTRATION,
                    "Writes are not allowed during a registration callback");
        }
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
