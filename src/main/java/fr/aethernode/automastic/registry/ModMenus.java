package fr.aethernode.automastic.registry;

import java.util.function.Supplier;
import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.machine.harvester.MenuHarvester;
import fr.aethernode.automastic.machine.weathersensor.MenuWeatherSensor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, Automastic.MODID);

  public static final Supplier<MenuType<MenuHarvester>> HARVESTER = MENUS.register("harvester",
      () -> IMenuTypeExtension.create(MenuHarvester::new));

  public static final Supplier<MenuType<MenuWeatherSensor>> WEATHER_SENSOR = MENUS.register("weather_sensor",
      () -> IMenuTypeExtension.create(MenuWeatherSensor::new));

  private ModMenus() {}
}
