package app.resource;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageResourceUnitTest {

    @Test
    @SuppressWarnings("unchecked")
    void s3UploadPath() throws Exception {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        Instance<S3Client> instance = mock(Instance.class);
        when(instance.get()).thenReturn(s3);

        var resource = new ImageResource(instance);
        resource.storage = "s3";
        resource.s3Bucket = "my-bucket";
        resource.s3Region = "us-east-1";
        resource.fsPath = "/tmp/test";

        File tmp = File.createTempFile("test", ".jpg");
        Files.write(tmp.toPath(), new byte[]{(byte) 0xFF, (byte) 0xD8});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.size()).thenReturn(1024L);
        when(upload.filePath()).thenReturn(tmp.toPath());

        var response = resource.upload(upload);
        assertThat(response.getStatus()).isEqualTo(201);
        verify(s3).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        tmp.delete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void filesystemUploadPath() throws Exception {
        Instance<S3Client> instance = mock(Instance.class);
        var resource = new ImageResource(instance);
        resource.storage = "filesystem";
        resource.fsPath = "/tmp/lifeapp-test-img-" + System.nanoTime();

        File tmp = File.createTempFile("test", ".png");
        Files.write(tmp.toPath(), new byte[]{(byte) 0x89, 0x50});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/png");
        when(upload.size()).thenReturn(512L);
        when(upload.filePath()).thenReturn(tmp.toPath());

        var response = resource.upload(upload);
        assertThat(response.getStatus()).isEqualTo(201);
        tmp.delete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNullFile() throws Exception {
        Instance<S3Client> instance = mock(Instance.class);
        var resource = new ImageResource(instance);
        var response = resource.upload(null);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsUnsupportedType() throws Exception {
        Instance<S3Client> instance = mock(Instance.class);
        var resource = new ImageResource(instance);
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("application/pdf");
        var response = resource.upload(upload);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsOversized() throws Exception {
        Instance<S3Client> instance = mock(Instance.class);
        var resource = new ImageResource(instance);
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.size()).thenReturn(11L * 1024 * 1024);
        var response = resource.upload(upload);
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allExtensions() throws Exception {
        Instance<S3Client> instance = mock(Instance.class);
        var resource = new ImageResource(instance);
        resource.storage = "filesystem";
        resource.fsPath = "/tmp/lifeapp-ext-" + System.nanoTime();

        for (String[] ct : new String[][]{{"image/jpeg", ".jpg"}, {"image/png", ".png"}, {"image/gif", ".gif"}, {"image/webp", ".webp"}}) {
            File tmp = File.createTempFile("ext", ct[1]);
            Files.write(tmp.toPath(), new byte[]{1, 2, 3});
            FileUpload upload = mock(FileUpload.class);
            when(upload.contentType()).thenReturn(ct[0]);
            when(upload.size()).thenReturn(100L);
            when(upload.filePath()).thenReturn(tmp.toPath());
            var response = resource.upload(upload);
            assertThat(response.getStatus()).isEqualTo(201);
            tmp.delete();
        }
    }
}
