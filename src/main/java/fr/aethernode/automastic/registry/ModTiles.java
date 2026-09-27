package fr.aethernode.automastic.registry;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.machine.harvester.TileHarvester;
import fr.aethernode.automastic.machine.sprinkler.TileSprinkler;
import fr.aethernode.automastic.machine.weathersensor.TileWeatherSensor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTiles {

  public static final DeferredRegister<BlockEntityType<?>> TILES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Automastic.MODID);

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileHarvester>> HARVESTER =
      TILES.register("harvester",
          () -> BlockEntityType.Builder.of(TileHarvester::new, ModBlocks.HARVESTER.get()).build(null));

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileSprinkler>> SPRINKLER =
      TILES.register("sprinkler",
          () -> BlockEntityType.Builder.of(TileSprinkler::new, ModBlocks.SPRINKLER.get()).build(null));

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileWeatherSensor>> WEATHER_SENSOR =
      TILES.register("weather_sensor",
          () -> BlockEntityType.Builder.of(TileWeatherSensor::new, ModBlocks.WEATHER_SENSOR.get()).build(null));

  private ModTiles() {}
}
