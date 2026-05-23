# Changelog

## 1.1.1

### Fixed
- **Stuck-blocked LOS cache.** The previous "skip the raycast if cached as blocked and neither player moved >1 block" optimisation was unsound: a third party (other player, mob, opening door, broken block) could restore line of sight without either endpoint moving, leaving pairs stuck as `blocked` forever. Symptom: nametags hidden for players standing in clear sight of each other, and occasionally still visible after walking behind cover. The engine now recasts every pair every sweep — measured cost is well under a millisecond even at scale.
- **`crouch_hides_nametag` actually hides nametags now.** The 1.1.0 implementation piggybacked on the LOS-blocked force-sneak mechanism, which only suppresses nametags through walls — vanilla still renders sneaking-player nametags at close range with clear LOS, so the feature had no visible effect for direct viewers. Replaced with a per-viewer scoreboard team override (`culltag_hidden`, `nametagVisibility=NEVER`) sent only to viewers who should not see the crouching player, leaving the actual server-side team membership untouched.
- **`/culltag disable` failing to fully restore nametags.** Restoration now sends a clearSneaking packet for every (viewer, target) pair rather than only the tracked `hiddenIds` set — guarantees a full vanilla restore even after a hot jar swap, where the server-side tracking set is empty but clients may still hold force-sneak flags from a previous binary. The LOS cache is wiped so a subsequent re-enable fires fresh visibility-change callbacks instead of being suppressed by stale "same as last time" entries. `/culltag reload` that toggles `enabled` off does the same.

### Added
- New [CrouchHider](src/main/java/com/culltag/CrouchHider.java) module managing the per-viewer team packets for the crouch-hide feature.
- `/culltag stats` now reports `LOS-hidden` and `crouch-hidden` counts separately.
- `/culltag disable` restores both LOS and crouch overrides; message reports both counts.
- Per-viewer restoration logging (`[CullTag] Restored N nametag(s) for viewer X`) plus an aggregate summary, for diagnosing client-side desync.
- Declared [PassableFoliage](https://modrinth.com/mod/passable-foliage) as a `breaks` dependency — Fabric loader will refuse to start with both installed. Also documented the incompatibility in the README.

### Removed
- The `lastPos` movement-tracking map (no longer needed without the asymmetric cache skip).

### Caveat
- The per-viewer team trick overrides any real team `target` was already on, in `viewer`'s view only. If your server uses scoreboard teams for PvP coloring or friendly-fire indicators, crouching players will appear teamless to anyone they're hidden from. Disable `crouch_hides_nametag` if that's a problem.

## 1.1.0

- Added `crouch_hides_nametag` config option (default `true`): crouching players have their nametag hidden from everyone entirely, independent of line of sight.
- Added `README.md`.

## 1.0.0

- Initial release.
