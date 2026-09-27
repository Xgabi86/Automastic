package fr.aethernode.automastic.machine.sprinkler;

import java.util.List;
import javax.annotation.Nullable;
import com.mojang.serialization.MapCodec;
import fr.aethernode.automastic.config.AutomasticConfig;
import fr.aethernode.automastic.core.TooltipUtil;
import fr.aethernode.automastic.registry.ModTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * Sprinkler : arrose les cultures autour de lui pour accélérer leur croissance.
 * <p>
 * Repris du sprinkler de Cyclic 1.12 (Lothrazar, MIT). Au lieu de demander de l'eau posée
 * dessous, il a un réservoir interne rempli par tuyau (capability fluide) ou avec un seau.
 * Il n'a ni interface, ni inventaire, ni énergie. Clic droit : affiche le niveau d'eau ;
 * clic droit avec un seau/contenant : remplit ; clic droit accroupi : particules on/off.
 * <p>
 * Ce bloc n'étend volontairement pas {@code BaseMachineBlock} : pas d'orientation, pas d'état
 * LIT, pas de menu à ouvrir.
 */
public class BlockSprinkler extends BaseEntityBlock {

  public static final MapCodec<BlockSprinkler> CODEC = simpleCodec(BlockSprinkler::new);

  /** Nom de la machine dans les clés de traduction ({@code block.automastic.sprinkler.*}). */
  private static final String NAME = "sprinkler";

  /** Demi-bloc du bas, avec 1/16 de marge sur les côtés (identique au AABB_BOTTOM_HALF de Cyclic). */
  private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D);

  public BlockSprinkler(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  /**
   * Description de l'item : ce que fait le sprinkler, et ses valeurs actuelles (rayon, fréquence,
   * pourcentage, eau). Les valeurs viennent de la config à chaque affichage : un changement de
   * config est donc pris en compte après redémarrage, sans rien à régénérer.
   */
  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, tooltip, flag);
    final int radius = TooltipUtil.value(AutomasticConfig.SPRINKLER_RADIUS);
    final int side = 2 * radius + 1;
    final int cost = TooltipUtil.value(AutomasticConfig.SPRINKLER_WATER_PER_WATERING);

    TooltipUtil.main(tooltip, NAME);
    TooltipUtil.detail(tooltip, NAME, "area", ChatFormatting.DARK_AQUA, side, side, radius);
    TooltipUtil.detail(tooltip, NAME, "rate", ChatFormatting.DARK_AQUA,
        TooltipUtil.value(AutomasticConfig.SPRINKLER_PERCENT_PER_WATERING),
        TooltipUtil.seconds(TooltipUtil.value(AutomasticConfig.SPRINKLER_TICKS_BETWEEN_WATERING)));
    if (cost > 0) {
      TooltipUtil.detail(tooltip, NAME, "water", ChatFormatting.BLUE, cost);
    }
    else {
      TooltipUtil.detail(tooltip, NAME, "no_water", ChatFormatting.BLUE);
    }
  }

  @Override
  protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }

  @SuppressWarnings("deprecation")
  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TileSprinkler(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    if (level.isClientSide) {
      return null; // rien à faire côté client : la croissance et les particules viennent du serveur
    }
    return createTickerHelper(type, ModTiles.SPRINKLER.get(), (lvl, pos, st, tile) -> tile.serverTick());
  }

  /**
   * Clic droit avec un seau ou tout contenant de fluide : remplit le réservoir avec l'eau du
   * contenant (ou, à l'inverse, en reprendrait, mais le réservoir refuse toute extraction).
   * Sans contenant, on laisse passer vers {@link #useWithoutItem}.
   */
  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    if (stack.isEmpty()) {
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (level.isClientSide) {
      // Le serveur tranche. On NE consulte PAS la capability du bloc ici : elle lit la config
      // serveur, qui n'est pas chargée côté client. On répond seulement si l'objet tenu est un
      // contenant de fluide, pour que la main s'anime et que le clic ne « traverse » pas.
      return FluidUtil.getFluidHandler(stack).isPresent()
          ? ItemInteractionResult.SUCCESS
          : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
      this.showWater(level, pos, player);
      return ItemInteractionResult.CONSUME;
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  /**
   * Clic droit à mains nues : affiche le niveau d'eau (ou dit qu'il en faut).
   * Clic droit accroupi : active/désactive les particules.
   */
  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide) {
      return InteractionResult.SUCCESS;
    }
    if (!(level.getBlockEntity(pos) instanceof TileSprinkler tile)) {
      return InteractionResult.PASS;
    }
    if (player.isShiftKeyDown()) {
      tile.toggleSpawnParticles();
      player.displayClientMessage(
          Component.translatable("block.automastic.sprinkler.message.particles." + tile.isSpawningParticles()), true);
    }
    else {
      this.showWater(level, pos, player);
    }
    return InteractionResult.CONSUME;
  }

  /** Message d'action bar : niveau du réservoir, ou « pas d'eau » si le sprinkler est à sec. */
  private void showWater(Level level, BlockPos pos, Player player) {
    if (!(level.getBlockEntity(pos) instanceof TileSprinkler tile)) {
      return;
    }
    if (!tile.isRunning()) {
      player.displayClientMessage(Component.translatable("block.automastic.sprinkler.message.empty"), true);
      return;
    }
    if (AutomasticConfig.SPRINKLER_WATER_PER_WATERING.get() <= 0) {
      player.displayClientMessage(Component.translatable("block.automastic.sprinkler.message.no_water_needed"), true);
      return;
    }
    player.displayClientMessage(Component.translatable("block.automastic.sprinkler.message.water",
        tile.getWaterAmount(), tile.getWaterCapacity()), true);
  }
}
