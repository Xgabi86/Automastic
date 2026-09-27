package fr.aethernode.automastic.core;

import javax.annotation.Nullable;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Bloc de base des machines : orientation (6 directions), état allumé, ticker serveur,
 * ouverture du menu, et vidage de l'inventaire à la destruction.
 */
public abstract class BaseMachineBlock extends BaseEntityBlock {

  public static final DirectionProperty FACING = BlockStateProperties.FACING;
  public static final BooleanProperty LIT = BlockStateProperties.LIT;

  protected BaseMachineBlock(Properties properties) {
    super(properties);
    this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
  }

  /**
   * Codec du bloc (obligatoire depuis 1.20.5 pour BaseEntityBlock).
   * Chaque bloc concret doit retourner {@code simpleCodec(MonBloc::new)} depuis un champ statique.
   */
  @Override
  protected abstract MapCodec<? extends BaseEntityBlock> codec();

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, LIT);
  }

  /** Le bloc regarde vers le joueur qui le pose (6 directions, comme Cyclic). */
  @Override
  @Nullable
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
  }

  @SuppressWarnings("deprecation")
  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide) {
      return InteractionResult.SUCCESS;
    }
    BlockEntity be = level.getBlockEntity(pos);
    if (be instanceof MenuProvider provider && player instanceof ServerPlayer sp) {
      sp.openMenu(provider, pos);
      return InteractionResult.CONSUME;
    }
    return InteractionResult.PASS;
  }

  /**
   * Ticker serveur uniquement : les machines ne font rien côté client.
   * Les sous-classes fournissent leur méthode de tick statique.
   */
  @Nullable
  protected static <T extends BlockEntity, E extends BaseMachineTile> BlockEntityTicker<T> serverTicker(
      Level level, BlockEntityType<T> given, BlockEntityType<E> expected) {
    if (level.isClientSide) {
      return null;
    }
    return createTickerHelper(given, expected, (lvl, pos, st, tile) -> tile.serverTick());
    // signature : createTickerHelper(typeDonné, typeAttendu, BlockEntityTicker<E>) -> BlockEntityTicker<A> ou null
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock())) {
      BlockEntity be = level.getBlockEntity(pos);
      if (be instanceof BaseMachineTile tile) {
        IItemHandler handler = tile.getInventoryForDrops();
        if (handler != null) {
          for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
              Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
          }
        }
      }
    }
    super.onRemove(state, level, pos, newState, movedByPiston);
  }

  @Override
  public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
  }
}
