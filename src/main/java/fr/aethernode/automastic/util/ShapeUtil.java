package fr.aethernode.automastic.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Génération de zones de positions. Réécrit pour Automastic (aucune dépendance externe).
 * Inspiré de UtilShape (Cyclic 1.12) ; le doublon de positions de squareHorizontalFull
 * de l'original est corrigé ici.
 */
public final class ShapeUtil {

  private ShapeUtil() {}

  /** Carré plein horizontal de rayon {@code radius} centré sur {@code center} (même Y). */
  public static List<BlockPos> squareHorizontalFull(BlockPos center, int radius) {
    List<BlockPos> shape = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
    for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
      for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
        shape.add(new BlockPos(x, center.getY(), z));
      }
    }
    return shape;
  }

  /** Contour (anneau) d'un carré horizontal de rayon {@code radius}. Rayon 0 = un seul bloc. */
  public static List<BlockPos> squareHorizontalHollow(BlockPos center, int radius) {
    List<BlockPos> shape = new ArrayList<>();
    if (radius <= 0) {
      shape.add(center);
      return shape;
    }
    int y = center.getY();
    int xMin = center.getX() - radius;
    int xMax = center.getX() + radius;
    int zMin = center.getZ() - radius;
    int zMax = center.getZ() + radius;
    for (int x = xMin; x <= xMax; x++) {
      shape.add(new BlockPos(x, y, zMin));
      shape.add(new BlockPos(x, y, zMax));
    }
    for (int z = zMin + 1; z < zMax; z++) {
      shape.add(new BlockPos(xMin, y, z));
      shape.add(new BlockPos(xMax, y, z));
    }
    return shape;
  }

  /**
   * Répète une forme sur {@code layers} couches supplémentaires, vers le haut (layers > 0)
   * ou vers le bas (layers < 0). La forme d'origine (couche 0) est conservée.
   */
  public static List<BlockPos> repeatVertically(List<BlockPos> base, int layers) {
    List<BlockPos> result = new ArrayList<>(base);
    if (layers == 0) {
      return result;
    }
    int step = layers > 0 ? 1 : -1;
    for (int i = 1; i <= Math.abs(layers); i++) {
      for (BlockPos p : base) {
        result.add(p.offset(0, i * step, 0));
      }
    }
    return result;
  }
}
