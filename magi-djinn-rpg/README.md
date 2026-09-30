# Magi: Djinn RPG (Forge 1.20.1) — Phase 1

Foundation: registries, config, networking, player capability, data-driven Djinn (6 definitions), Djinn Core binding item, first armor set, defensive compat framework.

## Build
Requires JDK 17. This project has no Gradle wrapper yet (it could not be generated offline). Either copy `gradlew`, `gradlew.bat` and `gradle/` from the official Forge 1.20.1 MDK, or run `gradle wrapper --gradle-version 8.1.1` with a local Gradle. Then:

    ./gradlew build        # jar in build/libs/
    ./gradlew runClient
    ./gradlew runServer    # dedicated-server boot test

Rename the package `com.yourname.magi` / `mod_group_id` / `mod_authors` to your own.

## Test in game (op)
    /give @s magi:djinn_core{Djinn:"magi:baal"}   then right-click, or:
    /magi djinn grant @s magi:baal
    /magi djinn list @s
    /magi djinn equip @s magi:baal
Check attributes with `/attribute @s minecraft:generic.movement_speed get`. Die, relog, change dimension: state must persist.

## Optional mods
Iron's Spells, Epic Fight, Apotheosis are optional; Phase 1 has only placeholder compat modules (log line when present). See docs/DESIGN.md.

## Phase 2a — weapons + Epic Fight mapping
18 weapons (iron/steel/dungeon x scimitar/saber/dagger/spear/axe/bow), creative-tab only (no recipes until the Magi materials exist).
Epic Fight mappings live in `src/main/resources/data/magi/capabilities/weapons/<item>.json` (file name = item id; UNVERIFIED path convention, see docs/DESIGN.md s.23).
Not yet done: chain weapons, custom mobs (Bandit etc.), elemental/Djinn/legendary tiers, special attacks.
