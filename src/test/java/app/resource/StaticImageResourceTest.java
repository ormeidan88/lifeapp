package app.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class StaticImageResourceTest {

    @Test
    void servesExistingImage(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("test.jpg"), new byte[]{1, 2, 3});
        var resource = new StaticImageResource();
        resource.fsPath = dir.toString();
        var response = resource.serve("test.jpg");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void returns404ForMissing(@TempDir Path dir) throws Exception {
        var resource = new StaticImageResource();
        resource.fsPath = dir.toString();
        var response = resource.serve("nonexistent.jpg");
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
