# Bitbucket Data Center Git LFS S3 Store Migration Tool

## Running:

Example `config.properties` file

```
bitbucket.home=/var/atlassian/application-data/bitbucket
s3.bucket=bitbucket-git-lfs
s3.region=ap-southeast-2
s3.access-key=<access key>
s3.secret-key=<access key>
```

```
java -jar bitbucket-lfs-s3-migration-tool-1.0.0-SNAPSHOT-jar-with-dependencies.jar config.properties
```

## Building

```
git clone https://...
cd bitbucket-lfs-s3-migration-tool
mvn package
```