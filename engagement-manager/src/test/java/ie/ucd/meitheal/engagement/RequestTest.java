package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.engagement.port.CategoryId;
import ie.ucd.meitheal.engagement.port.MemberId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestTest {

    @Test
    void preservesAllFields() {
        RequestId id = new RequestId(UUID.randomUUID());
        MemberId requesterId = new MemberId(UUID.randomUUID());
        CategoryId categoryId = new CategoryId("gardening");
        RequestDuration duration = new RequestDuration(60);
        RequestWindow window = new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));
        RequestStatus status = RequestStatus.OPEN;

        Request request = new Request(id, requesterId, categoryId, duration, window, status);

        assertEquals(id, request.id());
        assertEquals(requesterId, request.requesterId());
        assertEquals(categoryId, request.categoryId());
        assertEquals(duration, request.duration());
        assertEquals(window, request.window());
        assertEquals(status, request.status());
    }

    @Test
    void rejectsNullId() {
        assertThrows(
            NullPointerException.class,
            () -> new Request(null, new MemberId(UUID.randomUUID()), new CategoryId("gardening"), new RequestDuration(30),
                new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)), RequestStatus.OPEN)
        );
    }

    @Test
    void rejectsNullRequesterId() {
        assertThrows(
            NullPointerException.class,
            () -> new Request(new RequestId(UUID.randomUUID()), null, new CategoryId("gardening"), new RequestDuration(30),
                new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)), RequestStatus.OPEN)
        );
    }

    @Test
    void rejectsNullCategoryId() {
        assertThrows(
            NullPointerException.class,
            () -> new Request(new RequestId(UUID.randomUUID()), new MemberId(UUID.randomUUID()), null,
                new RequestDuration(30), new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)),
                RequestStatus.OPEN)
        );
    }

    @Test
    void rejectsNullDuration() {
        assertThrows(
            NullPointerException.class,
            () -> new Request(new RequestId(UUID.randomUUID()), new MemberId(UUID.randomUUID()),
                new CategoryId("gardening"), null,
                new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)), RequestStatus.OPEN)
        );
    }

    @Test
    void rejectsNullWindow() {
        assertThrows(
            NullPointerException.class,
            () -> new Request(new RequestId(UUID.randomUUID()), new MemberId(UUID.randomUUID()),
                new CategoryId("gardening"), new RequestDuration(30), null, RequestStatus.OPEN)
        );
    }

    @Test
    void rejectsNullStatus() {
        assertThrows(
            NullPointerException.class,
            () -> new Request(new RequestId(UUID.randomUUID()), new MemberId(UUID.randomUUID()),
                new CategoryId("gardening"), new RequestDuration(30),
                new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)), null)
        );
    }
}
