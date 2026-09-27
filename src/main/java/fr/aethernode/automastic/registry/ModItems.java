package fr.aethernode.automastic.registry;

import java.util.List;
import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.core.TooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Automastic.MODID);

  /**
   * Le châssis n'a pas de classe de bloc dédiée (simple {@code Block}) et rien de configurable :
   * sa description, purement statique, est portée par l'item.
   */
  public static final DeferredItem<BlockItem> MACHINE_FRAME = ITEMS.register("machine_frame",
      () -> new BlockItem(ModBlocks.MACHINE_FRAME.get(), new Item.Properties()) {

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
          super.appendHoverText(stack, context, tooltip, flag);
          TooltipUtil.main(tooltip, "machine_frame");
        }
      });

  public static final DeferredItem<BlockItem> HARVESTER = ITEMS.register("harvester",
      () -> new BlockItem(ModBlocks.HARVESTER.get(), new Item.Properties()));

  public static final DeferredItem<BlockItem> SPRINKLER = ITEMS.register("sprinkler",
      () -> new BlockItem(ModBlocks.SPRINKLER.get(), new Item.Properties()));

  public static final DeferredItem<BlockItem> WEATHER_SENSOR = ITEMS.register("weather_sensor",
      () -> new BlockItem(ModBlocks.WEATHER_SENSOR.get(), new Item.Properties()));

  private ModItems() {}
}
