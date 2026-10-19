# CubeCraft Health Bar (Fabric, Minecraft Java 1.21.7)

Client-side, visual-only replacement for CubeCraft's combat health indicator. It never sends packets,
never touches real health, and does nothing unless you are connected to `*.cubecraft.net`.

**Status: source only. It has NOT been compiled, run, or tested against 1.21.7 or Lunar Client.**
Everything below marked "unverified" is exactly that.

## What was verified vs. not

| Item | Status |
|---|---|
| Lunar Client's Fabric add-on accepts your own Fabric mods (drag-and-drop into the launcher's Mods tab, or the mods-folder button), 1.16+ | Lunar's own docs say so |
| Lunar offers a Vanilla add-on that runs without Lunar features (fallback if a mixin conflicts) | Lunar's own docs say so |
| Fabric API `0.129.0+1.21.7`, Java 21 | Seen in public 1.21.7 projects |
| Lunar publishes Fabric modpacks that list 1.21.7 | Seen on third-party listings |
| Whether *this* mod loads in Lunar's 1.21.7 Fabric add-on | **Unverified** (needs you to try) |
| Mojang-mapped names used in the mixins/renderer (see "Likely first-build fixes") | **Unverified** (no compiler/jars here) |
| How CubeCraft delivers its health indicator (scoreboard row? entity data? text entity?) | **Unverified**, use `/cchb probe` |
| CubeCraft's exact visibility timer | **Unverified**. A 2020 CubeCraft proposal described "visible after you deal damage, hidden ~7 s later, ~10 blocks"; I can't confirm that is what shipped |

## Build

1. Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from one of your working 1.21.7 mod projects into this folder.
2. If your working project uses different Loom/Loader versions, copy those into `gradle.properties`.
3. `./gradlew build` -> `build/libs/cubecraft-healthbar-0.1.0.jar` (not the `-sources` jar).

The project uses **Mojang mappings**. If your other mods use Yarn, say so and the source needs a rename pass.

## Install (Lunar)

1. In the Lunar launcher choose 1.21.7 with the **Fabric** add-on.
2. Open the Mods tab and drag the jar in (or use the mods-folder button). Button positions change between launcher updates.
3. Launch, join CubeCraft. If Lunar shows a mod error screen, or a mixin fails, send me the text.
4. Fallback: the **Vanilla** add-on, or a plain Fabric 1.21.7 profile.

## First run (important)

CubeCraft's indicator delivery is unknown, so the mod ships with a read-only probe.

1. Join a CubeCraft game, stand next to an opponent (ideally just after you hit them), run `/cchb probe`.
2. Send me the output. It shows the below-name objective (name, criteria), the opponent's scoreboard score and entity health, team prefix/suffix, passengers, and nearby text displays.

What the defaults do, and why the probe matters:

- `healthSource=AUTO`, `visibility=AUTO`: if a BELOW_NAME objective with `health` criteria (or the name in `scoreboardObjectiveName`) exists, the bar is shown for exactly the players the server sends a score for, and that row is hidden so you don't get two bars.
- If no usable objective exists, it falls back to **entity health + a combat timer** (you hit them, or they hit you, within `combatWindowMs`). That is an **approximation**, and if CubeCraft fakes entity health it will show fake values.
- If the probe shows CubeCraft uses a different mechanism (e.g. `dummy` criteria, custom text), we adjust `scoreboardObjectiveName`/`scoreboardMaxHealth` or change the code. The original bar can only be hidden when it is the vanilla below-name row.
- Keep CubeCraft's own health-indicator setting **on**; the mod relies on the data the server sends for it.

## Animation

Per opponent: `targetRatio` (latest known) and `displayed` (what you see). Every frame:
`displayed += (target - displayed) * (1 - exp(-dt / tau))`, with `tau = smoothingTimeMs` (default 100 ms: ~63% of the gap in 100 ms, ~95% in 300 ms) and `dt` clamped to 250 ms. A new hit only moves the target, so nothing restarts. Healing glides the same way. A bar starts at the current known value when it first appears or reappears after >1.5 s hidden. It smooths only what is drawn; it cannot make the underlying data more precise.

## Placement

Same anchor and 0.025 scale as nametag text, camera-facing, replacing the below-name row. `top = referenceTopPx - closerFraction*height + verticalOffset`, clamped so it stays `minClearancePx` below the name text. `referenceTopPx`, width, height and colours are **estimates** until I can match them to your video.

## Commands

`/cchb toggle | list | set <name> <value> | reload | debug | probe`. Settings live in `config/cubecraft-healthbar.json`. `devAllowAnyServer` (default false) is for testing on a non-CubeCraft server only.

## Likely first-build fixes (all version-sensitive spots)

- `PlayerRenderState.scoreText`, `.nameTagAttachment`, `.boundingBoxHeight`, `.distanceToCameraSq`, `.isInvisible` (PlayerRendererMixin, HealthBarRenderer)
- Mixin targets `PlayerRenderer.extractRenderState / renderNameTag / render` (a missing override fails at launch with a clear mixin error)
- `RenderType.debugQuads()` and `VertexConsumer.addVertex(...).setColor(int)` (HealthBarRenderer): if the bar is invisible, try `RenderType.debugFilledBox()`-style alternatives or tell me
- `ClientboundDamageEventPacket.entityId()` / `getSource(Level)` (ClientPacketListenerMixin)
- `Scoreboard.getDisplayObjective / getPlayerScoreInfo`, `ReadOnlyScoreInfo.value()`
- Lunar's own nametag mods may also mix into player name rendering; disable them if you see conflicts.

## Testing checklist

- [ ] No bar on single-player or other servers
- [ ] No bar above every nearby player
- [ ] Bar appears/disappears with CubeCraft's own indicator (compare against it before hiding it: `/cchb set hideOriginal false`)
- [ ] Nametag still visible, no overlap, no second bar
- [ ] Several rapid hits glide without stutter; healing glides back up
- [ ] Same feel at 60 and 240 FPS (`/cchb set smoothingTimeMs` to taste)
- [ ] No crash on death, respawn, server switch, disconnect
- [ ] Check CubeCraft's current mod rules before using it in ranked play (this mod only draws data the server already sends)
