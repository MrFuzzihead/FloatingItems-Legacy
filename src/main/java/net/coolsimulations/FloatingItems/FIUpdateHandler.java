package net.coolsimulations.FloatingItems;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Level;

import cpw.mods.fml.relauncher.FMLRelaunchLog;

public class FIUpdateHandler {

    private static final String VERSION_URL = "https://coolsimulations.net/mcmods/floatingitems/versionchecker17.txt";
    private static final String INFO_URL = "https://coolsimulations.net/mcmods/floatingitems/updateinfo17.txt";
    private static final String MOD_URL = "https://curseforge.com/minecraft/mc-mods/floatingitems";
    private static final int TIMEOUT_MILLIS = 5000;

    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);

    public static volatile boolean isOld = false;
    public static volatile ChatComponentTranslation updateInfo = null;
    public static volatile ChatComponentText updateVersionInfo = null;

    public static void init() {

        // The update checker is a best-effort service. Respect the existing config switch, and allow automation
        // (unit/CI game tests) to skip the blocking network calls before a world exists.
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

        try {
            String latestVersion = readFirstLine(VERSION_URL);
            if (latestVersion == null || latestVersion.isEmpty()) {
                return;
            }

            String latestVersionInfo = readFirstLine(INFO_URL);
            processUpdateData(latestVersion, latestVersionInfo);
        } catch (Throwable t) {
            FMLRelaunchLog.log(FIReference.MOD_NAME, Level.WARN, t, "Unexpected update-check failure");
        }
    }

    /**
     * Applies one version/info pair. Package-private so the unit suite can exercise every branch without a network
     * request or a running game.
     *
     * @param latestVersion     the version string returned by the version checker
     * @param latestVersionInfo the optional release-note line
     */
    static void processUpdateData(String latestVersion, String latestVersionInfo) {

        isOld = false;
        updateInfo = null;
        updateVersionInfo = null;

        if (latestVersion == null || latestVersion.isEmpty()) {
            return;
        }

        if ("ended".equals(latestVersion)) {
            isOld = true;
            updateInfo = buildEndedMessage();
            return;
        }

        if (latestVersion.equals(FIReference.VERSION)) {
            return;
        }

        isOld = true;
        updateInfo = buildAvailableMessage(latestVersion);
        if (latestVersionInfo != null && !latestVersionInfo.isEmpty()) {
            updateVersionInfo = buildVersionInfoMessage(latestVersionInfo);
        }
    }

    private static ChatComponentTranslation buildEndedMessage() {

        ChatComponentText mod = new ChatComponentText(FIReference.MOD_NAME);
        mod.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        ChatComponentText mcVersion = new ChatComponentText(MinecraftForge.MC_VERSION);
        mcVersion.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        ChatComponentTranslation message = new ChatComponentTranslation(
            FILang.getTranslations("floatingitems.update.display3"),
            new Object[] { mod, mcVersion });
        message.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        addUpdateLink(message);
        return message;
    }

    private static ChatComponentTranslation buildAvailableMessage(String latestVersion) {

        ChatComponentText mod = new ChatComponentText(FIReference.MOD_NAME);
        mod.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        ChatComponentText version = new ChatComponentText(latestVersion);
        version.getChatStyle()
            .setColor(EnumChatFormatting.BLUE);

        ChatComponentTranslation message = new ChatComponentTranslation(
            FILang.getTranslations("floatingitems.update.display1"),
            new Object[] { mod, version });
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
     * Reads the first line of a remote text file with bounded connect/read timeouts. Package-private so the unit
     * suite can exercise it against a local HTTP server without touching the production URLs.
     *
     * @param location the absolute URL to read
     * @return the trimmed first line, or {@code null} if the request failed or returned an empty body
     */
    static String readFirstLine(String location) {

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
            connection.setRequestProperty("User-Agent", "FloatingItems/" + FIReference.VERSION);

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null ? null : line.trim();
            }
        } catch (Exception e) {
            FMLRelaunchLog
                .log(FIReference.MOD_NAME, Level.WARN, "Could not read update data from " + location + ": " + e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
