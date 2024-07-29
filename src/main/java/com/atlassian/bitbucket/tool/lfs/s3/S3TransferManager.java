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
package com.atlassian.bitbucket.tool.lfs.s3;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.atlassian.bitbucket.tool.lfs.s3.S3TransferManagerHelper.HIERARCHY_ID_PATTERN;
import static com.atlassian.bitbucket.tool.lfs.s3.S3TransferManagerHelper.getDirStream;

/**
 * Transfers files between the Bitbucket client and S3.
 */
public class S3TransferManager implements AutoCloseable {

    private final String bucket;
    private final S3Client client;
    private final S3TransferManagerHelper helper;

    public S3TransferManager(AppConfiguration config) {
        AwsCredentialsProvider awsCredentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getS3AccessKey(), config.getS3SecretKey()));

        bucket = config.getS3Bucket();
        client = S3Client.builder()
                .endpointOverride(config.isEndpointOverride() ? URI.create(config.getS3EndpointOverride()) : null)
                .region(Region.of(config.getS3Region()))
                .credentialsProvider(awsCredentialsProvider)
                .forcePathStyle(true)
                .build();
        helper = new S3TransferManagerHelper(bucket, client);
    }

    @Override
    public void close() {
        client.close();
    }

    /**
     * Downloads all LFS objects from S3 to the specified LFS object store directory.
     *
     * @param lfsDir the path of the embedded LFS object store directory.
     * @return a summary containing the overall number of successful, skipped and failed object downloads.
     */
    public TransferSummary download(Path lfsDir) {
        TransferSummary overallSummary = new TransferSummary();

        List<String> hierarchies = helper.getHierarchies();
        int processed = 0;
        for (String hierarchy : hierarchies) {
            System.out.printf("%nProcessing hierarchy %s (%d of %d)%n", hierarchy, ++processed, hierarchies.size());
            TransferSummary summary = helper.downloadHierarchy(hierarchy, lfsDir.resolve(hierarchy));
            overallSummary.add(summary);
            System.out.println(summary);
        }

        return overallSummary;
    }

    /**
     * Uploads all LFS objects from the specified LFS object store directory to S3.
     *
     * @param lfsDir the path of the embedded LFS object store directory.
     * @return a summary containing the overall number of successful, skipped and failed object uploads.
     */
    public TransferSummary upload(Path lfsDir) {
        TransferSummary overallSummary = new TransferSummary();

        // Java is not smart enough to close streams that need to release file descriptors
        // so use try-with-resources
        try (Stream<Path> lfsDirStream = getDirStream(lfsDir, HIERARCHY_ID_PATTERN)) {
            List<Path> hierarchyDirs = lfsDirStream.collect(Collectors.toList());
            if (hierarchyDirs.isEmpty()) {
                System.out.printf("%nEmbedded LFS object store is empty.%n");
                System.exit(0);
            }

            int processed = 0;
            for (Path dir : hierarchyDirs) {
                System.out.printf("%nProcessing hierarchy %s (%d of %d)%n", dir.getFileName(), ++processed, hierarchyDirs.size());
                TransferSummary summary = helper.uploadHierarchy(dir);
                overallSummary.add(summary);
                System.out.println(summary);
            }
        }

        return overallSummary;
    }
}
