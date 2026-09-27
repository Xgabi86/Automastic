package fr.aethernode.automastic.registry;

import fr.aethernode.automastic.Automastic;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {

  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Automastic.MODID);

  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
      () -> CreativeModeTab.builder()
          .title(Component.translatable("itemGroup." + Automastic.MODID))
          .icon(() -> new ItemStack(ModItems.HARVESTER.get()))
          .displayItems((params, output) -> {
            output.accept(ModItems.MACHINE_FRAME.get());
            output.accept(ModItems.HARVESTER.get());
            output.accept(ModItems.SPRINKLER.get());
            output.accept(ModItems.WEATHER_SENSOR.get());
          })
          .build());

  private ModTabs() {}
}
