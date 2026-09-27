package fr.aethernode.automastic.registry;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.machine.harvester.BlockHarvester;
import fr.aethernode.automastic.machine.sprinkler.BlockSprinkler;
import fr.aethernode.automastic.machine.weathersensor.BlockWeatherSensor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Automastic.MODID);

  /** Propriétés communes des machines (métal, nécessite un outil). */
  private static BlockBehaviour.Properties machine() {
    return BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(3.0F, 6.0F)
        .sound(SoundType.METAL)
        .requiresCorrectToolForDrops();
  }

  public static final DeferredBlock<Block> MACHINE_FRAME = BLOCKS.register("machine_frame",
      () -> new Block(machine()));

  public static final DeferredBlock<BlockHarvester> HARVESTER = BLOCKS.register("harvester",
      () -> new BlockHarvester(machine()));

  /**
   * Le sprinkler n'est pas un cube plein (demi-bloc + rotor) : {@code noOcclusion} évite que les
   * faces voisines soient masquées à tort.
   */
  public static final DeferredBlock<BlockSprinkler> SPRINKLER = BLOCKS.register("sprinkler",
      () -> new BlockSprinkler(machine().noOcclusion()));

  public static final DeferredBlock<BlockWeatherSensor> WEATHER_SENSOR = BLOCKS.register("weather_sensor",
      () -> new BlockWeatherSensor(machine().lightLevel(state -> state.getValue(BlockWeatherSensor.LIT) ? 8 : 0)));

  private ModBlocks() {}
}
