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

    /** Restores all hidden nametags for every online player. Call when disabling the mod. */
    public static void restoreAll(List<ServerPlayer> players) {
        for (ServerPlayer viewer : players) {
            NametagController ctrl = (NametagController) viewer.connection;
            Set<Integer> hiddenIds = ctrl.culltag_getHiddenEntityIds();
            if (hiddenIds.isEmpty()) continue;
            List<Integer> toRestore = new ArrayList<>(hiddenIds);
            hiddenIds.clear();
            for (ServerPlayer target : players) {
                if (toRestore.contains(target.getId())) {
                    ctrl.culltag_sendDirect(buildFlagsPacket(target, getRealFlags(target)));
                }
            }
        }
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

    public static ClientboundSetEntityDataPacket buildFlagsPacket(Entity entity, byte flagsValue) {
        List<SynchedEntityData.DataValue<?>> entries = List.of(
                new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flagsValue));
        return new ClientboundSetEntityDataPacket(entity.getId(), entries);
    }
}
