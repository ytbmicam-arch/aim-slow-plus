# Aim Slow Plus

A **client-side Fabric mod** for **Minecraft Java 1.21.11** that smoothly lowers your mouse
sensitivity when your crosshair gets close to an entity, so precise aiming is easier.

> **It is NOT an aim assist.** The mod never rotates the camera, never snaps to targets and never
> moves the crosshair. It only multiplies the raw mouse movement by a number between your
> "minimum sensitivity" and 1.0. You stay fully in control.

Inspired by [Aim Slow](https://modrinth.com/mod/aim-slow). This is an independent re-implementation
written for 1.21.11 (it does not reuse the original code).

## Requirements

| What | Version |
|------|---------|
| Minecraft Java Edition | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.141.6+1.21.11 or newer for 1.21.11 |
| Java | 21 (the one bundled with Minecraft 1.21.11 is fine) |
| Mod Menu | optional (adds a "Configure" button in the Mods list) |

Client-side only: nothing to install on servers.

## Build the JAR

You need a **JDK 21 or newer**.

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

The mod is `build/libs/aim-slow-plus-1.0.0.jar`. Ignore the `-sources.jar`.
The first build downloads Minecraft and the mappings, so it needs internet access.

No Java installed? Push this folder to a GitHub repository: the included workflow
(`.github/workflows/build.yml`) builds it for you. Open the **Actions** tab, then the latest run,
then download the **Artifacts** archive.

## Install (Prism Launcher)

1. Create a **Fabric** instance for **1.21.11** (Add Instance, version 1.21.11, loader Fabric).
2. Open the instance, **Edit** then **Mods** then **Download mods**, pick **Modrinth**,
   and install **Fabric API**. Optionally install **Mod Menu** too.
3. Still in **Mods**, click **Add file** and select `aim-slow-plus-1.0.0.jar`
   (or drop it into the instance's `mods` folder: right click the instance, **Folder**, then **.minecraft/mods**).
4. Launch the game. In **Options > Controls > Key Binds** you will find the "Aim Slow Plus" category.

## Usage

* **K** (rebindable) toggles the mod on/off and shows a small message above the hotbar.
* A second key, "Open Aim Slow Plus settings", is unbound by default.
* Open the settings from **Mod Menu** (Mods list, Aim Slow Plus, Configure) or with that key.

### Settings

| Setting | Default | Meaning |
|---------|---------|---------|
| Enable | ON | Master toggle. OFF = vanilla mouse. |
| Slowdown Strength | 75% | Sensitivity removed when the crosshair is exactly on a target (0-100%). |
| Bubble Angle | 10.0 deg | Radius around the crosshair where a target starts to slow the mouse (1-45). |
| Min. Sensitivity | 20% | The multiplier never goes below this, so the mouse cannot freeze (5-100%). |
| Max Range | 24 blocks | Targets farther away are ignored (4-64). |
| Activation Smoothness | 25% | How gradually the slowdown engages (0% = instant). |
| Release Smoothness | 35% | How gradually normal sensitivity returns (0% = instant). |
| Players Only | OFF | Ignore mobs and other entities. |
| Ignore Teammates | ON | Ignore entities on your scoreboard team. |
| Require Line of Sight | ON | Ignore entities hidden behind blocks. |

Settings are saved to `config/aimslowplus.json` and survive restarts. Missing or invalid values are
replaced by defaults or clamped to their allowed range; an unreadable file is renamed to
`aimslowplus.json.broken` and recreated.

### How it works

Every frame, the mod measures the angle between your view direction and the nearest point of each
eligible entity's hitbox (0 degrees = crosshair on the hitbox). The entity closest to the crosshair
inside the bubble wins. The target multiplier eases from 1.0 at the bubble edge down to
`max(min sensitivity, 1 - strength)` at the crosshair (smoothstep curve). The real multiplier follows the
target with frame-rate independent smoothing, with separate speeds for slowing down and for releasing.

Eligible entities are living entities (players, mobs, animals). Spectators, invisible entities,
dead entities and the vehicle you are riding are ignored.

Implementation notes: a single Mixin on `MouseHandler.turnPlayer` scales the accumulated mouse
movement just before vanilla uses it. The sensitivity option itself is never modified, and the state
is reset when the mod is disabled, when you join or leave a world, and when a screen opens.

## License

MIT, see `LICENSE`.
