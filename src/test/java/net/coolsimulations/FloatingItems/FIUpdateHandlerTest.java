package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Covers the update checker's opt-out paths. The HTTP path itself is hard-coded and is intentionally not exercised
 * by the unit suite; the important part for a server/CI run is that both switches return before any network call.
 */
public class FIUpdateHandlerTest {

    @Before
    @After
    public void reset() {
        System.clearProperty("floatingitems.skipUpdateCheck");
        FIConfig.disableUpdateCheck = false;
        FIUpdateHandler.isOld = false;
        FIUpdateHandler.updateInfo = null;
        FIUpdateHandler.updateVersionInfo = null;
    }

    @Test
    public void systemPropertySkipsTheNetworkPath() {
        System.setProperty("floatingitems.skipUpdateCheck", "true");

        FIUpdateHandler.init();

        assertFalse(FIUpdateHandler.isOld);
        assertNull(FIUpdateHandler.updateInfo);
        assertNull(FIUpdateHandler.updateVersionInfo);
    }

    @Test
    public void configSwitchSkipsTheNetworkPath() {
        FIConfig.disableUpdateCheck = true;

        FIUpdateHandler.init();

        assertFalse(FIUpdateHandler.isOld);
        assertNull(FIUpdateHandler.updateInfo);
        assertNull(FIUpdateHandler.updateVersionInfo);
    }
}
