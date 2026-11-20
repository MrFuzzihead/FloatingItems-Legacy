package net.coolsimulations.FloatingItems;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Owns the update result state and turns {@link UpdateCheckResult} values into chat components.
 */
public final class FIUpdateHandler {

    private static final String MOD_URL = "https://github.com/MrFuzzihead/FloatingItems-Legacy/releases/latest";

    public static volatile boolean isOld = false;
    public static volatile IChatComponent updateInfo = null;
    public static volatile ChatComponentText updateVersionInfo = null;

    private FIUpdateHandler() {}

    public static void init() {
        UpdateCheckService.checkAsync(new UpdateCheckService.Listener() {

            @Override
            public void onResult(UpdateCheckResult result) {
                processResult(result);
            }
        });
    }

    static void processUpdateData(String latestVersion, String latestVersionInfo) {
        processResult(new UpdateCheckResult(latestVersion, latestVersionInfo));
    }

    static void processResult(UpdateCheckResult result) {
        isOld = false;
        updateInfo = null;
        updateVersionInfo = null;

        if (result == null || !result.isPresent()) {
            return;
        }

        String latestVersion = normalizeVersion(result.getLatestVersion());
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
        String releaseInfo = result.getReleaseInfo();
        if (releaseInfo != null && !releaseInfo.isEmpty()) {
            updateVersionInfo = buildVersionInfoMessage(releaseInfo);
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

    private static IChatComponent buildEndedMessage() {
        ChatComponentText mod = styledText(FIReference.MOD_NAME, EnumChatFormatting.BLUE);
        ChatComponentText mcVersion = styledText(
            net.minecraftforge.common.MinecraftForge.MC_VERSION,
            EnumChatFormatting.BLUE);
        return styleUpdateMessage(buildLocalizedMessage("floatingitems.update.display3", mod, mcVersion));
    }

    private static IChatComponent buildAvailableMessage(String latestVersion) {
        ChatComponentText mod = styledText(FIReference.MOD_NAME, EnumChatFormatting.BLUE);
        ChatComponentText version = styledText(latestVersion, EnumChatFormatting.BLUE);
        return styleUpdateMessage(buildLocalizedMessage("floatingitems.update.display1", mod, version));
    }

    private static ChatComponentText styledText(String text, EnumChatFormatting color) {
        ChatComponentText component = new ChatComponentText(text);
        component.getChatStyle()
            .setColor(color);
        return component;
    }

    /**
     * Builds a component from a real translation key and sibling arguments. This avoids using a fully translated
     * sentence as the key passed to {@code ChatComponentTranslation}.
     */
    private static IChatComponent buildLocalizedMessage(String key, Object... arguments) {
        String format = FILang.getTranslations(key);
        ChatComponentText root = new ChatComponentText("");
        int argumentIndex = 0;
        int start = 0;

        while (true) {
            int placeholder = format.indexOf("%s", start);
            if (placeholder < 0) {
                root.appendText(format.substring(start));
                break;
            }

            root.appendText(format.substring(start, placeholder));
            if (argumentIndex < arguments.length) {
                Object argument = arguments[argumentIndex++];
                if (argument instanceof IChatComponent) {
                    root.appendSibling((IChatComponent) argument);
                } else {
                    root.appendText(String.valueOf(argument));
                }
            } else {
                root.appendText("%s");
            }
            start = placeholder + 2;
        }
        return root;
    }

    private static IChatComponent styleUpdateMessage(IChatComponent message) {
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

    static String readLatestReleaseTag(String location) {
        return UpdateCheckService.readLatestReleaseTag(location);
    }
}
