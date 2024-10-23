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

To run the integration tests Docker must be running as the test starts a LocalStack container. The tests can be
run with:
```
mvn verify
```

## Running

Example `config.properties` file:

```
bitbucket.home=/var/atlassian/application-data/bitbucket
s3.bucket=bitbucket-object-store
s3.region=ap-southeast-2
s3.access-key=<access key>
s3.secret-key=<secret key>
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

## What does the migration tool do?

The migration tool will copy all LFS objects from the shared-home filesystem to the S3 bucket. Details of the filesystem
and S3 bucket layout are described below. The migration tool does not modify any Bitbucket configuration, it purely
copied data.

### Shared-home filesystem layout

Bitbucket Data Center stores LFS objects in the shared-home filesystem in the directory
`$BITBUCKET_HOME/shared/data/git-lfs/storage`. Within this directory objects are stored using the file path
`<hierarchyId>/<sha256[0:1]>/sha256[2:63]`. The `hierarchyId` is a 20 character hexadecimal string that uniquely
identifies all repositories related by forking. Git LFS objects are  shared between forks to support "cheap" forking.
The SHA-256 hash mentioned above is the LFS "object identifier", often called the OID. It is the SHA-256 hash of the
LFS object content. Putting that all together, the full path to an LFS object in the shared home, given the hierarchyId
`1234567890abcdef1234` and OID of `0ba904eae8773b70c75333db4de2f3ac45a8ad4ddba1b242f0b3cfc199391dd8`, would be:
```
$BITBUCKET_HOME/shared/data/git-lfs/storage/1234567890abcdef1234/0b/a904eae8773b70c75333db4de2f3ac45a8ad4ddba1b242f0b3cfc199391dd8
```

### S3 bucket layout

When stored in S3 the LFS objects are stored with key `git-lfs/<hierarchyId>/<sha256[0:1]>/sha256[2:63]`. It may seem
unnecessary to break down the OID into the `[0:1]/[2:63]` format given S3 isn't a hierarchical filesystem. It is
important on many hierarchical filesystem implementations to avoid having too many files in a single directory, but
this is not necessary in S3. The reason for this layout is that the LFS/S3 storage implementation in Bitbucket is
built upon the FileStore API/SPI, which is a store agnostic API. So the Git LFS implementation doesn't really know what
store may be used, it may be an object store like S3 or may be something that is filesystem backed, or may be something
completely different.

So putting that all together, the migration tool will iterate over all LFS objects in the shared-home filesystem (for
example) an object stored at the following filesystem path:
```
$BITBUCKET_HOME/shared/data/git-lfs/storage/1234567890abcdef1234/0b/a904eae8773b70c75333db4de2f3ac45a8ad4ddba1b242f0b3cfc199391dd8
```
would be migrated to S3 with an object key:
```
git-lfs/1234567890abcdef1234/0b/a904eae8773b70c75333db4de2f3ac45a8ad4ddba1b242f0b3cfc199391dd8
```

## Contributions

Contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

Copyright (c) 2024 Atlassian US., Inc.
Apache 2.0 licensed, see [LICENSE](LICENSE) file.

<br/>

[![With â¤ï¸ from Atlassian](https://raw.githubusercontent.com/atlassian-internal/oss-assets/master/banner-cheers.png)](https://www.atlassian.com)