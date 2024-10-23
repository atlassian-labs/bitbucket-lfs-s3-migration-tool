package com.atlassian.bitbucket.tool.lfs.s3;

import java.nio.file.Path;

/**
 * Summarizes the results of {@link S3TransferManager#upload(Path)}'s and {@link S3TransferManager#download(Path)}'s.
 */
public class TransferSummary {

    private long failed;
    private long skipped;
    private long successful;

    public void add(TransferSummary summary) {
        this.failed += summary.failed;
        this.skipped += summary.skipped;
        this.successful += summary.successful;
    }

    public long getFailed() {
        return failed;
    }

    public long getSkipped() {
        return skipped;
    }

    public long getSuccessful() {
        return successful;
    }

    public void incrementFailed() {
        failed++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void incrementSuccessful() {
        successful++;
    }

    @Override
    public String toString() {
        return String.format("- Successful: %d%n"
                + "- Skipped (already exists): %d%n"
                + "- Failed: %d",
                successful, skipped, failed);
    }
}
