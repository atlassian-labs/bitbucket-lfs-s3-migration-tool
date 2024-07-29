# Bitbucket Data Center Git LFS S3 Store Migration Tool

## About

This tool assists in migrating LFS objects that reside on an NFS server of a Bitbucket Data Center instance to S3. 
The LFS objects are uploaded to the provided S3 bucket and no objects are deleted from the NFS server. The same applies 
for reversing a migration; the LFS objects are downloaded from the S3 bucket back to the NFS server and no objects are deleted 
from the S3 bucket.

## Building

```
git clone https://...
cd bitbucket-lfs-s3-migration-tool
mvn package
```
You can find the generated jar under the `/target` directory.

## Running

Example `config.properties` file:

```
bitbucket.home=/var/atlassian/application-data/bitbucket
s3.bucket=bitbucket-git-lfs
s3.region=ap-southeast-2
s3.access-key=<access key>
s3.secret-key=<access key>
s3.endpoint-override=<url> # optional; when omitted the S3 endpoint will be configured automatically
```
You can then run the migration tool using the following command:

```
java -jar bitbucket-lfs-s3-migration-tool-1.0.0-SNAPSHOT-jar-with-dependencies.jar config.properties
```

### Reverse migration

In order to perform a migration from the S3 LFS object store back to the filesystem, simply add the following property 
to your configuration properties file:

```
reverse-migration=true # optional; when omitted defaults to false
```