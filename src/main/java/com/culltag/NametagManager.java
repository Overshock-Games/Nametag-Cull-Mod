package com.culltag;

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pushes per-viewer nametag visibility overrides when LOS changes.
 *
 * <p>On LOS blocked: adds target ID to the viewer's hidden set (see
 * {@link NametagController}) and immediately sends a metadata packet that forces
 * the SNEAKING flag (0x02) on the target. Vanilla clients hide nametags for
 * sneaking players when LOS is obstructed, so this replicates that behaviour
 * for all obstructed players regardless of their actual pose.
 *
 * <p>While hidden, {@link com.culltag.mixin.ServerCommonPacketListenerImplMixin}
 * intercepts any subsequent metadata packet for that entity and preserves the
 * sneaking flag, preventing vanilla delta-syncs from clearing the override.
 *
 * <p>On LOS restored: removes from hidden set and sends the target's real FLAGS
 * byte so the client immediately corrects the pose.
 */
public final class NametagManager {

    private NametagManager() {}

    /** Force-restores nametag state for every (viewer, target) pair, regardless of
     *  whether the server thinks the target was being hidden. This matters after a
     *  hot-jar-swap: server-side {@code hiddenIds} is empty, but the client may still
     *  have force-sneak flags lingering from the previous binary. Clearing only the
     *  tracked set would miss those, so we blast every pair.
     *
     *  Returns the total number of restoration packets sent. */
    public static int restoreAll(List<ServerPlayer> players) {
        int totalSent = 0;
        for (ServerPlayer viewer : players) {
            NametagController ctrl = (NametagController) viewer.connection;
            ctrl.culltag_getHiddenEntityIds().clear();
            int sentForViewer = 0;
            for (ServerPlayer target : players) {
                if (target == viewer) continue;
                // Force-clear the 0x02 (sneaking) bit. If the target is genuinely sneaking
                // IRL, vanilla will reassert it on the next metadata tick — for a kill-switch
                // we want guaranteed nametag restoration now even if no prior override existed.
                byte restored = clearSneaking(getRealFlags(target));
                ctrl.culltag_sendDirect(buildFlagsPacket(target, restored));
                sentForViewer++;
            }
            if (sentForViewer > 0) {
                CullTagMod.LOGGER.info("[CullTag] Restored {} nametag(s) for viewer {}",
                        sentForViewer, viewer.getName().getString());
                totalSent += sentForViewer;
            }
        }
        CullTagMod.LOGGER.info("[CullTag] restoreAll complete: {} packet(s) across {} viewer(s)",
                totalSent, players.size());
        return totalSent;
    }

    /** Total entities currently being hidden across all viewers. For /culltag stats. */
    public static int countHidden(List<ServerPlayer> players) {
        int total = 0;
        for (ServerPlayer viewer : players) {
            NametagController ctrl = (NametagController) viewer.connection;
            total += ctrl.culltag_getHiddenEntityIds().size();
        }
        return total;
    }

    public static void onVisibilityChanged(ServerPlayer viewer, ServerPlayer target, boolean nowVisible) {
        NametagController ctrl = (NametagController) viewer.connection;
        Set<Integer> hiddenIds = ctrl.culltag_getHiddenEntityIds();

        if (nowVisible) {
            hiddenIds.remove(target.getId());
            ctrl.culltag_sendDirect(buildFlagsPacket(target, getRealFlags(target)));
        } else {
            hiddenIds.add(target.getId());
            ctrl.culltag_sendDirect(buildFlagsPacket(target, addSneaking(getRealFlags(target))));
        }
    }

    public static byte getRealFlags(Entity entity) {
        return entity.getEntityData().get(Entity.DATA_SHARED_FLAGS_ID);
    }

    public static byte addSneaking(byte flags) {
        return (byte) (flags | 0x02);
    }

    public static byte clearSneaking(byte flags) {
        return (byte) (flags & ~0x02);
    }

    public static ClientboundSetEntityDataPacket buildFlagsPacket(Entity entity, byte flagsValue) {
        List<SynchedEntityData.DataValue<?>> entries = List.of(
                new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flagsValue));
        return new ClientboundSetEntityDataPacket(entity.getId(), entries);
    }
}
