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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The {@link #main main} entry point for the migration application.
 */
public class MigrationApp {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Must provide configuration properties file parameter");
            System.exit(1);
        }

        AppConfiguration config = getConfiguration(args[0]);

        Path homeDir = Paths.get(config.getBitbucketHomeDir());
        verifyDirectoryExists(homeDir, "Specified home directory");

        Path lfsDir = homeDir.resolve("shared/data/git-lfs/storage");
        verifyDirectoryExists(lfsDir, "LFS storage directory");

        if (config.isEndpointOverride()) {
            System.out.println("S3 endpoint override: " + config.getS3EndpointOverride());
        }
        System.out.println("S3 bucket: " + config.getS3Bucket());
        System.out.println("S3 region: " + config.getS3Region());

        S3TransferManager s3TransferManager = new S3TransferManager(config);
        TransferSummary overallSummary;

        if (!config.isReverseMigration()) {
            System.out.printf("%nBeginning migration of embedded LFS object store to S3...%n");
            overallSummary = s3TransferManager.upload(lfsDir);
        } else {
            System.out.printf("%nBeginning migration of S3 LFS objects to filesystem...%n");
            overallSummary = s3TransferManager.download(lfsDir);
        }

        System.out.printf("%nFinished.%n");
        System.out.printf("%nOverall summary:%n");
        System.out.println(overallSummary);
    }

    private static AppConfiguration getConfiguration(String configFile) {
        try {
            return new AppConfiguration(configFile);
        } catch (FileNotFoundException e) {
            System.err.println("Configuration file not found: " + configFile);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }

    private static void verifyDirectoryExists(Path dir, String description) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.out.printf("%n%s does not exist: %s%n", description, dir);
            System.exit(1);
        }
    }
}
