package app.auth;

import app.config.AuthConfig;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthConfig authConfig;

    @Inject
    SessionManager sessionManager;

    @POST
    @Path("/login")
    public Response login(Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            return Response.status(401).entity(Map.of("error", "Invalid password")).build();
        }
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), authConfig.getPasswordHash());
        if (!result.verified) {
            return Response.status(401).entity(Map.of("error", "Invalid password")).build();
        }
        String token = sessionManager.createSession();
        NewCookie cookie = new NewCookie.Builder("lifeapp-session")
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(!authConfig.isDevMode())
                .maxAge(-1)
                .build();
        return Response.ok(Map.of("message", "ok")).cookie(cookie).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam("lifeapp-session") String session) {
        if (session != null) sessionManager.invalidate(session);
        NewCookie cookie = new NewCookie.Builder("lifeapp-session")
                .value("")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
        return Response.ok(Map.of("message", "ok")).cookie(cookie).build();
    }

    @GET
    @Path("/check")
    public Response check(@CookieParam("lifeapp-session") String session) {
        if (session != null && sessionManager.isValid(session)) {
            return Response.ok(Map.of("authenticated", true)).build();
        }
        return Response.status(401).entity(Map.of("authenticated", false)).build();
    }
}
