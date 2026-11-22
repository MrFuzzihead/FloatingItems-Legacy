package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpdateCheckServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    @After
    public void reset() {
        FIConfig.disableUpdateCheck = false;
        System.clearProperty("floatingitems.skipUpdateCheck");
    }

    @Test
    public void cacheRoundTripsVersionAndReleaseInfo() {
        File cache = cacheFile();
        UpdateCheckService.writeCache(cache, new UpdateCheckResult("9.9.9", "release notes"));

        UpdateCheckResult result = UpdateCheckService.readCache(cache);

        assertNotNull(result);
        assertEquals("9.9.9", result.getLatestVersion());
        assertEquals("release notes", result.getReleaseInfo());
    }

    @Test
    public void expiredCacheIsIgnored() {
        File cache = cacheFile();
        UpdateCheckService.writeCache(
            cache,
            new UpdateCheckResult("9.9.9", null),
            System.currentTimeMillis() - 7L * 60L * 60L * 1000L);

        assertNull(UpdateCheckService.readCache(cache));
    }

    @Test
    public void validCacheIsUsedByFetchWithoutNetworkAccess() {
        File cache = cacheFile();
        UpdateCheckService.writeCache(cache, new UpdateCheckResult("9.9.9", null));

        UpdateCheckResult result = UpdateCheckService.fetch(cache);

        assertNotNull(result);
        assertEquals("9.9.9", result.getLatestVersion());
    }

    @Test
    public void cacheForAnotherModVersionIsIgnored() throws Exception {
        File cache = cacheFile();
        Properties properties = new Properties();
        properties.setProperty("timestamp", Long.toString(System.currentTimeMillis()));
        properties.setProperty("modVersion", "some-other-version");
        properties.setProperty("latestVersion", "9.9.9");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(cache)) {
            properties.store(output, "test");
        }

        assertNull(UpdateCheckService.readCache(cache));
    }

    private File cacheFile() {
        return new File(temporaryFolder.getRoot(), "FloatingItems-update-cache.properties");
    }
}
