package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Covers the update checker's opt-out and HTTP helper paths without touching the production update URLs.
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
    public void readFirstLineTrimsWhitespaceAndUsesUtf8() throws Exception {
        HttpServer server = startServer("/version", new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                byte[] body = "  1.2.3  \nsecond line\n".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });

        try {
            assertEquals("1.2.3", FIUpdateHandler.readFirstLine(url(server, "/version")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void readFirstLineReturnsNullForAnEmptyBody() throws Exception {
        HttpServer server = startServer("/empty", new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });

        try {
            assertNull(FIUpdateHandler.readFirstLine(url(server, "/empty")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void readFirstLineReturnsNullForAnErrorResponse() throws Exception {
        HttpServer server = startServer("/missing", new HttpHandler() {

            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });

        try {
            assertNull(FIUpdateHandler.readFirstLine(url(server, "/missing")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void readFirstLineReturnsNullForANullLocation() {
        assertNull(FIUpdateHandler.readFirstLine(null));
    }

    private static HttpServer startServer(String context, HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(context, handler);
        server.start();
        return server;
    }

    private static String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress()
            .getPort() + path;
    }
}
