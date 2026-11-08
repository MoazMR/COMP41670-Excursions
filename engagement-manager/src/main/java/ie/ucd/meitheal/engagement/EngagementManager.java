package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.engagement.port.CategoryId;
import ie.ucd.meitheal.engagement.port.CategoryReadAccess;
import ie.ucd.meitheal.engagement.port.MemberId;
import ie.ucd.meitheal.engagement.port.MemberReadAccess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EngagementManager {
    private final MemberReadAccess memberReadAccess;
    private final CategoryReadAccess categoryReadAccess;
    private final Map<RequestId, Request> requestsById = new LinkedHashMap<>();

    public EngagementManager(MemberReadAccess memberReadAccess, CategoryReadAccess categoryReadAccess) {
        this.memberReadAccess = Objects.requireNonNull(memberReadAccess, "memberReadAccess must not be null");
        this.categoryReadAccess = Objects.requireNonNull(categoryReadAccess, "categoryReadAccess must not be null");
    }

    public synchronized RequestCreationResult createRequest(
        RequestId requestId,
        MemberId requesterId,
        CategoryId categoryId,
        RequestDuration duration,
        RequestWindow window
    ) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(requesterId, "requesterId must not be null");
        Objects.requireNonNull(categoryId, "categoryId must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(window, "window must not be null");

        if (!memberReadAccess.exists(requesterId)) {
            return new RequestCreationResult.Failure(RequestCreationError.NOT_FOUND, "Requester not found");
        }

        if (!memberReadAccess.isActive(requesterId)) {
            return new RequestCreationResult.Failure(
                RequestCreationError.SUSPENDED_MEMBER,
                "Requester is not active"
            );
        }

        if (!categoryReadAccess.isKnown(categoryId)) {
            return new RequestCreationResult.Failure(
                RequestCreationError.INVALID_CATEGORY,
                "Category is not known"
            );
        }

        if (requestsById.containsKey(requestId)) {
            return new RequestCreationResult.Failure(
                RequestCreationError.INVALID_STATE,
                "Request ID already exists"
            );
        }

        Request request = new Request(requestId, requesterId, categoryId, duration, window, RequestStatus.OPEN);
        requestsById.put(requestId, request);
        return new RequestCreationResult.Success(request);
    }

    public synchronized Optional<Request> findRequestById(RequestId requestId) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        return Optional.ofNullable(requestsById.get(requestId));
    }

    public synchronized List<Request> getRequests() {
        return List.copyOf(requestsById.values());
    }
}
