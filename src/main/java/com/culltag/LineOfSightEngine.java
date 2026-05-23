package com.culltag;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the per-player LOS cache and drives callbacks into {@link NametagManager}
 * when visibility between two players changes.
 *
 * Optimisations:
 *  - Symmetric LOS: one raycast per unordered pair, result shared both ways.
 */
public final class LineOfSightEngine {

    // viewer UUID → target UUID → can viewer currently see target?
    private static final Map<UUID, Map<UUID, Boolean>> cache = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Boolean>> prev  = new ConcurrentHashMap<>();

    private static int tickAccum = 0;

    // ── Perf counters ─────────────────────────────────────────────────────────
    private static long totalSweeps    = 0;
    private static long totalRaycasts  = 0;
    private static double lastSweepMs  = 0;
    private static final double[] sweepRing = new double[100];
    private static int sweepRingIdx = 0;

    public record PerfStats(long totalSweeps, long totalRaycasts, double lastSweepMs, double avgSweepMs) {}

    public static PerfStats getStats() {
        double sum = 0;
        int count = 0;
        for (double v : sweepRing) { if (v > 0) { sum += v; count++; } }
        return new PerfStats(totalSweeps, totalRaycasts, lastSweepMs, count == 0 ? 0 : sum / count);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private LineOfSightEngine() {}

    /** Wipes all cached visibility state. Call after disabling so a re-enable
     *  fires fresh visibility-change callbacks instead of being suppressed
     *  by stale "same as last time" entries. */
    public static void clearCache() {
        cache.clear();
        prev.clear();
    }

    public static boolean canSee(UUID viewerUuid, UUID targetUuid) {
        Map<UUID, Boolean> row = cache.get(viewerUuid);
        if (row == null) return true;
        return row.getOrDefault(targetUuid, true);
    }

    public static void tick(List<ServerPlayer> players) {
        tickAccum++;
        if (tickAccum % CullTagConfig.checkIntervalTicks != 0) return;

        if (!CullTagConfig.enabled) return;

        long startNs = System.nanoTime();
        int raycasts = 0;

        Set<UUID> live = new HashSet<>(players.size());
        for (ServerPlayer p : players) live.add(p.getUUID());
        cache.keySet().retainAll(live);
        prev .keySet().retainAll(live);

        double maxDistSq = (double) CullTagConfig.maxDistance * CullTagConfig.maxDistance;

        // Process each unordered pair exactly once.
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer a = players.get(i);

            for (int j = i + 1; j < players.size(); j++) {
                ServerPlayer b = players.get(j);

                // Different dimensions — always hidden, no ray needed.
                if (a.level() != b.level()) {
                    handleResult(a, b, false);
                    handleResult(b, a, false);
                    continue;
                }

                // Beyond max distance — evict from cache (vanilla will handle rendering).
                if (a.distanceToSqr(b) > maxDistSq) {
                    evict(a, b);
                    evict(b, a);
                    continue;
                }

                // Crouch-hide uses a per-viewer team override (NEVER nametag visibility)
                // because the force-sneak mechanism doesn't suppress nametags at close range
                // with clear LOS — vanilla still renders sneaking nametags then.
                boolean aCrouchHidden = CullTagConfig.crouchHidesNametag && a.isCrouching();
                boolean bCrouchHidden = CullTagConfig.crouchHidesNametag && b.isCrouching();
                CrouchHider.setHidden(a, b, bCrouchHidden);
                CrouchHider.setHidden(b, a, aCrouchHidden);

                // Both crouching — both nametags are team-hidden, no point raycasting.
                if (aCrouchHidden && bCrouchHidden) {
                    continue;
                }

                Vec3 eyeA = a.getEyePosition();
                Vec3 eyeB = b.getEyePosition();

                // Always recast. The previous "skip if cached blocked and neither moved"
                // optimisation was unsound: a third party (other player, mob, opening door,
                // broken block) can restore LOS without either endpoint moving, leaving the
                // pair stuck as blocked forever. Sweeps are sub-millisecond — correctness wins.
                raycasts++;
                boolean visible = castRay(a, b, eyeA, eyeB);

                handleResult(a, b, visible);
                handleResult(b, a, visible);
            }
        }

        double ms = (System.nanoTime() - startNs) / 1_000_000.0;
        lastSweepMs = ms;
        totalSweeps++;
        totalRaycasts += raycasts;
        sweepRing[sweepRingIdx] = ms;
        sweepRingIdx = (sweepRingIdx + 1) % sweepRing.length;
    }

    private static void evict(ServerPlayer viewer, ServerPlayer target) {
        UUID tid = target.getUUID();
        Map<UUID, Boolean> c = cache.get(viewer.getUUID());
        Map<UUID, Boolean> p = prev .get(viewer.getUUID());
        if (c != null) c.remove(tid);
        if (p != null) p.remove(tid);
    }

    private static void handleResult(ServerPlayer viewer, ServerPlayer target, boolean nowVisible) {
        UUID tid = target.getUUID();
        Map<UUID, Boolean> viewerCache = cache.computeIfAbsent(viewer.getUUID(), k -> new ConcurrentHashMap<>());
        Map<UUID, Boolean> viewerPrev  = prev .computeIfAbsent(viewer.getUUID(), k -> new ConcurrentHashMap<>());

        boolean wasVisible = viewerPrev.getOrDefault(tid, nowVisible);
        viewerCache.put(tid, nowVisible);
        viewerPrev .put(tid, nowVisible);

        if (nowVisible != wasVisible) {
            NametagManager.onVisibilityChanged(viewer, target, nowVisible);
        }
    }

    private static boolean castRay(ServerPlayer viewer, Entity target, Vec3 eye, Vec3 tgtEye) {
        BlockHitResult hit = viewer.level().clip(new ClipContext(
                eye, tgtEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                viewer));
        return hit.getType() == HitResult.Type.MISS;
    }
}
