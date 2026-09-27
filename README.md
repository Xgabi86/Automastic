# Automastic

Automation machines for Minecraft 1.21.1 (NeoForge 21.1.x). No dependency other than NeoForge itself.

## Machines

### Harvester
Harvests fully grown crops in a zone in front of it and stores the drops in a 27-slot output inventory.

- Crops are reset to age 0 instead of being broken and replanted, so modded crops
  (e.g. Mystical Agriculture) keep working, including their farmland bonuses.
- Zone from 1x1 to 25x25, height 0 to 16 layers, up or down. Adjustable in the GUI.
- Two modes, toggled in the GUI: one block at a time, or whole area at once.
- Output inventory is extract-only and exposed as a standard item handler capability,
  so hoppers and pipes from other mods can pull from it.
- Overflow is dropped above the block; the machine pauses while the inventory is full.
- Energy: 250 FE per plant by default, 1,000,000 FE buffer (both configurable).
  `energyPerPlant = 0` disables energy entirely. In "whole area" mode the harvester collects what it
  can afford and stops as soon as it cannot pay for the next plant.
- Feedback: a sound and particles on every harvested plant.

### Sprinkler
Waters the crops around it to make them grow faster.

- By default, every 5 seconds it gives each block of its 9x9 area (same height as the sprinkler) a
  40% chance of an extra growth tick. The interval (`ticksBetweenWatering`), the percentage
  (`percentPerWatering`) and the radius (`radius`) are all configurable. Crops follow their own rules (light, soil, growth events), so this works
  with modded crops (e.g. Mystical Agriculture) and never skips a growth stage.
- Internal water tank: 10,000 mB by default, 500 mB used per watering pass. Water is only used if
  at least one plant was actually watered, so a fully grown field costs nothing.
- Fill it with a bucket (right-click), or with any fluid pipe: it exposes the standard NeoForge
  fluid handler capability (Mekanism, Pipez, XNet...). Only water is accepted and nothing can be
  extracted from it.
- Right-click: shows the water level. Sneak + right-click: toggles the splash particles.
- Tank size and cost per watering are configurable. `waterPerWatering = 0` disables the tank
  entirely: the sprinkler then runs without water and stops accepting fluids.
- The rotor spins only while the sprinkler has water.
- The item tooltip shows what it does and its current range, rate and water cost, read from the
  config (so it follows your changes after a restart; on a server, it shows the server's values).
  The harvester and the machine frame have tooltips too.

## Configuration
`automastic-server.toml` (per world), see the comments in the file.

## Building
```
./gradlew build
```
The jar is in `build/libs/`. Java 21 is required. The first run downloads Gradle.

### Version and channel
Both live in `gradle.properties`:

| Property      | Role                                   | Example |
|---------------|----------------------------------------|---------|
| `mod_version` | The version number (MAJOR.MINOR.PATCH) | `0.1.0` |
| `mod_channel` | `alpha`, `beta` or `release`           | `alpha` |

The channel is appended to the version, except for `release`:

| Channel   | Jar                                        |
|-----------|--------------------------------------------|
| `alpha`   | `automastic-1.21.1-0.1.0-alpha.jar`        |
| `beta`    | `automastic-1.21.1-0.1.0-beta.jar`         |
| `release` | `automastic-1.21.1-0.1.0.jar`              |

Override without editing the file:
```
./gradlew build -Pmod_channel=beta
./gradlew build -Pmod_channel=release -Pmod_version=0.2.0
```
Any other channel value fails the build with an explicit error.

## Adding a machine
1. Create `machine/<name>/` with a `Block`, `Tile`, `Menu`, `Screen`.
2. `Block` extends `BaseMachineBlock`, `Tile` extends `BaseMachineTile` (+ `IFieldHolder`),
   `Menu` extends `BaseMachineMenu`.
3. Register it in `ModBlocks`, `ModItems`, `ModTiles`, `ModMenus`, `ModTabs`, and `ClientSetup`.
4. Item/energy capabilities are wired automatically by `CapabilityRegistry`.
5. Add an item tooltip with `TooltipUtil` (`core/`): override `appendHoverText` in the block and read
   values through `TooltipUtil.value(...)`, which is safe on the client before a world is loaded.

### Translation keys
Every key follows the same pattern, so lang files stay readable with many machines:

| Key                                      | Used for                          |
|------------------------------------------|-----------------------------------|
| `block.automastic.<machine>`             | item / block name                 |
| `block.automastic.<machine>.tooltip`     | main description (grey)           |
| `block.automastic.<machine>.tooltip.<x>` | detail lines (config values)      |
| `block.automastic.<machine>.message.<x>` | action bar messages               |
| `container.automastic.<machine>`         | GUI title                         |
| `gui.automastic.<machine>.<x>`           | widgets specific to one machine   |
| `gui.automastic.common.<x>`              | widgets shared between machines   |

Toggle buttons use `<key>.0` / `<key>.1` for their two states.

## License
MIT. Derived in part from Cyclic by Lothrazar (MIT), see `LICENSE`.
