package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

import com.example.eventflow.storage.FileStorageService;
import com.example.eventflow.storage.LocalFileStorageService;
import com.example.eventflow.storage.S3FileStorageService;
import com.example.eventflow.storage.StorageException;
import com.example.eventflow.storage.StorageNotFoundException;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Local profile works without AWS; the prod profile only takes secrets from environment variables; S3 is mocked. */
class CloudConfigTest extends TestSupport {

    @Autowired FileStorageService storage;

    @Test
    void localProfileUsesDiskStorageWithoutAnyAwsSetup() {
        assertInstanceOf(LocalFileStorageService.class, storage);
    }

    @Test
    void localStorageRoundTripAndPathTraversalIsBlocked(@TempDir Path dir) {
        LocalFileStorageService local = new LocalFileStorageService(dir.toString());
        local.put("posters/a.png", new byte[] { 1, 2, 3 }, "image/png");
        assertArrayEquals(new byte[] { 1, 2, 3 }, local.get("posters/a.png").content());
        assertEquals("image/png", local.get("posters/a.png").contentType());
        assertThrows(StorageException.class, () -> local.get("../secret.txt"));
        assertThrows(StorageException.class, () -> local.put("/etc/passwd", new byte[0], "text/plain"));
        local.delete("posters/a.png");
        assertThrows(StorageNotFoundException.class, () -> local.get("posters/a.png"));
    }

    @Test
    void productionProfileReadsEverythingSecretFromEnvironmentVariables() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application-prod.properties"));
        env.getPropertySources().addFirst(new MapPropertySource("fakeEnv", Map.of(
                "DB_HOST", "mydb.abc.ap-south-1.rds.amazonaws.com", "DB_NAME", "eventflow", "DB_USERNAME", "app_user",
                "DB_PASSWORD", "s3cr3t-from-env", "S3_BUCKET", "my-bucket", "AWS_REGION", "ap-south-1")));

        assertEquals("jdbc:mysql://mydb.abc.ap-south-1.rds.amazonaws.com:3306/eventflow?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8",
                env.getProperty("spring.datasource.url"));
        assertEquals("app_user", env.getProperty("spring.datasource.username"));
        assertEquals("s3cr3t-from-env", env.getProperty("spring.datasource.password"));
        assertEquals("s3", env.getProperty("app.storage.type"));
        assertEquals("my-bucket", env.getProperty("app.storage.s3.bucket"));
        assertEquals("ap-south-1", env.getProperty("app.storage.s3.region"));
        assertEquals("false", env.getProperty("app.seed.demo-accounts"), "no demo logins in production");
        assertEquals("false", env.getProperty("spring.h2.console.enabled"));
    }

    @Test
    void productionPropertiesContainNoHardcodedSecrets() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/application-prod.properties"));
        for (String line : text.lines().filter(l -> !l.startsWith("#")).toList()) {
            String lower = line.toLowerCase();
            if (lower.contains("password") || lower.contains("secret") || lower.contains("access-key") || lower.contains("accesskey")) {
                assertTrue(line.contains("${"), "secret must come from an environment variable: " + line);
            }
        }
        assertFalse(text.contains("AKIA"), "no AWS access key id in the repository");
    }

    @Test
    void s3StorageIsSelectedOnlyWhenConfiguredAndUsesTheSdk() {
        S3Client s3 = mock(S3Client.class);
        new ApplicationContextRunner().withBean(S3Client.class, () -> s3).withUserConfiguration(S3FileStorageService.class)
                .withPropertyValues("app.storage.type=s3", "app.storage.s3.bucket=my-bucket")
                .run(ctx -> assertTrue(ctx.getBean(FileStorageService.class) instanceof S3FileStorageService));
        new ApplicationContextRunner().withBean(S3Client.class, () -> s3).withUserConfiguration(S3FileStorageService.class)
                .withPropertyValues("app.storage.type=local")
                .run(ctx -> assertFalse(ctx.containsBean("s3FileStorageService")));
    }

    @Test
    void s3PutGetDeleteGoToTheConfiguredBucket() {
        S3Client s3 = mock(S3Client.class);
        S3FileStorageService service = new S3FileStorageService(s3, "my-bucket");

        service.put("certificates/EFC-1.pdf", new byte[] { 9 }, "application/pdf");
        var put = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertEquals("my-bucket", put.getValue().bucket());
        assertEquals("certificates/EFC-1.pdf", put.getValue().key());
        assertEquals("application/pdf", put.getValue().contentType());

        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> bytes = mock(ResponseBytes.class);
        when(bytes.asByteArray()).thenReturn(new byte[] { 9 });
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytes);
        assertArrayEquals(new byte[] { 9 }, service.get("certificates/EFC-1.pdf").content());

        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("nope").build());
        assertThrows(StorageNotFoundException.class, () -> service.get("certificates/missing.pdf"));
        assertThrows(StorageException.class, () -> service.get("../etc/passwd"));
    }

    @Test
    void s3StorageFailsFastWithoutBucket() {
        assertThrows(IllegalStateException.class, () -> new S3FileStorageService(mock(S3Client.class), " "));
    }
}
