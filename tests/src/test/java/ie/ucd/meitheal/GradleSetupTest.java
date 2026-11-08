package ie.ucd.meitheal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleSetupTest {
    @Test
    void java21AndJunit5AreAvailable() {
        assertTrue(Runtime.version().feature() >= 21);
    }
}
