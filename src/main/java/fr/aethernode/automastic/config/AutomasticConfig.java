package fr.aethernode.automastic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration serveur (fichier {@code automastic-server.toml}, propre à chaque monde).
 * <p>
 * Les valeurs sont lues à chaque utilisation via {@code .get()} : un changement de
 * config est pris en compte sans redémarrer.
 */
public final class AutomasticConfig {

  public static final ModConfigSpec SPEC;

  // ---- Harvester
  /** Énergie (FE) consommée par plante récoltée. 0 = énergie désactivée. */
  public static final ModConfigSpec.IntValue HARVESTER_ENERGY_PER_PLANT;
  /** Capacité du tampon d'énergie du harvester (FE). */
  public static final ModConfigSpec.IntValue HARVESTER_ENERGY_CAPACITY;
  /** Ticks entre deux actions en mode « un bloc à la fois ». */
  public static final ModConfigSpec.IntValue HARVESTER_TICKS_BETWEEN_ACTIONS;
  /** Ticks entre deux passes en mode « Area » (tout le champ d'un coup). */
  public static final ModConfigSpec.IntValue HARVESTER_TICKS_BETWEEN_AREA_PASSES;

  // ---- Sprinkler
  /** Eau (mB) consommée à chaque arrosage. 0 = réservoir désactivé (le sprinkler fonctionne sans eau). */
  public static final ModConfigSpec.IntValue SPRINKLER_WATER_PER_WATERING;
  /** Capacité du réservoir d'eau du sprinkler (mB). */
  public static final ModConfigSpec.IntValue SPRINKLER_TANK_CAPACITY;
  /** Rayon d'arrosage (2 * rayon + 1 = côté de la zone), de 1 à 4 (valeur d'origine de Cyclic). */
  public static final ModConfigSpec.IntValue SPRINKLER_RADIUS;
  /** Ticks entre deux arrosages (20 ticks = 1 seconde). */
  public static final ModConfigSpec.IntValue SPRINKLER_TICKS_BETWEEN_WATERING;
  /** Pourcentage (1 à 100) des blocs de la zone arrosés à chaque arrosage. */
  public static final ModConfigSpec.IntValue SPRINKLER_PERCENT_PER_WATERING;
  /** Afficher les particules d'eau pendant l'arrosage (côté serveur : s'applique à tous les sprinklers). */
  public static final ModConfigSpec.BooleanValue SPRINKLER_PARTICLES;

  static {
    ModConfigSpec.Builder b = new ModConfigSpec.Builder();

    b.comment("Harvester settings").push("harvester");

    HARVESTER_ENERGY_PER_PLANT = b
        .comment("Energy (FE) consumed for EACH plant harvested.",
            "In 'whole area' mode the harvester collects as many plants as it can afford, then",
            "stops as soon as it lacks the energy for the next plant (it resumes on the next pass).",
            "Set to 0 to DISABLE energy entirely: the harvester then runs without any power",
            "and its energy bar is hidden.")
        .defineInRange("energyPerPlant", 250, 0, 1_000_000);

    HARVESTER_ENERGY_CAPACITY = b
        .comment("Size of the harvester's internal energy buffer (FE).",
            "Ignored when energyPerPlant is 0.",
            "Values above 32,767,000 are not fully displayed by the GUI bar (the machine still works).")
        .defineInRange("energyCapacity", 1_000_000, 1, 32_767_000);

    HARVESTER_TICKS_BETWEEN_ACTIONS = b
        .comment("Game ticks between two actions in the default mode (one block per action).",
            "20 ticks = 1 second. Lower = faster.")
        .defineInRange("ticksBetweenActions", 4, 1, 1200);

    HARVESTER_TICKS_BETWEEN_AREA_PASSES = b
        .comment("Game ticks between two full passes in 'Area' mode (whole field at once).",
            "20 ticks = 1 second. Lower = faster.")
        .defineInRange("ticksBetweenAreaPasses", 100, 1, 12000);

    b.pop();

    b.comment("Sprinkler settings").push("sprinkler");

    SPRINKLER_WATER_PER_WATERING = b
        .comment("Water (mB) consumed by EACH watering pass (see ticksBetweenWatering).",
            "Water is only consumed if at least one plant was actually watered, so a sprinkler",
            "standing in a fully grown field does not drain its tank.",
            "1000 mB = 1 bucket. Set to 0 to DISABLE the water tank entirely: the sprinkler then",
            "runs without any water and no longer accepts fluids from pipes.")
        .defineInRange("waterPerWatering", 500, 0, 1_000_000);

    SPRINKLER_TANK_CAPACITY = b
        .comment("Size of the sprinkler's internal water tank (mB). 10000 mB = 10 buckets.",
            "Ignored when waterPerWatering is 0.")
        .defineInRange("tankCapacity", 10_000, 1, 1_000_000);

    SPRINKLER_RADIUS = b
        .comment("Watering radius of the sprinkler (radius 4 = a 9x9 area, the original Cyclic value).",
            "Lower it to make sprinklers cover less ground. The maximum is 4.")
        .defineInRange("radius", 4, 1, 4);

    SPRINKLER_TICKS_BETWEEN_WATERING = b
        .comment("Game ticks between two watering passes. 20 ticks = 1 second, so the default of",
            "100 is one pass every 5 seconds. Lower = faster growth (and more water used).")
        .defineInRange("ticksBetweenWatering", 50, 1, 72_000);

    SPRINKLER_PERCENT_PER_WATERING = b
        .comment("Percentage of the plants in range that receive the watering effect on EACH pass.",
            "Every block of the area rolls this chance independently.",
            "40 is the original Cyclic value; 100 waters every plant on every pass.")
        .defineInRange("percentPerWatering", 40, 1, 100);

    SPRINKLER_PARTICLES = b
        .comment("Spawn water splash particles on the plants being watered.",
            "Set to false to disable them for every sprinkler (useful on low-end servers/clients).",
            "Players can also toggle them per sprinkler with a sneak + right-click.")
        .define("particles", true);

    b.pop();
    SPEC = b.build();
  }

  private AutomasticConfig() {}
}
