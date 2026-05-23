package com.culltag;

import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-viewer nametag suppression via a scoreboard team with
 * {@code nametagVisibility=NEVER}.
 *
 * <p>The trick: scoreboard teams are normally global state, but team-membership
 * packets are clientbound — the server can lie to each client about who is on
 * which team. We create a real {@link PlayerTeam} ({@value #TEAM_NAME}) on the
 * server scoreboard but never add anyone to it server-side; instead, when we
 * want viewer V to stop seeing target T's nametag, we send V (and only V) a
 * packet adding T to the team. V's client then applies the team's
 * {@code NEVER} visibility rule to T's nametag.
 *
 * <p>Used by {@link LineOfSightEngine} for the crouch-hide feature, where the
 * force-sneak mechanism in {@link NametagManager} doesn't work (vanilla still
 * renders sneaking-player nametags at close range with clear LOS).
 *
 * <p>Caveat: if target T was already on a real team server-side, V's client
 * sees T as on {@value #TEAM_NAME} instead, which can disrupt team color
 * indicators. For PvP/SMP setups without server-managed teams this is a
 * non-issue.
 */
public final class CrouchHider {

    public static final String TEAM_NAME = "culltag_hidden";

    // viewer UUID → set of target usernames currently hidden from that viewer
    private static final Map<UUID, Set<String>> hidden = new ConcurrentHashMap<>();

    private static volatile PlayerTeam team;

    private CrouchHider() {}

    /** Create the hidden-nametags team on the server scoreboard if it doesn't
     *  already exist, and force its visibility rule. Idempotent. */
    public static void onServerStarted(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam existing = sb.getPlayerTeam(TEAM_NAME);
        team = (existing != null) ? existing : sb.addPlayerTeam(TEAM_NAME);
        team.setNameTagVisibility(Team.Visibility.NEVER);
    }

    /** Push the team-create packet to a freshly-joined viewer so their client
     *  knows the team exists before we start adding members to it. */
    public static void onPlayerJoin(ServerPlayer viewer) {
        if (team == null) return;
        viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }

    /** Set whether {@code target}'s nametag should be hidden from {@code viewer}.
     *  Sends the appropriate add/remove packet only on a state change. */
    public static void setHidden(ServerPlayer viewer, ServerPlayer target, boolean hide) {
        if (team == null) return;
        Set<String> set = hidden.computeIfAbsent(viewer.getUUID(), k -> ConcurrentHashMap.newKeySet());
        String name = target.getName().getString();
        boolean wasHidden = set.contains(name);
        if (hide == wasHidden) return;

        if (hide) {
            set.add(name);
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, name, ClientboundSetPlayerTeamPacket.Action.ADD));
        } else {
            set.remove(name);
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }
    }

    /** Restore every viewer's view by removing all targets from the hidden team.
     *  Called by the disable/reload kill-switch alongside {@link NametagManager#restoreAll}. */
    public static int restoreAll(List<ServerPlayer> players) {
        if (team == null) return 0;
        int total = 0;
        for (ServerPlayer viewer : players) {
            Set<String> set = hidden.get(viewer.getUUID());
            if (set == null || set.isEmpty()) continue;
            for (String name : set) {
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        team, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
                total++;
            }
            set.clear();
        }
        return total;
    }

    public static int countHidden() {
        int total = 0;
        for (Set<String> set : hidden.values()) total += set.size();
        return total;
    }
}
