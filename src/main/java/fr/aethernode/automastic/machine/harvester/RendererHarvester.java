package fr.aethernode.automastic.machine.harvester;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Preview de la zone : contour de la surface récoltée, activable dans l'interface.
 * <p>
 * Le PoseStack d'un BlockEntityRenderer est relatif à la position du bloc : chaque boîte est
 * donc dessinée à (pos cible − pos du harvester).
 */
public class RendererHarvester implements BlockEntityRenderer<TileHarvester> {

  public RendererHarvester(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(TileHarvester tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    if (!tile.isPreviewVisible()) {
      return;
    }
    VertexConsumer lines = buffers.getBuffer(RenderType.lines());
    BlockPos origin = tile.getBlockPos();
    for (BlockPos target : tile.getPreviewShape()) {
      AABB box = new AABB(
          target.getX() - origin.getX(), target.getY() - origin.getY(), target.getZ() - origin.getZ(),
          target.getX() - origin.getX() + 1, target.getY() - origin.getY() + 1, target.getZ() - origin.getZ() + 1);
      LevelRenderer.renderLineBox(pose, lines, box, 0.2F, 1.0F, 0.3F, 0.9F);
    }
  }

  /** La zone peut être très loin du bloc : on force le rendu même si le bloc n'est pas à l'écran. */
  @Override
  public AABB getRenderBoundingBox(TileHarvester tile) {
    return tile.getRenderBox();
  }

  @Override
  public boolean shouldRenderOffScreen(TileHarvester tile) {
    return true;
  }

  /** Distance de rendu : la zone peut s'étendre jusqu'à 25 blocs + hauteur. */
  @Override
  public int getViewDistance() {
    return 96;
  }
}
