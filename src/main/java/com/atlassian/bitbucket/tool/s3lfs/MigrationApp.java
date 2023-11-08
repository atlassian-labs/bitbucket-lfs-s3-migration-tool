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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrationApp {

    /**
     * Bitbucket Data Center's embedded LFS object store stores objects for multiple repositories in a collection
     * called a "hierarchy". Any repository related by forking has the same hierarchy ID and as such all LFS objects
     * for those repositories are stored in the same collection. A hierarchy ID is a 20 character lower-case
     * hexadecimal string.
     */
    private static final Pattern HIERARCHY_ID_PATTERN = Pattern.compile("[0-9a-f]{20}");

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Must provide configuration properties file parameter");
            System.exit(1);
        }

        Configuration config = null;
        try {
            config = new Configuration(args[0]);
        } catch (FileNotFoundException e) {
            System.err.println("Configuration file not found: " + args[0]);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
            System.exit(1);
        }

        Path homeDir = Paths.get(config.getBitbucketHomeDir());
        if (!Files.exists(homeDir) || !Files.isDirectory(homeDir)) {
            System.out.println("Specified home directory does not exist: " + homeDir);
            System.exit(1);
        }
        Path lfsDir = homeDir.resolve("shared/data/git-lfs/storage");
        if (!Files.exists(lfsDir) || !Files.isDirectory(lfsDir)) {
            System.out.println("LFS storage directory does not exist: " + lfsDir);
            System.exit(1);
        }

        System.out.println("Uploading to S3 bucket: " + config.getS3Bucket());
        System.out.println("Uploading to S3 region: " + config.getS3Region());

        UploadSummary overallSummary = new UploadSummary();
        try (S3Uploader uploader = new S3Uploader(config)) {
            List<Path> hierarchyDirs = getHierarchyDirs(lfsDir);
            int count = 0;
            for (Path dir : hierarchyDirs) {
                System.out.printf("%nProcessing hierarchy %s (%d of %d)%n",
                        dir.getFileName(), ++count, hierarchyDirs.size());
                UploadSummary summary = uploader.upload(dir);
                overallSummary.add(summary);
                System.out.println(summary);
            }

            System.out.printf("%nOverall summary:%n");
            System.out.println(overallSummary);
        } catch (IOException e) {
            System.err.println("Error: Upload failed: " + e.getMessage());
        }
    }

    private static List<Path> getHierarchyDirs(Path lfsDir) throws IOException {
        // Java is not smart enough to close streams that need to release file descriptors, so use try-with-resources
        try (Stream<Path> stream = Files.list(lfsDir)
                .filter(Files::isDirectory)
                .filter(path -> HIERARCHY_ID_PATTERN.matcher(path.getFileName().toString()).matches())) {
            return stream.collect(Collectors.toList());
        }
    }
}
