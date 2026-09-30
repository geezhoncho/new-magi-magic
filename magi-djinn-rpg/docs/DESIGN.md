# Magi: Djinn RPG — Technical Design (Forge 1.20.1)

Status: design v1 + Phase 1 scaffold. Nothing in this document has been compiled against Forge yet (see §22–23).

## 0. Guiding decisions

1. **Ports & adapters.** The core mod defines small interfaces (`ManaProvider`, `WeaponStyleProvider`, `LootAugmenter`) with self-sufficient defaults. `compat.<mod>` packages provide alternative implementations when a mod is present. Core never imports compat.
2. **Data over code.** Djinn, dungeons, set bonuses, loot, biomes, structures and Epic Fight weapon mappings are JSON. Code only implements *behaviour* (abilities, boss scripts, room logic).
3. **Server authoritative, event driven, no idle ticking.** Cooldowns are deadlines, energy regenerates lazily, sync is on state change.
4. **Integrate, don't compete.** One mana pool (Iron's), one affix/loot system (Apotheosis), one combat system (Epic Fight). We add content that plugs into them.
5. **Pocket dimensions = one dimension per *theme*, many *cells* per dimension** (see §8). Truly per-instance dimensions need unsafe runtime level creation and are avoided.

## 1. Mod architecture

Layers, dependencies pointing downward only:

```
content     : item, weapon, armor, entity, boss scripts, spell, dungeon rooms, biome/structure data
domain      : djinn, boss framework, dungeon generator, encounter/party, progression
core        : registry, config, networking, capability/savedata, data loaders
compat      : irons | epicfight | apotheosis | combat   (implements core ports; optional)
client      : rendering, GUI, HUD, sound, particles     (only reached via DistExecutor / client bus)
```

## 2. Package structure (`com.yourname.magi`)

`MagiMod` · `registry` · `config` · `capability` · `networking` · `djinn` (definition, manager, equipment handler, ability API, affinity) · `boss` (brain, phases, attack scripts, telegraphs, encounter scaler) · `entity` + `entity.ai` · `dungeon` (definition, graph generator, room templates, instance manager) · `dimension` (chunk generator, effects, cell allocator) · `world` + `biome` (features, structures, spawns) · `item` · `weapon` · `armor` (+ set bonuses) · `spell` · `loot` · `gui` · `client` · `data` (datagen, codecs) · `command` · `compat.{irons,epicfight,apotheosis,combat}`.
Phase 1 already contains: `config capability djinn networking client armor item registry command compat`.

## 3. Registry plan

`DeferredRegister` per type, one `Magi<Type>` class each: Items, Blocks, BlockEntities, Entities, Menus, Sounds, Particles, CreativeTabs, Attributes, MobEffects, Features, StructureTypes, StructurePieceTypes, ChunkGenerators (codec), Recipes (custom serializers), LootFunctions/Conditions.
Custom Forge registry (`magi:djinn_ability`, `magi:boss_attack`, `magi:room_logic`) via `DeferredRegister.createRegistry`/`NewRegistryEvent` so addons and datapacks can reference behaviours by id.
Armor/weapon sets are declared by one helper call each (`armorSet(name, material, bonuses)`) — 30 sets stay 30 lines plus data.

## 4. Capability plan

| Scope | Holder | Data | Notes |
|---|---|---|---|
| Player | capability `PlayerDjinnData` | owned Djinn, level/xp, equipped, energy stamp, cooldown deadlines | copied on Clone; full sync on login/respawn/dimension change/reload |
| Player | capability `DungeonProgress` (P5) | tiers cleared, keys, current instance id | |
| Server | `SavedData` `DungeonInstances` (P5) | cell allocations, seeds, room states, participants | dirty-flag saves |
| Server | `SavedData` `RewardLedger` (P6) | encounter UUID → per-player claim state | duplicate-exploit guard |
| Entity | none | boss state lives in the entity + `SynchedEntityData` | |

## 5. Djinn architecture

- `DjinnDefinition` (JSON, codec): element, colour, max level, max energy, attribute bonuses (`base + per_level*(level-1)`), ability ids. Id = file name.
- `DjinnManager`: `SimpleJsonResourceReloadListener`, atomic swap on reload.
- `DjinnAbility` (P3, code, Forge registry): `id, cooldown, energyCost, canActivate, activate(ServerPlayer, DjinnContext)`. Activation path: keybind → C2S packet (ability *slot*, not free-form data) → `DjinnEquipmentHandler` validates equipped Djinn, cooldown, energy → runs ability → updates cooldown/energy → syncs.
- `DjinnEquipmentHandler`: the only mutator of `PlayerDjinnData`; applies transient attribute modifiers with deterministic UUIDs; re-applies on login/respawn/dimension/reload.
- **Energy vs mana:** Iron's mana pays for *spells*. Djinn Energy is a small gating meter for *non-spell* Djinn abilities and is what the UI shows. Without Iron's, spells fall back to Djinn Energy so the base mod stays playable. No second mana bar.
- **Affinity** (P3): per-element accumulator raised by using that element; queried by set bonuses and weapons via `AffinityQuery`.
- Multiple Djinn: `maxEquipped` config (default 1). Extra slots share one global ability-cooldown penalty to avoid stacking exploits.

## 6. Boss architecture

- `MagiBossEntity extends Monster`: not a pathfinding-goal soup. Uses a **`BossBrain`** state machine ticked once per tick with cheap logic; vanilla goals limited to movement/targeting.
- `BossDefinition` per boss (code + JSON tunables): phases; each phase = HP threshold, attack pool (weights, cooldowns, conditions), arena mechanics, enrage timer.
- **`AttackScript`** is a timeline: `windup → telegraph → commit → active → recovery`. Each step is data: duration, animation id, `TelegraphShape`, hit volume, damage, effects.
- **`TelegraphShape`**: `Circle, Ring, Cone, Line, Rect, Sweep`. Server sends **one** packet (shape, origin, params, start tick, commit tick) to trackers; client renders indicators and plays warning sound from that. Server resolves hits once at commit (and every N ticks for lingering `HazardZone` entities, N ≥ 5). Nothing is unavoidable: damage only at commit, telegraph ≥ 12 ticks for heavy attacks.
- Mechanics: invulnerability/transition phases, interrupts (stagger meter), summons (capped), arena hazards, teleport/dash, projectile volleys (pooled entities), environmental destruction limited to marked arena blocks that are restored.
- **Scaling** (`EncounterScaler`): participants are snapshotted at engage (+ join-in-progress rule). Tier 1 (1 player): base mechanics. Tier 2 (2–3): +mechanics (e.g. adds, split zones). Tier 3 (4+): raid mechanics (soak/spread, tank-swap debuff). HP scaling is small and configurable; mechanics carry the difficulty.
- UI: `ServerBossEvent` bar for HP, `SynchedEntityData` for phase/enrage/mechanic flags; custom overlay (P8). Music: `BossMusicHook` event + client `BossMusicManager` (placeholder sounds).
- Models: placeholder vanilla-model-based renderers scaled up (no GeckoLib dependency until we decide, §23).
- **Blue Djinn (Talmir, gravity):** Phase 1 melee+gravity pulls; 2 large AOE circles/rings; 3 arena transformation (floor sections collapse into gravity wells); 4 ultimate (long telegraph, arena-wide with safe pockets); final: enrage (shorter recovery, stacking pull).

## 7. Dungeon architecture

- `DungeonDefinition` (JSON): id, theme, tier, floor count, room pools (by role: combat, puzzle, trap, treasure, secret, elite, miniboss, boss), mob tables, loot tables, boss id, dimension theme id.
- `DungeonGraph` generator (pure Java, no MC classes → unit-testable): seeded RNG builds a graph — start → main path of N rooms with branches → optional secrets → boss. Constraints: required-key placement, difficulty ramp, no duplicate puzzle type back to back.
- `RoomTemplate`: structure NBT + JSON sidecar (connectors, role, mob spawn markers, logic id). Rooms are placed on a fixed lattice inside the instance's cell (no overlap tests beyond lattice occupancy).
- `RoomLogic` (registry): puzzles/traps/encounters as small state machines with per-room persisted state. Puzzle set: pressure-plate sequence, rotating statues, element switches (fire/lightning/water activation), light/mirror, rune, mob-triggered doors.
- Rooms **activate lazily**: mobs spawn and logic ticks only for rooms that contain a player.

## 8. Pocket dimension architecture

- Dimensions per **theme**, defined in datapack JSON (`dimension_type` + `dimension`): `magi:pocket_lightning`, `_strength`, `_hellfire`, `_life`, `_fire`, `_gravity`. Each has its own sky/fog/ambient light via `DimensionSpecialEffects`.
- Chunk generator: empty/void (`PocketChunkGenerator` codec, or vanilla flat-void as a fallback). All terrain comes from placed room templates plus small decoration features.
- **Cell allocator** (`SavedData`): each dungeon run gets a cell (e.g. 2048×2048 blocks, spaced far apart). Cells are freed when the run ends/times out, blocks cleared in budgeted batches.
- Entrance structure → `PortalBlockEntity` → creates/joins an instance for the party → teleports to instance start. Exit portal returns to the overworld entrance (stored in the instance record; fallback to world spawn if the entrance was destroyed).
- Dimension travel and death: respawn rules place players at the instance start (config) or overworld spawn if the instance has closed.

## 9. Procedural generation strategy

Seed = hash(world seed, entrance position, run counter) → identical graph on every server for a given run; replay = new counter. Generation = graph (instant, pure) + template placement (budgeted, main thread, ≤ N rooms/tick via `StructureTemplate.placeInWorld`; placement is never done off-thread). Async is used only for pure computation (graph, validation). Placement can be pre-warmed while the party walks the entrance approach.

## 10. Magic integration strategy (Iron's Spells 'n Spellbooks)

- Port `ManaProvider { getMana, canSpend, spend }`. Default = Djinn Energy. `compat.irons` implements it on Iron's `MagicData` and registers Magi spells.
- Spells subclass Iron's `AbstractSpell` and register in Iron's spell registry so they inherit its mana costs, cooldowns, scaling, casting animations and scroll/spellbook support.
- Magi spells are only *registered* when Iron's is present; Djinn abilities that "cast" call the same spell logic through the port.
- Djinn bonuses target Iron's attributes by id in JSON (unknown ids are skipped).
- **All Iron's class/method/attribute names are unverified** (§23). Phase 3 begins with a spike: read the pinned Iron's sources, write one spell end-to-end, then scale.

## 11. Epic Fight integration strategy

- Prefer **datapack mappings** over code: Epic Fight loads weapon-capability JSON; map Magi weapons to existing categories (sword/katana-like → scimitars & sabers, dagger, spear, axe, bow) — no Java coupling to Epic Fight internals.
- Weapons extend the correct vanilla item classes (`SwordItem`, `AxeItem`, `BowItem`, …) so Epic Fight's category heuristics and other mods classify them.
- Chains: no native category assumed → fallback to a stable existing category + Magi special attack through our own events; custom category only if the spike shows a stable API.
- Armor: express weight/stun/impact stats through attribute modifiers only if Epic Fight exposes them as attributes (verify).
- Bosses: large hitboxes are handled by our own hit resolution; Epic Fight mob patches are optional, added via `compat.epicfight` if the API allows.
- Never disable or force Epic Fight mode; never ship vanilla-animation weapons without a mapping.

## 12. Apotheosis integration strategy

- Items extend proper vanilla classes so Apotheosis' loot categories pick them up for affixes/rarity/enchants.
- Dungeon/boss loot tables contain base items; we do **not** write affix NBT. Apotheosis' own loot rules (and, if verified, its loot-table/affix hooks) upgrade drops.
- `LootAugmenter` port: `compat.apotheosis` may bias rarity by dungeon tier if a public hook exists; otherwise no-op.
- Our attribute bonuses use registered attributes and UUID-stable modifiers so affix modifiers stack cleanly.

## 13. Networking strategy

One `SimpleChannel` (`magi:main`, protocol `"1"`). Direction-checked registrations. S2C: `SyncDjinnData` (full snapshot on change), telegraph/boss-state packets to *trackers only*, dungeon HUD state to participants. C2S: `EquipDjinn`, ability activation (slot index), later dungeon UI actions — each re-validated server-side with a simple rate limit. No per-tick packets; boss numbers ride `SynchedEntityData`.

## 14. Multiplayer strategy

Server owns everything. Instances have a participant set and owner; party = players who entered together (Forge/FTB party APIs optional later). Reward eligibility is computed server-side from contribution during the encounter. Reconnect: re-sync full state, re-teleport into a live instance or to safety if closed. Dedicated-server safe: no client class references outside `client` / `DistExecutor`.

## 15. Save-data strategy

Player state → capability NBT, versioned, copied on `Clone` (death + End return). Global state → `SavedData` on the overworld storage with dirty flags. Rules: unknown/invalid ids are dropped on load, never crash; cooldowns are game-time deadlines; capture claims live in the ledger, not only on items.

## 16. World-generation strategy

Biomes as datapack JSON: Golden, Oasis, Ancient, Cursed, Djinn desert. Added to the world through region/biome-modifier hooks; **TerraBlender is a candidate optional dependency for coexisting with other biome mods (unverified, §23)**. Features: dunes (noise-based feature or modified surface rules), palms (`Feature`), oases, ancient roads. Structures: monumental dungeon entrances as custom `Structure` + pieces; camps/ruins/statues via jigsaw pools. Placement via `structure_set` spacing driven by config defaults. Bandit camps carry spawn markers scaled by distance/progression.

## 17. Loot strategy

Loot tables per dungeon tier/room role; global loot modifiers for boss drops (materials, Djinn core, relics). Djinn core is **personal reward** via the ledger (each eligible participant). Materials: Djinn Essence, Ancient Gold, Magical Sand, Elemental Shards, Dungeon Steel, Spirit Crystal, Djinn Core — each gates a crafting tier.

## 18. Armor & weapon strategy

- Armor: `MagiArmorMaterial` + `MagiArmorItem` (extra attribute bonuses, config multiplier). **30 sets** = 30 material lines + bonus data. Set bonuses (2/3/4-piece) are data-driven (`data/magi/set_bonuses/*.json`), applied via equipment-change events as transient modifiers — no ticking.
- Weapons: distinct classes per archetype (curved blade, saber, dagger, spear, axe, bow, chain) with reach/speed/attack-pattern data, elemental properties, Djinn/spell synergy hooks, tier ladder Iron → Steel → Dungeon → Elemental → Djinn → Legendary. Special attacks go through Epic Fight where mapped, else a stable right-click/cooldown fallback.

## 19. Performance strategy

No idle ticking (deadlines/lazy regen). Boss brain = one small state machine; telegraphs = one packet; hazards checked every ≥5 ticks with AABB tests; pooled projectiles and capped summons; particle budgets and client-side LOD. Room logic only for occupied rooms. Template placement is budgeted per tick. Caches for room templates/definitions, atomically swapped on reload. Cell cleanup batched. No per-entity capability lookups in hot loops.

## 20. Configuration strategy

`magi-common.toml` (Forge `ForgeConfigSpec`): captureChance, energyRegen, maxEquipped, boss healthMultiplier, mana/spell multipliers, dungeonFrequency/spacing, lootQuality, armorBonusMultiplier (declared in Phase 1; each wired in the phase noted in its comment). Gameplay numbers that are *content* live in data, not config.

## 21. Milestones

P1 foundation (this drop) → P2 combat (weapons, Epic Fight spike, first mobs) → P3 magic (Iron's spike, spells, abilities) → P4 world → P5 dungeons/pocket dims → P6 boss framework + six Djinn → P7 30 armor sets, weapon tiers, set bonuses → P8 UI → P9 polish. Each phase exits only when: builds, dedicated server boots, client boots, and the matrix in §22 passes.

## 22. Testing strategy

- Unit tests (JUnit) for pure logic: dungeon graph generator, codecs, `PlayerDjinnData` NBT round-trip and duplicate-guard.
- Forge GameTests for structures/rooms/room logic.
- `runServer` smoke test on a dedicated server (no client classes loaded) in CI.
- Launch matrix: base only · +Iron's · +Epic Fight · +Apotheosis · all three · with a broken/mismatched version of each (module must disable itself, not crash).
- Two-client test for sync, ownership, death/respawn/dimension change, reconnect.
- Resource validation: JSON lint, lang keys vs registry, texture existence.

## 23. Known API uncertainties (verify before each phase)

| Area | Uncertain | Fallback / action |
|---|---|---|
| Gradle deps | Maven coordinates/file ids for Iron's, Epic Fight, Apotheosis (CurseMaven project ids written in `build.gradle` comments are from memory) | Confirm on the mod pages; pin exact file ids matching the modpack |
| Iron's | `AbstractSpell` subclassing, spell registry, `MagicData` mana accessors, attribute ids (`irons_spellbooks:max_mana`, `*_spell_power`, `cooldown_reduction`) | P3 spike against real sources; JSON/armor use id-lookup so wrong ids are skipped, not fatal |
| Epic Fight | weapon-capability JSON schema and category ids, whether armor stats are attributes, mob-patch API, custom weapon category API | P2 spike; prefer datapack mapping; fall back to nearest existing category |
| Apotheosis | public loot/affix hooks, rarity bias API | Rely on item-class recognition + its own loot rules |
| TerraBlender | need/versions for coexisting biomes | Biome modifiers only if it is not needed |
| GeckoLib | model/animation library choice for bosses | Placeholder vanilla-model renderers until decided |
| Forge 47 details used in Phase 1 | `OnDatapackSyncEvent#getPlayer()==null` meaning "all players"; `DataResult.error(Supplier)`; `SimpleChannel.send(PacketTarget, msg)` | Standard for 1.20.1 to my knowledge, but **not compiled here** — fix on first build |

## 24. Dependency handling

`mods.toml`: Forge and Minecraft mandatory; Iron's, Epic Fight, Apotheosis `mandatory=false`, `ordering=AFTER`. Code: `CompatManager` checks `ModList` and instantiates a module only when present; each module is try/caught (`Throwable`) and disables itself on failure. Compile-time deps are `compileOnly` (never bundled). Rule: nothing outside `compat.<mod>` references that package. A CI test scans for stray imports of optional-mod packages outside `compat`.
