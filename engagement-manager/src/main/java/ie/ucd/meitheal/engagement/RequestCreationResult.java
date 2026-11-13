package ie.ucd.meitheal.engagement;

import java.util.Objects;

public sealed interface RequestCreationResult permits RequestCreationResult.Success, RequestCreationResult.Failure {

    record Success(Request request) implements RequestCreationResult {
        public Success {
            request = Objects.requireNonNull(request, "request must not be null");
        }
    }

    record Failure(RequestCreationError code, String message) implements RequestCreationResult {
        public Failure {
            code = Objects.requireNonNull(code, "code must not be null");
            message = Objects.requireNonNull(message, "message must not be null");
        }
    }
}
