package net.coolsimulations.FloatingItems;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FIUpdateHandler {

    private static final Logger LOGGER = LogManager.getLogger(FIReference.MOD_ID);

    private static final String RELEASES_URL = "https://github.com/MrFuzzihead/FloatingItems-Legacy/releases/latest";
    private static final String MOD_URL = RELEASES_URL;
    private static final int TIMEOUT_MILLIS = 5000;

    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);

    public static volatile boolean isOld = false;
    public static volatile ChatComponentTranslation updateInfo = null;
    public static volatile ChatComponentText updateVersionInfo = null;

    public static void init() {

        // The update checker is a best-effort service. The config switch disables the request as well as the
        // message, and automation can skip it without touching the persisted config.
        if (Boolean.getBoolean("floatingitems.skipUpdateCheck") || FIConfig.disableUpdateCheck) {
            return;
        }
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        // Never block preInit on a remote host. The login handler checks for null messages, so a slow or failed
        // request simply means no notification for that session.
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                checkForUpdates();
            }
        }, "FloatingItems-UpdateCheck");
        worker.setDaemon(true);
        worker.start();
    }

    private static void checkForUpdates() {

        if (FIConfig.disableUpdateCheck || Boolean.getBoolean("floatingitems.skipUpdateCheck")) {
            return;
        }

        try {
            String latestVersion = readLatestReleaseTag(RELEASES_URL);
            if (latestVersion == null || latestVersion.isEmpty()) {
                return;
            }

            processUpdateData(latestVersion, null);
        } catch (Throwable t) {
            LOGGER.warn("Unexpected update-check failure", t);
        }
    }

    /**
     * Applies one version/info pair. Package-private so the unit suite can exercise every branch without a network
     * request or a running game.
     *
     * @param latestVersion     the version string returned by the release checker
     * @param latestVersionInfo the optional release-note line
     */
    static void processUpdateData(String latestVersion, String latestVersionInfo) {

        isOld = false;
        updateInfo = null;
        updateVersionInfo = null;

        latestVersion = normalizeVersion(latestVersion);
        if (latestVersion == null || latestVersion.isEmpty()) {
            return;
        }

        if ("ended".equals(latestVersion)) {
            isOld = true;
            updateInfo = buildEndedMessage();
            return;
        }

        if (!isNewerVersion(latestVersion, FIReference.VERSION)) {
            return;
        }

        isOld = true;
        updateInfo = buildAvailableMessage(latestVersion);
        if (latestVersionInfo != null && !latestVersionInfo.isEmpty()) {
            updateVersionInfo = buildVersionInfoMessage(latestVersionInfo);
        }
    }

    private static String normalizeVersion(String version) {

        if (version == null) {
            return null;
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    static boolean isNewerVersion(String latest, String current) {

        String latestNormalized = normalizeVersion(latest);
        String currentNormalized = normalizeVersion(current);
        if (latestNormalized == null || currentNormalized == null) {
            return false;
        }
        if (latestNormalized.equals(currentNormalized)) {
            return false;
        }

        String[] latestParts = splitVersion(latestNormalized);
        String[] currentParts = splitVersion(currentNormalized);
        int coreComparison = compareCore(latestParts[0], currentParts[0]);
        if (coreComparison == Integer.MIN_VALUE) {
            // A non-numeric development version cannot be ordered; fall back to "different means newer".
            return true;
        }
        if (coreComparison != 0) {
            return coreComparison > 0;
        }

        String latestPre = latestParts[1];
        String currentPre = currentParts[1];
        if (latestPre == null && currentPre == null) {
            return false;
        }
        if (latestPre == null) {
            return true;
        }
        if (currentPre == null) {
            return false;
        }
        return comparePreRelease(latestPre, currentPre) > 0;
    }

    private static String[] splitVersion(String version) {

        int plus = version.indexOf('+');
        if (plus >= 0) {
            version = version.substring(0, plus);
        }
        int dash = version.indexOf('-');
        if (dash < 0) {
            return new String[] { version, null };
        }
        return new String[] { version.substring(0, dash), version.substring(dash + 1) };
    }

    private static int compareCore(String latest, String current) {

        if (latest.equals(current)) {
            return 0;
        }

        String[] latestSegments = latest.split("\\.");
        String[] currentSegments = current.split("\\.");
        int length = Math.max(latestSegments.length, currentSegments.length);
        for (int i = 0; i < length; i++) {
            Integer latestPart = i < latestSegments.length ? parseNumber(latestSegments[i]) : Integer.valueOf(0);
            Integer currentPart = i < currentSegments.length ? parseNumber(currentSegments[i]) : Integer.valueOf(0);
            if (latestPart == null || currentPart == null) {
                return Integer.MIN_VALUE;
            }
            int comparison = latestPart.compareTo(currentPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int comparePreRelease(String latest, String current) {

        String[] latestSegments = latest.split("\\.");
        String[] currentSegments = current.split("\\.");
        int length = Math.max(latestSegments.length, currentSegments.length);
        for (int i = 0; i < length; i++) {
            String latestPart = i < latestSegments.length ? latestSegments[i] : null;
            String currentPart = i < currentSegments.length ? currentSegments[i] : null;
            if (latestPart == null) {
                return -1;
            }
            if (currentPart == null) {
                return 1;
            }

            Integer latestNumber = parseNumber(latestPart);
            Integer currentNumber = parseNumber(currentPart);
            if (latestNumber != null && currentNumber != null) {
                int comparison = latestNumber.compareTo(currentNumber);
                if (comparison != 0) {
                    return comparison;
                }
            } else if (latestNumber != null) {
                return -1;
            } else if (currentNumber != null) {
                return 1;
            } else {
                int comparison = latestPart.compareTo(currentPart);
                if (comparison != 0) {
                    return comparison;
                }
            }
        }
        return 0;
    }

    private static Integer parseNumber(String value) {

        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ChatComponentTranslation buildEndedMessage() {

        ChatComponentText mod = new ChatComponentText(FIReference.MOD_NAME);
        mod.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        ChatComponentText mcVersion = new ChatComponentText(MinecraftForge.MC_VERSION);
        mcVersion.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        return styleUpdateMessage(
            new ChatComponentTranslation(
                FILang.getTranslations("floatingitems.update.display3"),
                new Object[] { mod, mcVersion }));
    }

    private static ChatComponentTranslation buildAvailableMessage(String latestVersion) {

        ChatComponentText mod = new ChatComponentText(FIReference.MOD_NAME);
        mod.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        ChatComponentText version = new ChatComponentText(latestVersion);
        version.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        return styleUpdateMessage(
            new ChatComponentTranslation(
                FILang.getTranslations("floatingitems.update.display1"),
                new Object[] { mod, version }));
    }

    private static ChatComponentTranslation styleUpdateMessage(ChatComponentTranslation message) {

        message.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        addUpdateLink(message);
        return message;
    }

    private static ChatComponentText buildVersionInfoMessage(String latestVersionInfo) {

        ChatComponentText message = new ChatComponentText(latestVersionInfo);
        message.getChatStyle()
            .setColor(EnumChatFormatting.DARK_AQUA);
        message.getChatStyle()
            .setBold(true);
        addUpdateLink(message);
        return message;
    }

    private static void addUpdateLink(IChatComponent message) {

        message.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText(FILang.getTranslations("floatingitems.update.display2"))));
        message.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, MOD_URL));
    }

    /**
     * Resolves the latest GitHub release tag from the {@code /releases/latest} redirect. Package-private so the
     * unit suite can exercise it against a local HTTP server without touching GitHub.
     *
     * @param location the absolute {@code releases/latest} URL
     * @return the decoded tag name, or {@code null} if the request failed, was not a redirect, or had no tag
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
