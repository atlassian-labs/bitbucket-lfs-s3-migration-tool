package com.atlassian.bitbucket.tool.s3lfs;

public class UploadSummary {

    private long failed;
    private long skipped;
    private long uploaded;

    public void add(UploadSummary summary) {
        failed = summary.failed;
        skipped = summary.skipped;
        uploaded = summary.uploaded;
    }

    public void incrementFailed() {
        failed++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void incrementUploaded() {
        uploaded++;
    }

    @Override
    public String toString() {
        return String.format("- Uploaded: %d%n"
                + "- Skipped (already exists): %d%n"
                + "- Upload errors: %d",
                uploaded, skipped, failed);
    }
}
