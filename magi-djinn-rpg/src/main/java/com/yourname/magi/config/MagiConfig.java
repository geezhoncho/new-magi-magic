package com.yourname.magi.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON config (server-authoritative). Values marked "reserved" are declared now so the file layout is
 * stable, but are wired up in the phase named in the comment.
 */
public final class MagiConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue DJINN_CAPTURE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue DJINN_ENERGY_REGEN_PER_SECOND;
    public static final ForgeConfigSpec.IntValue MAX_EQUIPPED_DJINN;
    public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MANA_COST_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue DUNGEON_FREQUENCY;
    public static final ForgeConfigSpec.IntValue DUNGEON_SPACING_CHUNKS;
    public static final ForgeConfigSpec.DoubleValue LOOT_QUALITY;
    public static final ForgeConfigSpec.DoubleValue ARMOR_BONUS_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("djinn");
        DJINN_CAPTURE_CHANCE = b.comment("Chance a defeated Djinn yields a bindable core (reserved: Phase 6).")
                .defineInRange("captureChance", 1.0, 0.0, 1.0);
        DJINN_ENERGY_REGEN_PER_SECOND = b.comment("Djinn Energy regenerated per second (reserved: Phase 3).")
                .defineInRange("energyRegenPerSecond", 2.0, 0.0, 1000.0);
        MAX_EQUIPPED_DJINN = b.comment("Simultaneously equipped Djinn (reserved: only 1 is implemented in Phase 1).")
                .defineInRange("maxEquipped", 1, 1, 3);
        b.pop();

        b.push("boss");
        BOSS_HEALTH_MULTIPLIER = b.comment("Global boss health multiplier (reserved: Phase 6).")
                .defineInRange("healthMultiplier", 1.0, 0.1, 100.0);
        b.pop();

        b.push("magic");
        MANA_COST_MULTIPLIER = b.comment("Multiplier for Magi spell mana costs (reserved: Phase 3).")
                .defineInRange("manaCostMultiplier", 1.0, 0.0, 100.0);
        SPELL_DAMAGE_MULTIPLIER = b.comment("Multiplier for Magi spell damage (reserved: Phase 3).")
                .defineInRange("spellDamageMultiplier", 1.0, 0.0, 100.0);
        b.pop();

        b.push("world");
        DUNGEON_FREQUENCY = b.comment("Dungeon entrance frequency multiplier (reserved: Phase 4/5).")
                .defineInRange("dungeonFrequency", 1.0, 0.0, 10.0);
        DUNGEON_SPACING_CHUNKS = b.comment("Minimum spacing between dungeon entrances, in chunks (reserved: Phase 4/5).")
                .defineInRange("dungeonSpacingChunks", 48, 8, 1024);
        b.pop();

        b.push("loot");
        LOOT_QUALITY = b.comment("Loot quality multiplier (reserved: Phase 5).")
                .defineInRange("lootQuality", 1.0, 0.0, 10.0);
        b.pop();

        b.push("equipment");
        ARMOR_BONUS_MULTIPLIER = b.comment("Multiplier applied to Magi armor attribute bonuses. Read once per session.")
                .defineInRange("armorBonusMultiplier", 1.0, 0.0, 10.0);
        b.pop();

        SPEC = b.build();
    }

    private MagiConfig() {}
}
