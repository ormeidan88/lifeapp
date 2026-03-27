package app.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionManagerTest {

    @Test
    void createSessionReturnsUniqueTokens() {
        var mgr = new SessionManager();
        String t1 = mgr.createSession();
        String t2 = mgr.createSession();
        assertThat(t1).isNotBlank();
        assertThat(t2).isNotBlank();
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void isValidReturnsTrueForActiveSession() {
        var mgr = new SessionManager();
        String token = mgr.createSession();
        assertThat(mgr.isValid(token)).isTrue();
    }

    @Test
    void isValidReturnsFalseForUnknownToken() {
        var mgr = new SessionManager();
        assertThat(mgr.isValid("unknown")).isFalse();
    }

    @Test
    void isValidReturnsFalseForNull() {
        var mgr = new SessionManager();
        assertThat(mgr.isValid(null)).isFalse();
    }

    @Test
    void invalidateRemovesSession() {
        var mgr = new SessionManager();
        String token = mgr.createSession();
        assertThat(mgr.isValid(token)).isTrue();
        mgr.invalidate(token);
        assertThat(mgr.isValid(token)).isFalse();
    }

    @Test
    void invalidateNullDoesNotThrow() {
        var mgr = new SessionManager();
        assertThatNoException().isThrownBy(() -> mgr.invalidate(null));
    }
}
