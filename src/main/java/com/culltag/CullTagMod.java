package com.culltag;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CullTag — Server-side nametag culling.
 * Vanilla renders player nametags through walls; this mod hides them when LOS is blocked.
 */
public final class CullTagMod implements DedicatedServerModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("culltag");

    @Override
    public void onInitializeServer() {
        CullTagConfig.load(LOGGER);

        ServerTickEvents.END_SERVER_TICK.register(server ->
                LineOfSightEngine.tick(server.getPlayerList().getPlayers()));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CullTagConfig.reload(LOGGER);
            CrouchHider.onServerStarted(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CrouchHider.onPlayerJoin(handler.player));

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> CullTagCommand.register(dispatcher));

        LOGGER.info("[CullTag] Initialised");
    }
}
