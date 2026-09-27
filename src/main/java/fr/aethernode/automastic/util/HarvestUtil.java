package fr.aethernode.automastic.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Logique de récolte : on ne casse pas la culture, on la remet à l'âge minimum
 * (elle « repousse » sans replanter). Fonctionne avec toute culture qui étend
 * {@link CropBlock}, y compris Mystical Agriculture, sans dépendre de ce mod.
 * <p>
 * Contrairement au Cyclic 1.21 d'origine, cette classe <b>retourne</b> les drops au lieu de les
 * lâcher dans le monde : c'est l'appelant qui décide de les ranger dans l'inventaire.
 * <p>
 * Pourquoi c'est compatible Mystical Agriculture : sa culture étend {@code CropBlock}, et son
 * {@code getDrops} lit {@code LootContextParams.ORIGIN} pour connaître le bloc SOUS la plante
 * (farmland d'essence → quantité et graines secondaires). {@link Block#getDrops} renseigne cet
 * ORIGIN, donc les bonus sont conservés.
 */
public final class HarvestUtil {

  private HarvestUtil() {}

  /** Résultat d'une tentative de récolte. */
  public record Result(boolean harvested, List<ItemStack> drops) {

    public static final Result NOTHING = new Result(false, Collections.emptyList());
  }

  /**
   * Tente de récolter la culture à {@code pos}.
   *
   * @return {@link Result#NOTHING} si rien n'a été récolté (pas une culture, pas mûre, etc.).
   */
  public static Result harvest(ServerLevel level, BlockPos pos) {
    BlockState state = level.getBlockState(pos);
    if (state.isAir()) {
      return Result.NOTHING;
    }
    Block block = state.getBlock();
    // Les tiges (citrouille/melon) donnent leur fruit AUTOUR d'elles : on ne les touche jamais.
    if (block instanceof StemBlock) {
      return Result.NOTHING;
    }
    // Voie principale : toute culture à âge (vanilla, Mystical Agriculture, etc.)
    if (block instanceof CropBlock crop) {
      return harvestCrop(level, pos, state, crop);
    }
    // Voie secondaire : bloc non-CropBlock avec propriété "age" (ex. nether wart, cacao)
    return harvestByAgeProperty(level, pos, state, block);
  }

  private static Result harvestCrop(ServerLevel level, BlockPos pos, BlockState state, CropBlock crop) {
    if (!crop.isMaxAge(state)) {
      return Result.NOTHING; // pas mûre
    }
    // getDrops peut renvoyer une liste immuable : on copie
    List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null));
    removeOneSeed(drops, seedOf(level, pos, state, crop));
    // La culture repart de l'âge 0 : pas de casse, pas de replantage
    level.setBlockAndUpdate(pos, crop.getStateForAge(0));
    return new Result(true, drops);
  }

  private static Result harvestByAgeProperty(ServerLevel level, BlockPos pos, BlockState state, Block block) {
    IntegerProperty ageProp = findAgeProperty(state);
    if (ageProp == null) {
      return Result.NOTHING;
    }
    int min = Collections.min(ageProp.getPossibleValues());
    int max = Collections.max(ageProp.getPossibleValues());
    if (min == max || state.getValue(ageProp) < max) {
      return Result.NOTHING; // pas mûr, ou propriété non pertinente
    }
    List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null));
    // Bloc sans CropBlock : sa propre forme d'item est la meilleure estimation de la « graine »
    // (nether wart, cacao...). removeOneSeed ne retire rien si ce n'est pas dans les drops.
    removeOneSeed(drops, block.asItem());
    level.setBlockAndUpdate(pos, state.setValue(ageProp, min));
    return new Result(true, drops);
  }

  /** La graine de la culture : ce que le joueur récupérerait en la clonant. */
  @Nullable
  private static Item seedOf(ServerLevel level, BlockPos pos, BlockState state, CropBlock crop) {
    ItemStack clone = crop.getCloneItemStack(level, pos, state);
    return clone.isEmpty() ? null : clone.getItem();
  }

  /**
   * Retire UNE graine des drops (elle sert à la « repousse »), comme le harvester d'origine.
   * On ne la retire que s'il y a plus d'un drop, pour ne jamais rendre une récolte vide
   * (cultures qui ne redonnent que leur graine).
   */
  private static void removeOneSeed(List<ItemStack> drops, @Nullable Item seed) {
    if (seed == null || drops.size() <= 1) {
      return;
    }
    for (ItemStack drop : drops) {
      if (!drop.isEmpty() && drop.getItem() == seed) {
        drop.shrink(1);
        break;
      }
    }
    drops.removeIf(ItemStack::isEmpty);
  }

  @Nullable
  private static IntegerProperty findAgeProperty(BlockState state) {
    for (Property<?> p : state.getProperties()) {
      if (p instanceof IntegerProperty ip && "age".equals(p.getName())) {
        return ip;
      }
    }
    return null;
  }

  /** Identifiant lisible d'un bloc, pour les logs. */
  public static String idOf(BlockState state) {
    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
  }
}
