package net.coolsimulations.floatingitems;

/**
 * Immutable result of one update-check attempt.
 */
final class UpdateCheckResult {

    static final UpdateCheckResult NONE = new UpdateCheckResult(null, null);

    private final String latestVersion;
    private final String releaseInfo;

    UpdateCheckResult(String latestVersion, String releaseInfo) {
        this.latestVersion = latestVersion;
        this.releaseInfo = releaseInfo;
    }

    boolean isPresent() {
        return latestVersion != null && !latestVersion.isEmpty();
    }

    String getLatestVersion() {
        return latestVersion;
    }

    String getReleaseInfo() {
        return releaseInfo;
    }
}
