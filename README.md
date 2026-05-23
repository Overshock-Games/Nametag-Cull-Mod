<h1 align="center">CullTag</h1>
<h3 align="center">True Server-Side Nametag Culling for Fair Play</h3>

Vanilla Minecraft gives every player a free, built-in wallhack: nametags visible through solid blocks. **CullTag** fixes this unfair advantage with highly optimized, server-side raycasting. 

If a player is hiding behind a wall, their nametag is gone. It’s that simple. 

Because all calculations and packet interceptions happen entirely on the server, **your players don't need to install anything.** They can connect with a 100% vanilla client and immediately experience fairer gameplay, making it perfect for SMPs, PvP arenas, and hardcore survival servers.

## Key Features

* **Zero Client Setup:** 100% server-side. Vanilla and modded clients both see the correct, culled nametags automatically.
* **Engineered for Performance:** Raycasting can be heavy, so CullTag is built to protect your server's TPS. It utilizes **symmetric LOS** (calculating only one ray per pair of players instead of two) and only runs a sweep every few ticks — typical cost is well under a millisecond.
* **Crouch to Hide:** Crouching players have their nametag hidden from everyone entirely, regardless of line of sight — great for staying unseen. Enabled by default and fully optional.
* **Live Configuration:** Tweak maximum tracking distance, tick frequency, and crouch-hiding in `config/culltag.properties`.
* **Hot-Swappable:** Toggle the entire system or reload config changes live without ever needing to restart your server.
* **Built-in Profiling:** Monitor exactly how the mod is operating under the hood with built-in performance metrics.

## How It Works

Every few ticks, the server quietly casts a ray between each pair of online players. If that ray intersects with a block, the server intercepts the vanilla network packets and hides the target's nametag from the viewer. The exact moment a player steps out of cover and Line of Sight (LOS) is restored, the server updates the packet and the nametag seamlessly reappears.

When `crouch_hides_nametag` is enabled (the default), a crouching player's nametag is hidden from everyone outright — no line-of-sight check needed.

## Commands

Manage the mod entirely in-game (requires appropriate permissions):
* `/culltag enable` & `/culltag disable` — Toggle the culling system live. Your choice automatically persists to the config file.
* `/culltag reload` — Hot-reload any changes made to `culltag.properties`.
* `/culltag stats` — View real-time raycast performance metrics and active tracking data.

## Requirements

* **Environment:** Server-side ONLY (Drop it in your server's `mods` folder and you're done)
* **Dependencies:** [Fabric API](https://modrinth.com/mod/fabric-api)

## Known Incompatibilities

* **[PassableFoliage](https://modrinth.com/mod/passable-foliage)** — mutates leaf collision shapes on both client and server, desynchronising LOS raycasts through foliage and producing erratic nametag visibility.