package com.mrfuzzihead.floatingitems;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Properties;
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
    private static final long CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L;
    private static final String CACHE_FILE_NAME = "FloatingItems-update-cache.properties";
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
        return fetch(defaultCacheFile());
    }

    static UpdateCheckResult fetch(File cacheFile) {
        if (FIConfig.disableUpdateCheck || Boolean.getBoolean("floatingitems.skipUpdateCheck")) {
            return UpdateCheckResult.NONE;
        }

        UpdateCheckResult cached = readCache(cacheFile);
        if (cached != null) {
            return cached;
        }

        String latestVersion = readLatestReleaseTag(RELEASES_URL);
        UpdateCheckResult result = latestVersion == null || latestVersion.isEmpty() ? UpdateCheckResult.NONE
            : new UpdateCheckResult(latestVersion, null);
        if (result.isPresent()) {
            writeCache(cacheFile, result);
        }
        return result;
    }

    static UpdateCheckResult readCache(File cacheFile) {
        if (cacheFile == null || !cacheFile.isFile()) {
            return null;
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(cacheFile)) {
            properties.load(input);
            long timestamp = Long.parseLong(properties.getProperty("timestamp", "0"));
            long now = System.currentTimeMillis();
            if (timestamp <= 0 || now - timestamp > CACHE_TTL_MILLIS || timestamp > now) {
                return null;
            }
            if (!FIReference.VERSION.equals(properties.getProperty("modVersion"))) {
                return null;
            }
            String latestVersion = properties.getProperty("latestVersion");
            if (latestVersion == null || latestVersion.isEmpty()) {
                return null;
            }
            return new UpdateCheckResult(latestVersion, properties.getProperty("releaseInfo"));
        } catch (Exception e) {
            LOGGER.debug("Could not read update-check cache {}", cacheFile, e);
            return null;
        }
    }

    static void writeCache(File cacheFile, UpdateCheckResult result) {
        writeCache(cacheFile, result, System.currentTimeMillis());
    }

    static void writeCache(File cacheFile, UpdateCheckResult result, long timestamp) {
        if (cacheFile == null || result == null || !result.isPresent()) {
            return;
        }

        File parent = cacheFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Properties properties = new Properties();
        properties.setProperty("timestamp", Long.toString(timestamp));
        properties.setProperty("modVersion", FIReference.VERSION);
        properties.setProperty("latestVersion", result.getLatestVersion());
        if (result.getReleaseInfo() != null) {
            properties.setProperty("releaseInfo", result.getReleaseInfo());
        }

        try (FileOutputStream output = new FileOutputStream(cacheFile)) {
            properties.store(output, "FloatingItems update-check cache");
        } catch (Exception e) {
            LOGGER.debug("Could not write update-check cache {}", cacheFile, e);
        }
    }

    private static File defaultCacheFile() {
        if (FIConfig.config == null) {
            return null;
        }
        File configFile = FIConfig.config.getConfigFile();
        File parent = configFile == null ? null : configFile.getParentFile();
        return parent == null ? null : new File(parent, CACHE_FILE_NAME);
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
