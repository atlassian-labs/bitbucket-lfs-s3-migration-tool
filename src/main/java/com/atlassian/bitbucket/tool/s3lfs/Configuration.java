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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Configuration {

    private static final String PROP_BITBUCKET_HOME = "bitbucket.home";
    private static final String PROP_S3_BUCKET = "s3.bucket";
    private static final String PROP_S3_REGION = "s3.region";
    private static final String PROP_S3_ACCESS_KEY = "s3.access-key";
    private static final String PROP_S3_SECRET_KET = "s3.secret-key";

    private final String homeDir;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;

    public Configuration(String configFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(configFile))) {
            Properties props = new Properties();
            props.load(inputStream);

            homeDir = props.getProperty(PROP_BITBUCKET_HOME);
            bucket = props.getProperty(PROP_S3_BUCKET);
            region = props.getProperty(PROP_S3_REGION);
            accessKey = props.getProperty(PROP_S3_ACCESS_KEY);
            secretKey = props.getProperty(PROP_S3_SECRET_KET);
        }
    }

    public String getBitbucketHomeDir() {
        return homeDir;
    }

    public String getS3Bucket() {
        return bucket;
    }

    public String getS3Region() {
        return region;
    }

    public String getS3AccessKey() {
        return accessKey;
    }

    public String getS3SecretKey() {
        return secretKey;
    }
}
