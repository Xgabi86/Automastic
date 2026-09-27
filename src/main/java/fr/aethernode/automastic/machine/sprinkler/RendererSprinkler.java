package fr.aethernode.automastic.machine.sprinkler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.aethernode.automastic.Automastic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Rotor animé du sprinkler (équivalent du {@code SprinklerTESR} de Cyclic 1.12).
 * <p>
 * Le socle est rendu par le blockstate comme n'importe quel bloc ; seul le rotor (les barres en
 * croix) tourne, il est donc un modèle séparé chargé via {@code ModelEvent.RegisterAdditional}
 * (voir {@code ClientSetup}) et dessiné ici par-dessus, tourné autour de l'axe Y.
 * <p>
 * Contrairement à Cyclic, l'angle dépend du temps de jeu et non de l'horloge système : le rotor
 * s'arrête quand le jeu est en pause et reste synchronisé entre tous les sprinklers.
 */
public class RendererSprinkler implements BlockEntityRenderer<TileSprinkler> {

  /** Modèle du rotor : {@code assets/automastic/models/block/sprinkler_rotor.json}. */
  public static final ModelResourceLocation ROTOR_MODEL =
      ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Automastic.MODID, "block/sprinkler_rotor"));

  /** Degrés par tick. Cyclic faisait 360° toutes les 3,6 s (1° / 10 ms), soit ~1 tour toutes les 72 ticks. */
  private static final float DEGREES_PER_TICK = 5.0F;
  /** Ticks pour un tour complet (360 / 5). */
  private static final int TICKS_PER_TURN = 72;

  public RendererSprinkler(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(TileSprinkler tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    var level = tile.getLevel();
    if (level == null) {
      return;
    }
    BakedModel rotor = Minecraft.getInstance().getModelManager().getModel(ROTOR_MODEL);
    if (rotor == null || rotor == Minecraft.getInstance().getModelManager().getMissingModel()) {
      return; // modèle non chargé : on n'affiche rien plutôt que le cube « missing »
    }
    pose.pushPose();
    // rotation autour du centre du bloc, uniquement quand de l'eau alimente le sprinkler
    if (tile.isAnimating()) {
      // Le modulo se fait sur le long AVANT de passer en float : (gameTime + partialTick) en float
      // perd toute sa précision sur un monde ancien (au-delà de ~1 million de ticks, les pas
      // deviennent plus gros qu'une image et le rotor « saute » à quelques FPS).
      float turnTicks = (float) Math.floorMod(level.getGameTime(), (long) TICKS_PER_TURN) + partialTick;
      float angle = turnTicks * DEGREES_PER_TICK;
      pose.translate(0.5, 0.0, 0.5);
      pose.mulPose(Axis.YP.rotationDegrees(angle));
      pose.translate(-0.5, 0.0, -0.5);
    }
    Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
        pose.last(),
        buffers.getBuffer(RenderType.cutout()),
        null,
        rotor,
        1.0F, 1.0F, 1.0F,
        light, overlay);
    pose.popPose();
  }

  @Override
  public AABB getRenderBoundingBox(TileSprinkler tile) {
    return tile.getRenderBox();
  }
}
