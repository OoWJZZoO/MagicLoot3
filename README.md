# MagicLoot3

**A Slimefun 4 addon** — adds randomly generated ruins to your world, ARPG-style random equipment, and an NPC-based item appraisal system.

Revived from [MagicLoot3](https://github.com/TheBusyBiscuit-plugin-archive/MagicLoot3) (2016, by mrCookieSlime).

---

## Compatibility

| Component | Minimum | Tested |
|-----------|---------|--------|
| Paper | 1.21.1 | 1.21.11 |
| Slimefun 4 | RC-37 | vb79ae49-Beta |
| Java | 21 | 21.0.7 |

Slimefun is a soft dependency — the plugin runs without it, only skipping Slimefun item registration.

---

## Gameplay

### World Ruin Generation

The plugin automatically spawns ruined structures in the world containing loot chests and randomized spawners.

- Each new chunk triggers one roll, with configurable probability
- Whitelist-based world selection, each world has independent spawn chances
- Two structure types: **regular ruins** and **special buildings** (i.e., Lost Library)

### Lost Librarian

#### How to Find
- **Natural generation**: always appears inside every Lost Library building

#### Alternative
- **Slimefun crafting**: craft a "Lost Librarian's Desk" via Enhanced Crafting Table, place it down, and right-click. Works identically to the Lost Librarian NPC.

#### Appraisal
Right-click the Librarian or Desk while holding "Unanalyzed" equipment (item name appears as garbled text). An appraisal GUI opens with these options:

| Option | XP Cost | Description |
|--------|---------|-------------|
| Random | 10 levels | Picks a random tier |
| Common | 2 levels | Common tier |
| Uncommon | 5 levels | Uncommon tier |
| Rare | 10 levels | Rare tier |
| Epic | 25 levels | Epic tier |
| Legendary | 50 levels | Legendary tier |

All costs and Random tier probabilities can be modified in the config files.

### ARPG Random Equipment System

Generated equipment has random tiers, names, enchantments, and potion affixes.

**Five tiers** (weighted random):

| Tier | Default Weight | Enchantments | Potion Affixes |
|------|---------------|-------------|----------------|
| Common | 11 | 1 | 0 |
| Uncommon | 7 | 1~2 | 0~1 |
| Rare | 4 | 1~3 | 0~2 |
| Epic | 3 | 1~4 | 0~3 |
| Legendary | 1 | 1~5 | 0~4 |

**Potion Affix System**:
- `+ effect_name level` in item lore grants a buff to the wearer on hit
- `- effect_name level` inflicts a debuff on the target
- Effect duration is `(level * 3)` seconds

**Loot Types** (individually toggleable in config):

| Type | Produces |
|------|----------|
| TOOL | Random weapon/tool/armor with tier affixes |
| TREASURE | Valuables (diamonds, gold ingots, etc.) |
| BOOK | Enchanted book with tier affixes |
| POTION | Splash/Lingering potion with 1~2 random effects |
| SLIMEFUN | Random Slimefun item |
| UNANALYZED | Unidentified equipment (garbled name), requires appraisal |

### Mob Equipment

Zombies, Skeletons, and Zombified Piglins have a chance (default 15%) to spawn wearing random magic gear, which can drop on death.

### Christmas Event

Every year from December 22 to 26, mobs have a chance to spawn wearing Santa hats and wielding Legendary weapons.

---

## Custom Ruins

1. Save your build as an `.nbt` file using a vanilla Structure Block in-game
2. Name the file in `lowercase_underscore` format (e.g., `my_tower.nbt`)
3. Place it in `plugins/MagicLoot3/structures/`
4. Run `/magicloot reload` or restart the server
5. The structure will automatically generate as a regular ruin in the world

> "Lost Library" type buildings are not yet user-configurable.

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/magicloot version` | None | View version, build, language, debug status |
| `/magicloot debug` | `magicloot3.admin` (op) | Toggle debug logging |
| `/magicloot reload` | `magicloot3.admin` (op) | Reload all configs and rescan structure files |
| `/magicloot generate <name>` | `magicloot3.admin` (op) | Generate the named structure at your location |
| `/magicloot language <zh\|en>` | `magicloot3.admin` (op) | Switch language |

All commands support Tab completion.

---

## Configuration Files

All config files are located in `plugins/MagicLoot3/`.

### config.yml — Main Configuration

```yaml
language: en          # Default language (zh / en)

chances:
  mobs: 15            # Chance for mobs to wear random gear (percent)

# World config: each world has independent spawn rates
worlds:
  world:
    ruin-chance: 8       # Chance to generate a ruin per new chunk
    building-chance: 12  # Chance a ruin is a special building
  world_the_end:
    ruin-chance: 35
    building-chance: 5

chest:
  items:
    min: 3
    max: 11            # Items per chest (random within range)
  treasure-stack:
    min: 2
    max: 9             # Treasure item stack size
  slimefun-stack:
    min: 1
    max: 3             # Slimefun item stack size

durability:
  min: 10              # Minimum remaining durability (percent)
  max: 90              # Maximum remaining durability (percent)

costs:                 # Appraisal cost in XP levels
  RANDOM: 10
  COMMON: 2
  UNCOMMON: 5
  RARE: 10
  EPIC: 25
  LEGENDARY: 50

enable:                # Enabled loot types
  TREASURE: true
  TOOL: true
  SLIMEFUN: true
  BOOK: true
  POTION: true
  UNANALIZED: true

spawners:              # Spawner mob pool
- ZOMBIE
- SPIDER
- CAVE_SPIDER
- VEX
```

### Items.yml — Item Weights

Three independent pools: `tools`, `treasure`, `slimefun`.

```yaml
tools:
  DIAMOND_SWORD: 10    # Higher weight = more common
  FLINT_AND_STEEL: 0   # Set to 0 to disable
  ...
treasure:
  DIAMOND: 10
  GOLD_INGOT: 10
  ...
slimefun:
  LOST_BOOKSHELF: 10
  ...
```

### loot_tiers.yml — Tier Parameters

```yaml
COMMON:
  weight: 11            # Generation weight (higher = more common)
  applicable-weight: 8  # "Random" appraisal weight
  enchantments:
    min: 1              # Minimum enchantments
    max: 1              # Maximum enchantments
  effects:
    min: 0              # Minimum potion affixes
    max: 0              # Maximum potion affixes
```

### Enchantments.yml — Enchantment Level Caps

```yaml
sharpness:
  max-level: 5          # Sharpness I ~ V
protection:
  max-level: 4
```

### Effects.yml — Potion Effect Level Caps

```yaml
speed:
  max-level: 3          # Speed I ~ III
instant_health:
  max-level: 1          # Instant Health I
fire_resistance:
  max-level: 1          # Fire Resistance I
```

### lang/zh.yml and lang/en.yml — Language Files

Controls equipment naming pools, NPC dialogue, GUI text, and log messages. Editable to customize equipment name styles.

### ruin_settings/<name>.yml — Per-Structure Config

```yaml
y-offset: 0             # Vertical offset
underwater: false       # Allow underwater generation
```

---

## Data Folder Structure

```
plugins/MagicLoot3/
├── config.yml
├── Items.yml
├── Enchantments.yml
├── Effects.yml
├── loot_tiers.yml
├── lang/
│   ├── zh.yml
│   └── en.yml
├── structures/          # Custom structure files (.nbt)
│   ├── farm.nbt
│   ├── house.nbt
│   └── ...
└── ruin_settings/       # Per-structure settings
    ├── farm.yml
    └── ...
```

---

## Building

Requires Java 21 + Maven 3.9+.

```bash
# Chinese build (default)
mvn clean package

# English build
mvn clean package -P en
```

Output: `target/MagicLoot3-3.5.0-en.jar` and `target/MagicLoot3-3.5.0-zh.jar`.

The two JARs differ only in the default language of Slimefun item names/descriptions and config.yml comments. All other text can be switched at runtime via `/magicloot language`.

---

## Credits

- Original author [mrCookieSlime / TheBusyBiscuit](https://github.com/TheBusyBiscuit) — MagicLoot3 original
- [Slimefun 4 Community](https://github.com/Slimefun/Slimefun4)
