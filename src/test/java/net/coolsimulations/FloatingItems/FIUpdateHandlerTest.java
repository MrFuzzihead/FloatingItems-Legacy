package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Covers the GitHub release check, the opt-out paths and every result branch without touching GitHub.
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

    @Test
    public void currentVersionDoesNotFlagAnUpdate() {
        FIUpdateHandler.processUpdateData(FIReference.VERSION, "release notes");

        assertFalse(FIUpdateHandler.isOld);
        assertNull(FIUpdateHandler.updateInfo);
        assertNull(FIUpdateHandler.updateVersionInfo);
    }

    @Test
    public void versionPrefixIsIgnored() {
        FIUpdateHandler.processUpdateData("v" + FIReference.VERSION.replaceFirst("^[vV]", ""), null);

        assertFalse(FIUpdateHandler.isOld);
        assertNull(FIUpdateHandler.updateInfo);
    }

    @Test
    public void olderVersionBuildsTheWarningAndTheReleaseNotes() {
        FIUpdateHandler.processUpdateData("0.0.0-test", "release notes");

        assertTrue(FIUpdateHandler.isOld);
        assertNotNull(FIUpdateHandler.updateInfo);
        assertNotNull(FIUpdateHandler.updateVersionInfo);
    }

    @Test
    public void missingReleaseNotesStillBuildsTheVersionWarning() {
        FIUpdateHandler.processUpdateData("0.0.0-test", null);

        assertTrue(FIUpdateHandler.isOld);
        assertNotNull(FIUpdateHandler.updateInfo);
        assertNull(FIUpdateHandler.updateVersionInfo);
    }

    @Test
    public void endedVersionBuildsTheEndedMessage() {
        FIUpdateHandler.processUpdateData("ended", null);

        assertTrue(FIUpdateHandler.isOld);
        assertNotNull(FIUpdateHandler.updateInfo);
        assertNull(FIUpdateHandler.updateVersionInfo);
    }

    @Test
    public void nullOrEmptyVersionLeavesTheStateEmpty() {
        FIUpdateHandler.processUpdateData(null, "notes");
        assertFalse(FIUpdateHandler.isOld);
        assertNull(FIUpdateHandler.updateInfo);

        FIUpdateHandler.processUpdateData("", "notes");
        assertFalse(FIUpdateHandler.isOld);
        assertNull(FIUpdateHandler.updateInfo);
    }

    @Test
    public void latestReleaseTagIsReadFromTheRedirect() throws Exception {
        HttpServer server = server("/releases/latest", redirect("/releases/tag/v1.2.3"));

        try {
            assertEquals("v1.2.3", FIUpdateHandler.readLatestReleaseTag(url(server, "/releases/latest")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void releaseTagIsUrlDecoded() throws Exception {
        HttpServer server = server("/releases/latest", redirect("/releases/tag/v1.2.3%2Bbuild.7"));

        try {
            assertEquals("v1.2.3+build.7", FIUpdateHandler.readLatestReleaseTag(url(server, "/releases/latest")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aNonTagRedirectIsRejected() throws Exception {
        HttpServer server = server("/releases/latest", redirect("/releases/latest"));

        try {
            assertNull(FIUpdateHandler.readLatestReleaseTag(url(server, "/releases/latest")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aNonRedirectResponseIsRejected() throws Exception {
        HttpServer server = server("/releases/latest", status(200));

        try {
            assertNull(FIUpdateHandler.readLatestReleaseTag(url(server, "/releases/latest")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void anErrorResponseIsRejected() throws Exception {
        HttpServer server = server("/releases/latest", status(404));

        try {
            assertNull(FIUpdateHandler.readLatestReleaseTag(url(server, "/releases/latest")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aNullLocationIsRejected() {
        assertNull(FIUpdateHandler.readLatestReleaseTag(null));
    }

    private static HttpServer server(String context, HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(context, handler);
        server.start();
        return server;
    }

    private static HttpHandler redirect(String location) {
        return new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders()
                    .add("Location", location);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        };
    }

    private static HttpHandler status(int code) {
        return new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(code, -1);
                exchange.close();
            }
        };
    }

    private static String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress()
            .getPort() + path;
    }
}
