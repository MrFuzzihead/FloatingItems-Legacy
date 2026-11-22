package com.mrfuzzihead.floatingitems;

import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.mrfuzzihead.floatingitems.api.DebugCounters;

/**
 * Operator command for inspecting and reloading the effective FloatingItems configuration.
 */
public final class FloatingItemsCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "floatingitems";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/floatingitems <status|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sendStatus(sender);
            return;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            FIConfig.reload();
            sender.addChatMessage(new ChatComponentText("FloatingItems config reloaded."));
            return;
        }

        sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }

    private static void sendStatus(ICommandSender sender) {
        FIConfigSnapshot filter = FIConfig.blacklist;
        LiquidRules liquids = FIConfig.liquidRules;
        BuoyancySettings buoyancy = FIConfig.buoyancy;
        ScopeRules scopes = FIConfig.scopeRules;

        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    Locale.ROOT,
                    "FloatingItems: %s filter (%d item, %d registry, %d ore entries)",
                    FIConfig.filterMode.getId(),
                    filter.getItems()
                        .size(),
                    filter.getRegistryItems()
                        .size(),
                    filter.getOreDict()
                        .size())));
        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    Locale.ROOT,
                    "Liquid: %s (allow=%s, deny=%s)",
                    liquids.getPolicy()
                        .getId(),
                    liquids.getAllowlist(),
                    liquids.getDenylist())));
        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    Locale.ROOT,
                    "Scope: dimension=%s%s biome=%s%s mod=%s%s",
                    scopes.getDimensionMode()
                        .getId(),
                    scopes.getDimensions(),
                    scopes.getBiomeMode()
                        .getId(),
                    scopes.getBiomes(),
                    scopes.getModMode()
                        .getId(),
                    scopes.getModIds())));
        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    Locale.ROOT,
                    "Buoyancy: lift=%.4f damping=%.3f maxSpeed=%.3f surfaceOffset=%.3f bobbing=%s",
                    buoyancy.getLift(),
                    buoyancy.getHorizontalDamping(),
                    buoyancy.getMaxUpwardSpeed(),
                    buoyancy.getSurfaceOffset(),
                    buoyancy.isSurfaceBobbing())));
        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    Locale.ROOT,
                    "Debug counters: %s evaluated=%d floated=%d cacheHits=%d cacheMisses=%d",
                    DebugCounters.isEnabled() ? "enabled" : "disabled",
                    DebugCounters.getItemsEvaluated(),
                    DebugCounters.getItemsFloated(),
                    DebugCounters.getCacheHits(),
                    DebugCounters.getCacheMisses())));
    }
}
