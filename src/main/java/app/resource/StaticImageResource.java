package app.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Path("/images")
public class StaticImageResource {

    @ConfigProperty(name = "app.images.filesystem-path", defaultValue = "/tmp/lifeapp-images")
    String fsPath;

    @GET
    @Path("/{filename}")
    public Response serve(@PathParam("filename") String filename) throws IOException {
        var path = Paths.get(fsPath, filename);
        if (!Files.exists(path)) return Response.status(404).build();
        String ct = Files.probeContentType(path);
        return Response.ok(Files.readAllBytes(path))
                .type(ct != null ? ct : MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }
}
