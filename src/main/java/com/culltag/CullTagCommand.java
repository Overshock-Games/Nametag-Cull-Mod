package com.culltag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class CullTagCommand {

    private CullTagCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("culltag")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("enable")
                    .executes(CullTagCommand::enable))
                .then(Commands.literal("disable")
                    .executes(CullTagCommand::disable))
                .then(Commands.literal("reload")
                    .executes(CullTagCommand::reload))
                .then(Commands.literal("stats")
                    .executes(CullTagCommand::stats))
        );
    }

    private static int enable(CommandContext<CommandSourceStack> ctx) {
        CullTagConfig.enabled = true;
        CullTagConfig.save(CullTagMod.LOGGER);
        ctx.getSource().sendSuccess(() -> Component.literal("[CullTag] Enabled."), true);
        return 1;
    }

    private static int disable(CommandContext<CommandSourceStack> ctx) {
        CullTagConfig.enabled = false;
        CullTagConfig.save(CullTagMod.LOGGER);
        NametagManager.restoreAll(
                ctx.getSource().getServer().getPlayerList().getPlayers());
        ctx.getSource().sendSuccess(() -> Component.literal("[CullTag] Disabled — all nametags restored."), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        boolean wasEnabled = CullTagConfig.enabled;
        CullTagConfig.reload(CullTagMod.LOGGER);
        if (wasEnabled && !CullTagConfig.enabled) {
            NametagManager.restoreAll(
                    ctx.getSource().getServer().getPlayerList().getPlayers());
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CullTag] Config reloaded: enabled=" + CullTagConfig.enabled
                + ", max_distance=" + CullTagConfig.maxDistance
                + ", check_interval_ticks=" + CullTagConfig.checkIntervalTicks
                + ", crouch_hides_nametag=" + CullTagConfig.crouchHidesNametag), true);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        LineOfSightEngine.PerfStats s = LineOfSightEngine.getStats();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CullTag] LOS sweeps: " + s.totalSweeps()
                + " | raycasts: " + s.totalRaycasts()
                + " | last sweep: " + String.format("%.3f ms", s.lastSweepMs())
                + " | avg sweep: "  + String.format("%.3f ms", s.avgSweepMs())), false);
        return 1;
    }
}
