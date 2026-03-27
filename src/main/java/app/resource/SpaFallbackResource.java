package app.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;

@Path("/")
public class SpaFallbackResource {

    /**
     * SPA fallback: serves index.html for client-side routes that don't match
     * a static file or API endpoint. Quarkus automatically serves real static
     * files from META-INF/resources/ before this resource is hit.
     *
     * Only matches paths without a file extension (i.e., SPA routes like /inbox, /projects).
     * Paths with extensions (.js, .css, .png) are served by Quarkus static handler.
     */
    @GET
    @Path("/{path:(?!api|images|q/)[^.]+}")
    public Response fallback(@PathParam("path") String path) {
        InputStream is = getClass().getResourceAsStream("/META-INF/resources/index.html");
        if (is == null) return Response.status(404).build();
        return Response.ok(is).type("text/html").build();
    }
}
