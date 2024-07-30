# Bitbucket Data Center - Git LFS S3 Store Migration Tool

A tool for migrating LFS objects between a Bitbucket Data Center's shared-home filesystem and AWS S3.

[![Atlassian license](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

## Building

```
git clone https://github.com/atlassian-labs/bitbucket-lfs-s3-migration-tool
cd bitbucket-lfs-s3-migration-tool
mvn package
```
You can find the generated JAR file under the `/target` directory. This JAR will need to be installed to one
of the Bitbucket Data Center nodes to run.

## Running

Example `config.properties` file:

```
bitbucket.home=/var/atlassian/application-data/bitbucket
s3.bucket=bitbucket-object-store
s3.region=ap-southeast-2
s3.access-key=<access key>
s3.secret-key=<access key>
s3.endpoint-override=<url> # optional; when omitted the S3 endpoint will be configured automatically
```
You can then run the migration tool directly from the node using the following command:

```
java -jar bitbucket-lfs-s3-migration-tool-1.0.0-SNAPSHOT-jar-with-dependencies.jar config.properties
```

### Reverse migration

In order to perform a migration from the S3 LFS object store back to the shared-home filesystem, simply add the
following property to the configuration properties file:

```
reverse-migration=true # optional; when omitted defaults to false
```

## Contributions

Contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

Copyright (c) 2024 Atlassian US., Inc.
Apache 2.0 licensed, see [LICENSE](LICENSE) file.

<br/>

[![With â¤ï¸ from Atlassian](https://raw.githubusercontent.com/atlassian-internal/oss-assets/master/banner-cheers.png)](https://www.atlassian.com)