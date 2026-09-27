package fr.aethernode.automastic;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import fr.aethernode.automastic.config.AutomasticConfig;
import fr.aethernode.automastic.net.PacketRegistry;
import fr.aethernode.automastic.registry.ModBlocks;
import fr.aethernode.automastic.registry.ModItems;
import fr.aethernode.automastic.registry.ModMenus;
import fr.aethernode.automastic.registry.ModTabs;
import fr.aethernode.automastic.registry.ModTiles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Point d'entrée d'Automastic.
 * <p>
 * Pour ajouter une nouvelle machine : créer un package {@code machine/xxx} puis
 * enregistrer ses éléments dans {@link ModBlocks}, {@link ModItems}, {@link ModTiles}
 * et {@link ModMenus} (une ligne chacun). Le socle {@code core/} ne change pas.
 */
@Mod(Automastic.MODID)
public class Automastic {

  public static final String MODID = "automastic";
  public static final Logger LOGGER = LogUtils.getLogger();

  public Automastic(IEventBus bus, ModContainer container) {
    container.registerConfig(ModConfig.Type.SERVER, AutomasticConfig.SPEC);
    ModBlocks.BLOCKS.register(bus);
    ModItems.ITEMS.register(bus);
    ModTiles.TILES.register(bus);
    ModMenus.MENUS.register(bus);
    ModTabs.TABS.register(bus);
    bus.addListener(PacketRegistry::register);
  }
}
