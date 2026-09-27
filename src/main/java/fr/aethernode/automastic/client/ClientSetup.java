package fr.aethernode.automastic.client;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.machine.harvester.RendererHarvester;
import fr.aethernode.automastic.machine.harvester.ScreenHarvester;
import fr.aethernode.automastic.machine.sprinkler.RendererSprinkler;
import fr.aethernode.automastic.machine.weathersensor.ScreenWeatherSensor;
import fr.aethernode.automastic.registry.ModMenus;
import fr.aethernode.automastic.registry.ModTiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Enregistrements côté client uniquement (écrans et renderers). Une ligne par machine. */
@EventBusSubscriber(modid = Automastic.MODID, value = Dist.CLIENT)
public final class ClientSetup {

  private ClientSetup() {}

  @SubscribeEvent
  public static void registerScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenus.HARVESTER.get(), ScreenHarvester::new);
    event.register(ModMenus.WEATHER_SENSOR.get(), ScreenWeatherSensor::new);
  }

  @SubscribeEvent
  public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(ModTiles.HARVESTER.get(), RendererHarvester::new);
    event.registerBlockEntityRenderer(ModTiles.SPRINKLER.get(), RendererSprinkler::new);
  }

  /** Charge les modèles qui ne sont attachés à aucun bloc (ici, le rotor animé du sprinkler). */
  @SubscribeEvent
  public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
    event.register(RendererSprinkler.ROTOR_MODEL);
  }
}
