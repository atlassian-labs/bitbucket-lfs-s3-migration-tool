package com.atlassian.bitbucket.tool.lfs.s3;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.codec.binary.Base64.decodeBase64;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;

public class S3TransferManagerHelper {

    /**
     * A prefix added so Git LFS content is namespaced in the bucket, so it can coexist with other usages
     * of the same bucket. Bitbucket Data Center expects this prefix to be present.
     */
    public static final String BUCKET_KEY_PREFIX = "git-lfs";
    /**
     * Bitbucket Data Center's embedded LFS object store stores objects for multiple repositories in a collection
     * called a "hierarchy". Any repository related by forking has the same hierarchy ID and as such all LFS objects
     * for those repositories are stored in the same collection. A hierarchy ID is a 20 character lower-case
     * hexadecimal string.
     */
    public static final Pattern HIERARCHY_ID_PATTERN = Pattern.compile("[0-9a-f]{20}");

    /**
     * The LFS storage directory consists of files stored in directories containing the first two characters of
     * the SHA256 hash (i.e. the LFS object ID). This pattern matches that part, i.e. the {@code <sha256[0:1]>} path
     * element.
     */
    private static final Pattern OID_LEVEL1_PATTERN = Pattern.compile("[0-9a-f]{2}");
    /**
     * The LFS storage directory consists of files stores in directories containing the first two characters of
     * the SHA256 hash (i.e. the LFS object ID), each containing files with names {@code <sha256[2:61]>}. This pattern
     * matches that filename path element.
     */
    private static final Pattern OID_LEVEL2_PATTERN = Pattern.compile("[0-9a-f]{62}");

    private final String bucket;
    private final S3Client client;

    public S3TransferManagerHelper(String bucket, S3Client client) {
        this.bucket = bucket;
        this.client = client;
    }

    /**
     * @return a stream of paths from the given directory.
     */
    protected static Stream<Path> getDirStream(Path dir, Pattern objectIdPattern) {
        try {
            return Files.list(dir)
                    .filter(objectIdPattern.equals(OID_LEVEL1_PATTERN) || objectIdPattern.equals(HIERARCHY_ID_PATTERN) ?
                            Files::isDirectory : Files::isRegularFile)
                    .filter(path -> objectIdPattern.matcher(path.getFileName().toString()).matches());
        } catch (IOException e) {
            System.err.printf("Error reading directory: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Downloads all LFS objects under the given hierarchy.
     * <p>
     * LFS objects are stored with structure <hierarchy dir>/<oid[0:2]>/<oid[2:63]> where OID is
     * the SHA256 checksum of the object.
     */
    protected TransferSummary downloadHierarchy(String hierarchy, Path hierarchyDir) {
        TransferSummary summary = new TransferSummary();

        listObjects(BUCKET_KEY_PREFIX + "/" + hierarchy).stream()
                .flatMap(resp -> resp.contents().stream())
                .forEach(object -> {
                    String key = object.key();
                    String oid = key.split("/")[2];
                    String oidLevel1 = oid.substring(0, 2);
                    String oidLevel2 = oid.substring(2);

                    Path oidLevel1Dir = hierarchyDir.resolve(oidLevel1);
                    if (!Files.exists(oidLevel1Dir) || !Files.isDirectory(oidLevel1Dir)) {
                        oidLevel1Dir.toFile().mkdirs();
                    }

                    Path file = oidLevel1Dir.resolve(oidLevel2);
                    if (Files.exists(file)) {
                        summary.incrementSuccessful();
                    } else {
                        boolean success = downloadObject(file, key);
                        if (success) {
                            summary.incrementSuccessful();
                        } else {
                            summary.incrementFailed();
                        }
                    }
                });

        return summary;
    }

    /**
     @return {@code true} if the download was successful, otherwise {@code false}.
     */
    protected boolean downloadObject(Path objectPath, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .checksumMode(ChecksumMode.ENABLED)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = client.getObjectAsBytes(request);

            try (FileOutputStream outputStream = new FileOutputStream(objectPath.toFile())) {
                outputStream.write(objectBytes.asByteArray());
            }
        } catch (S3Exception | IOException e) {
            System.err.println("  Error: Failure when downloading " +  key + ": " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * @return list of hierarchies for LFS objects in the bucket.
     */
    protected List<String> getHierarchies() {
        return listObjects(BUCKET_KEY_PREFIX).stream()
                .flatMap(resp -> resp.commonPrefixes().stream())
                .map(prefix -> prefix.prefix().split("/")[1])
                .collect(Collectors.toList());
    }

    /**
     * @return an iterable of LFS objects in the bucket that are namespaced with the given prefix.
     */
    protected ListObjectsV2Iterable listObjects(String prefix) {
        String delimiter = "/";
        if (!prefix.endsWith(delimiter)) {
            prefix += delimiter;
        }

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter(delimiter)
                .build();

        try {
            return client.listObjectsV2Paginator(request);
        } catch (S3Exception e) {
            System.err.println("  Error: Failure when reading from bucket: " + e.getMessage());
            System.exit(1);
        }

        return null;
    }

    /**
     * @return {@code true} if the object exists in the bucket, otherwise {@code false}.
     */
    protected boolean objectExists(Path objectPath, String hierarchy) {
        String oid = pathToOid(objectPath);
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(toKey(hierarchy, oid))
                .build();

        try {
            client.headObject(request);
        } catch (NoSuchKeyException ignored) {
            return false;
        } catch (S3Exception e) {
            System.err.println("  Error: Failure when reading from bucket for " +  oid + ": " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * @return {@code true} if the upload was successful, otherwise {@code false}.
     */
    protected boolean uploadObject(Path objectPath, String hierarchy) {
        String oid = pathToOid(objectPath);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(toKey(hierarchy, oid))
                    .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                    .checksumSHA256(hexToBase64(oid))
                    .build();

            PutObjectResponse response = client.putObject(request, objectPath);
            String actualChecksum = base64toHex(response.checksumSHA256());
            if (!actualChecksum.equalsIgnoreCase(oid)) {
                System.out.println(" Warning: Object uploaded but has wrong checksum. Expected: "
                        + oid + " Actual: " + actualChecksum);
            }
        } catch (S3Exception e) {
            System.err.println("  Error: Failure when uploading " +  oid + ": " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Uploads all LFS objects under the given hierarchy.
     * <p>
     * LFS objects are stored with structure <hierarchy dir>/<oid[0:2]>/<oid[2:63]> where OID is
     * the SHA256 checksum of the object.
     */
    protected TransferSummary uploadHierarchy(Path hierarchyDir) {
        TransferSummary summary = new TransferSummary();

        // Java is not smart enough to close streams that need to release file descriptors
        // so use try-with-resources
        try (Stream<Path> hierarchyDirStream = getDirStream(hierarchyDir, OID_LEVEL1_PATTERN)) {
            hierarchyDirStream.forEach(objectDir -> {
                try (Stream<Path> fileStream = getDirStream(objectDir, OID_LEVEL2_PATTERN)) {
                    fileStream.forEach(file -> {
                        String hierarchy = hierarchyDir.getFileName().toString();
                        if (objectExists(file, hierarchy)) {
                            summary.incrementSkipped();
                        } else {
                            boolean success = uploadObject(file, hierarchy);
                            if (success) {
                                summary.incrementSuccessful();
                            } else {
                                summary.incrementFailed();
                            }
                        }
                    });
                }
            });
        }

        return summary;
    }

    /**
     * Convert base64 SHA256 checksum to hex. We always want to use the hex encoding for any user logging since
     * the file names of LFS objects and associated LFS REST API use this encoding.
     */
    private static String base64toHex(String base64) {
        return Hex.encodeHexString(decodeBase64(base64));
    }

    /**
     * Convert an OID (which is a SHA256 checksum represented in hex) to base64 which is what AWS S3
     * deals with.
     */
    private static String hexToBase64(String hex) {
        try {
            return encodeBase64String(Hex.decodeHex(hex));
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructs an LFS object ID from the given object path.
     */
    private static String pathToOid(Path objectPath) {
        int pathNameCount = objectPath.getNameCount();
        return String.valueOf(objectPath.subpath(pathNameCount - 2, pathNameCount - 1))
                + objectPath.subpath(pathNameCount - 1, pathNameCount);
    }

    /**
     * Constructs a key for an S3 object from the given hierarchy and object ID.
     */
    private static String toKey(String hierarchy, String oid) {
        return BUCKET_KEY_PREFIX + "/" + hierarchy + "/" + oid;
    }
}
