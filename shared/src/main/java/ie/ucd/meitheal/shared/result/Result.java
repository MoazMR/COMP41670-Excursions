package ie.ucd.meitheal.shared.result;

import java.util.Objects;
import java.util.Optional;

public final class Result<T> {
    private final T value;
    private final FailureCode failureCode;
    private final String detail;

    private Result(T value, FailureCode failureCode, String detail) {
        this.value = value;
        this.failureCode = failureCode;
        this.detail = detail;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(Objects.requireNonNull(value), null, null);
    }

    public static <T> Result<T> failure(FailureCode failureCode, String detail) {
        return new Result<>(null, Objects.requireNonNull(failureCode), detail);
    }

    public boolean isSuccess() {
        return failureCode == null;
    }

    public boolean isFailure() {
        return failureCode != null;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public Optional<FailureCode> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Optional<String> detail() {
        return Optional.ofNullable(detail);
    }
}
