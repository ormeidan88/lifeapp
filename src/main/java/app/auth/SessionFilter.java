package app.auth;

import app.config.AuthConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class SessionFilter implements ContainerRequestFilter {

    @Inject
    SessionManager sessionManager;

    @Inject
    AuthConfig authConfig;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();

        // Allow auth endpoints and health checks
        if (path.equals("/api/auth/login") || path.equals("/api/auth/check") || path.equals("/api/auth/logout") || path.startsWith("/q/")) {
            return;
        }

        // Skip non-API paths (frontend static files)
        if (!path.startsWith("/api/")) {
            return;
        }

        // Check API key header
        String apiKey = ctx.getHeaderString("X-API-Key");
        if (apiKey != null && apiKey.equals(authConfig.getApiKey())) {
            return;
        }

        // Check session cookie
        Cookie cookie = ctx.getCookies().get("lifeapp-session");
        if (cookie != null && sessionManager.isValid(cookie.getValue())) {
            return;
        }

        ctx.abortWith(Response.status(401)
                .entity(Map.of("error", "Unauthorized"))
                .build());
    }
}
