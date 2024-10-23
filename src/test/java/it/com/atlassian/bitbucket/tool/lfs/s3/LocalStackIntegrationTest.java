package it.com.atlassian.bitbucket.tool.lfs.s3;

import com.atlassian.bitbucket.tool.lfs.s3.AppConfiguration;
import com.atlassian.bitbucket.tool.lfs.s3.S3TransferManager;
import com.atlassian.bitbucket.tool.lfs.s3.TransferSummary;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

public class LocalStackIntegrationTest {

    private static final String TEST_HIERARCHY = "1234567890abcdef1234";
    private static final String TEST_BUCKET = "bitbucket-object-store";
    private static final String TEST_CONFIG_FILENAME = "config.properties";
    private static final String LFS_DIR = "shared/data/git-lfs/storage";

    private final DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:3.7.2");

    private S3Client s3Client;

    @Rule
    public LocalStackContainer localstack = new LocalStackContainer(localstackImage)
            .withServices(S3);

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();

        createBucket(TEST_BUCKET);
    }

    @After
    public void tearDown() {
        s3Client.close();
    }

    @Test
    public void testMigration() throws IOException {
        Path homeDir = tmpFolder.newFolder().toPath();
        Path configFile = writeConfigFile(homeDir);
        Path storageDir = homeDir.resolve(LFS_DIR);

        UUID randomContent = UUID.randomUUID();
        String oid = writeObject(storageDir, randomContent.toString());

        // Perform migration
        AppConfiguration config = new AppConfiguration(configFile.toString());
        TransferSummary summary;
        try (S3TransferManager s3TransferManager = new S3TransferManager(config)) {
            summary = s3TransferManager.upload(storageDir);
        }

        // Verify migration
        assertNotNull(summary);
        assertEquals(1, summary.getSuccessful());
        assertEquals(0, summary.getFailed());
        assertEquals(0, summary.getSkipped());
        assertEquals(randomContent.toString(), downloadObject(oid));
    }

    private boolean bucketExists(String bucket) {
        ListBucketsRequest request = ListBucketsRequest.builder().build();
        ListBucketsResponse response = s3Client.listBuckets(request);
        return response.buckets()
                .stream()
                .anyMatch(b -> b.name().equalsIgnoreCase(bucket));
    }

    private void createBucket(String bucket) {
        if (bucketExists(bucket)) {
            return;
        }
        CreateBucketResponse response = s3Client.createBucket(
                CreateBucketRequest.builder()
                        .bucket(TEST_BUCKET)
                        .build());

        if (!response.sdkHttpResponse().isSuccessful()) {
            throw new RuntimeException("Failed to create bucket: " + bucket);
        }
    }

    private String downloadObject(String oid) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(TEST_BUCKET)
                .key(toObjectKey(oid))
                .build();

        try (InputStream inputStream = s3Client.getObject(request)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path toObjectPath(Path storageDir, String oid) {
        return storageDir
                .resolve(TEST_HIERARCHY)
                .resolve(oid.substring(0, 2))
                .resolve(oid.substring(2));
    }

    private String toObjectKey(String oid) {
        return "git-lfs/" + TEST_HIERARCHY + "/" + oid.substring(0, 2) + "/" + oid.substring(2);
    }

    private Path writeConfigFile(Path homeDir) throws IOException {
        Path configFile = homeDir.resolve(TEST_CONFIG_FILENAME);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(configFile))) {
            writer.write("bitbucket.home=" + homeDir + "\n");
            writer.write("s3.bucket=" + TEST_BUCKET + "\n");
            writer.write("s3.region=" + localstack.getRegion() + "\n");
            writer.write("s3.access-key=" + localstack.getAccessKey() + "\n");
            writer.write("s3.secret-key=" + localstack.getSecretKey() + "\n");
            writer.write("s3.endpoint-override=" + localstack.getEndpointOverride(S3) + "\n");
        }

        return configFile;
    }

    private String writeObject(Path storageDir, String content) throws IOException {
        String oid = DigestUtils.sha256Hex(content);
        Path objectPath = toObjectPath(storageDir, oid);
        Files.createDirectories(objectPath.getParent());
        Files.write(objectPath, content.getBytes());
        return oid;
    }
}
