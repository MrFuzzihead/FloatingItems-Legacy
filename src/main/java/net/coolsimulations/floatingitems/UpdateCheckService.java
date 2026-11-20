package net.coolsimulations.floatingitems;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Asynchronous GitHub release check. Network access is bounded and all failures are represented as
 * {@link UpdateCheckResult#NONE}.
 */
final class UpdateCheckService {

    private static final Logger LOGGER = LogManager.getLogger(FIReference.MOD_ID);
    private static final String RELEASES_URL = "https://github.com/MrFuzzihead/FloatingItems-Legacy/releases/latest";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);

    interface Listener {

        void onResult(UpdateCheckResult result);
    }

    private UpdateCheckService() {}

    /**
     * Starts one daemon check. Repeated calls are ignored.
     *
     * @param listener the result callback
     */
    static void checkAsync(Listener listener) {
        if (Boolean.getBoolean("floatingitems.skipUpdateCheck") || FIConfig.disableUpdateCheck) {
            return;
        }
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                UpdateCheckResult result = UpdateCheckResult.NONE;
                try {
                    result = fetch();
                } catch (Throwable t) {
                    LOGGER.warn("Unexpected update-check failure", t);
                }
                listener.onResult(result);
            }
        }, "FloatingItems-UpdateCheck");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Performs one synchronous check. Package-private for tests.
     *
     * @return the result, or NONE
     */
    static UpdateCheckResult fetch() {
        if (FIConfig.disableUpdateCheck || Boolean.getBoolean("floatingitems.skipUpdateCheck")) {
            return UpdateCheckResult.NONE;
        }
        String latestVersion = readLatestReleaseTag(RELEASES_URL);
        return latestVersion == null || latestVersion.isEmpty() ? UpdateCheckResult.NONE
            : new UpdateCheckResult(latestVersion, null);
    }

    /**
     * Resolves the latest GitHub release tag from the {@code /releases/latest} redirect.
     *
     * @param location the absolute releases/latest URL
     * @return the decoded tag, or null
     */
    static String readLatestReleaseTag(String location) {
        if (location == null) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(location);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "FloatingItems/" + FIReference.VERSION);
            connection.setRequestProperty("Accept", "text/html");

            int responseCode = connection.getResponseCode();
            if (responseCode < 300 || responseCode >= 400) {
                return null;
            }

            String redirect = connection.getHeaderField("Location");
            if (redirect == null || redirect.isEmpty()) {
                return null;
            }

            return releaseTag(new URL(url, redirect));
        } catch (Exception e) {
            LOGGER.warn("Could not read the latest release from {}", location, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String releaseTag(URL url) {
        String path = url.getPath();
        String marker = "/releases/tag/";
        int start = path.indexOf(marker);
        if (start < 0) {
            return null;
        }

        String tag = path.substring(start + marker.length());
        int slash = tag.indexOf('/');
        if (slash >= 0) {
            tag = tag.substring(0, slash);
        }
        if (tag.isEmpty()) {
            return null;
        }

        try {
            return URLDecoder.decode(tag, "UTF-8")
                .trim();
        } catch (Exception e) {
            return null;
        }
    }
}
