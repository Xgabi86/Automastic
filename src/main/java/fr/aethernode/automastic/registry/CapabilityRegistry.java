package fr.aethernode.automastic.registry;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.core.BaseMachineTile;
import fr.aethernode.automastic.machine.sprinkler.TileSprinkler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Expose les capabilities de toutes les machines Automastic.
 * <p>
 * Générique : toute machine dérivée de {@link BaseMachineTile} est branchée automatiquement.
 * C'est cette capability standard NeoForge qui rend le harvester compatible avec les
 * hoppers, et avec les tuyaux Mekanism, XNet, Pipez, etc. (ils utilisent tous
 * {@code Capabilities.ItemHandler.BLOCK}).
 * <p>
 * Le sprinkler n'étend pas {@link BaseMachineTile} (ni énergie, ni inventaire) : sa capability
 * fluide est enregistrée à part. Elle le rend compatible avec les tuyaux à fluide de Mekanism,
 * Pipez, XNet, etc. (ils utilisent tous {@code Capabilities.FluidHandler.BLOCK}).
 */
@EventBusSubscriber(modid = Automastic.MODID)
public final class CapabilityRegistry {

  private CapabilityRegistry() {}

  @SubscribeEvent
  public static void register(RegisterCapabilitiesEvent event) {
    for (var type : ModTiles.TILES.getEntries()) {
      event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type.get(), (be, side) -> {
        return be instanceof BaseMachineTile tile ? tile.getItemHandler(side) : null;
      });
      event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type.get(), (be, side) -> {
        return be instanceof BaseMachineTile tile ? tile.getEnergyHandler(side) : null;
      });
    }
    event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModTiles.SPRINKLER.get(),
        (be, side) -> be.getFluidHandler(side));
  }
}
