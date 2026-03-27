package app.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SessionManager {

    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final SecureRandom random = new SecureRandom();

    public String createSession() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.add(token);
        return token;
    }

    public boolean isValid(String token) {
        return token != null && sessions.contains(token);
    }

    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }
}
