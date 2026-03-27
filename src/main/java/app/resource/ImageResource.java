package app.resource;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Path("/api/images")
@Produces(MediaType.APPLICATION_JSON)
public class ImageResource {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    @ConfigProperty(name = "app.images.storage", defaultValue = "filesystem")
    String storage;

    @ConfigProperty(name = "app.images.filesystem-path", defaultValue = "/tmp/lifeapp-images")
    String fsPath;

    @ConfigProperty(name = "app.images.s3-bucket", defaultValue = "lifeapp-images")
    String s3Bucket;

    @ConfigProperty(name = "quarkus.s3.aws.region", defaultValue = "us-east-1")
    String s3Region;

    jakarta.enterprise.inject.Instance<S3Client> s3ClientInstance;

    @jakarta.inject.Inject
    public ImageResource(jakarta.enterprise.inject.Instance<S3Client> s3ClientInstance) {
        this.s3ClientInstance = s3ClientInstance;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@RestForm("file") FileUpload file) throws IOException {
        if (file == null) return Response.status(400).entity(Map.of("error", "No file provided")).build();

        String contentType = file.contentType();
        if (!ALLOWED.contains(contentType)) {
            return Response.status(400).entity(Map.of("error", "Unsupported image format")).build();
        }
        if (file.size() > MAX_SIZE) {
            return Response.status(413).entity(Map.of("error", "Image exceeds 10MB limit")).build();
        }

        String ext = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };

        String filename = UlidCreator.getUlid().toString() + ext;

        if ("filesystem".equals(storage)) {
            java.nio.file.Path dir = Paths.get(fsPath);
            Files.createDirectories(dir);
            Files.copy(file.filePath(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return Response.status(201).entity(Map.of("url", "/images/" + filename)).build();
        }

        // S3 upload for production
        S3Client s3 = s3ClientInstance.get();
        String key = "images/" + filename;
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file.filePath())
        );
        String url = String.format("https://%s.s3.%s.amazonaws.com/%s", s3Bucket, s3Region, key);
        return Response.status(201).entity(Map.of("url", url)).build();
    }
}
