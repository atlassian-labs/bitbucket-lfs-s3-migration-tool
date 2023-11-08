/*
 * Copyright 2023 Atlassian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atlassian.bitbucket.tool.s3lfs;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.apache.commons.codec.binary.Base64.decodeBase64;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;

public class S3Uploader implements AutoCloseable {

    /**
     * A prefix added so Git LFS content is namespaced in the bucket, so it can coexist with other usages
     * of the same bucket. Bitbucket Data Center expects this prefix to be present.
     */
    private static final String BUCKET_KEY_PREFIX = "git-lfs";
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

    public S3Uploader(Configuration config) {
        bucket = config.getS3Bucket();
        AwsCredentialsProvider awsCredentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getS3AccessKey(), config.getS3SecretKey()));

        client = S3Client.builder()
                .region(Region.of(config.getS3Region()))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    public UploadSummary upload(Path hierarchyDir) {
        UploadSummary summary = new UploadSummary();

        // LFS objects are stored with structure <hierarchy dir>/<oid[0:2]>/<oid[2:63]>
        // where OID is the SHA256 checksum of the object.

        // Java is not smart enough to close streams that need to release file descriptors, so use try-with-resources
        try (Stream<Path> dirStream = Files.list(hierarchyDir)
                .filter(Files::isDirectory)
                .filter(path -> OID_LEVEL1_PATTERN.matcher(path.getFileName().toString()).matches())) {

            dirStream.forEach(dir -> {
                try (Stream<Path> fileStream = Files.list(dir)
                        .filter(Files::isRegularFile)
                        .filter(path -> OID_LEVEL2_PATTERN.matcher(path.getFileName().toString()).matches())) {

                    fileStream.forEach(file -> {
                        String hierarchy = hierarchyDir.getFileName().toString();
                        if (objectExists(file, hierarchy)) {
                            summary.incrementSkipped();
                        } else {
                            boolean success = uploadFile(file, hierarchy);
                            if (success) {
                                summary.incrementUploaded();
                            } else {
                                summary.incrementFailed();
                            }
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return summary;
    }

    @Override
    public void close() {
        client.close();
    }

    private boolean objectExists(Path objectPath, String hierarchy) {
        String oid = pathToOid(objectPath);
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(toKey(hierarchy, oid))
                .build();

        try {
            client.headObject(request);
        } catch (NoSuchKeyException ignored) {
            return false;
        }

        return true;
    }

    /**
     * @return {@code true} if the upload was successful, otherwise {@code false}.
     */
    private boolean uploadFile(Path objectPath, String hierarchy) {
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
     * Convert an OID (which is a SHA256 checksum represented in hex) to base64 which is what AWS S3
     * deals with.
     */
    private String hexToBase64(String hex) {
        try {
            return encodeBase64String(Hex.decodeHex(hex));
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert base64 SHA256 checksum to hex. We always want to use the hex encoding for any user logging since
     * the file names of LFS objects and associated LFS REST API use this encoding.
     */
    private String base64toHex(String base64) {
        return Hex.encodeHexString(decodeBase64(base64));
    }

    private String toKey(String hierarchy, String oid) {
        return BUCKET_KEY_PREFIX + "/" + hierarchy + "/" + oid;
    }

    private String pathToOid(Path objectPath) {
        int pathNameCount = objectPath.getNameCount();
        return String.valueOf(objectPath.subpath(pathNameCount - 2, pathNameCount - 1))
                + objectPath.subpath(pathNameCount - 1, pathNameCount);
    }
}
