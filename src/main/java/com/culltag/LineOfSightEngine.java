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
 *  - Position cache: skip the raycast if neither player moved more than 1 block
 *    since the last check (result cannot have changed).
 */
public final class LineOfSightEngine {

    // viewer UUID → target UUID → can viewer currently see target?
    private static final Map<UUID, Map<UUID, Boolean>> cache = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Boolean>> prev  = new ConcurrentHashMap<>();

    // Last eye-position used for each player's LOS checks (for movement detection)
    private static final Map<UUID, Vec3> lastPos = new ConcurrentHashMap<>();

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
        lastPos.keySet().retainAll(live);

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

                Vec3 eyeA = a.getEyePosition();
                Vec3 eyeB = b.getEyePosition();

                // Check the current cached result for this pair.
                Map<UUID, Boolean> rowA = cache.computeIfAbsent(a.getUUID(), k -> new ConcurrentHashMap<>());
                Boolean cachedVisible = rowA.get(b.getUUID());

                // If currently visible, always recast — visible→blocked transitions
                // happen the instant someone steps behind cover (even <1 block of movement).
                // If currently blocked, skip the ray when neither player moved significantly;
                // they'd have to move to regain LOS anyway.
                Vec3 oldA = lastPos.get(a.getUUID());
                Vec3 oldB = lastPos.get(b.getUUID());
                boolean needsCheck = cachedVisible == null
                        || cachedVisible
                        || oldA == null || oldB == null
                        || eyeA.distanceToSqr(oldA) > 1.0
                        || eyeB.distanceToSqr(oldB) > 1.0;

                boolean visible;
                if (needsCheck) {
                    raycasts++;
                    visible = castRay(a, b, eyeA, eyeB);
                    lastPos.put(a.getUUID(), eyeA);
                    lastPos.put(b.getUUID(), eyeB);
                } else {
                    visible = false; // cached blocked, neither moved
                }

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
