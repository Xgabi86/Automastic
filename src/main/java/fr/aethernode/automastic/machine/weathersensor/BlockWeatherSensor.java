package fr.aethernode.automastic.machine.weathersensor;

import java.util.List;
import javax.annotation.Nullable;
import com.mojang.serialization.MapCodec;
import fr.aethernode.automastic.core.TooltipUtil;
import fr.aethernode.automastic.registry.ModTiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Capteur météo / heure : bloc statique (cube plein, pas d'orientation) qui émet un signal
 * redstone (force réglable) sur toutes ses faces quand sa condition est remplie.
 * <p>
 * Tous les réglages se font désormais depuis une vraie interface (clic droit l'ouvre) :
 * mode (météo / heure), météos surveillées ou intervalle horaire, force du signal et
 * inversion de la logique. Voir {@link ScreenWeatherSensor}.
 */
public class BlockWeatherSensor extends BaseEntityBlock {

  public static final MapCodec<BlockWeatherSensor> CODEC = simpleCodec(BlockWeatherSensor::new);
  public static final BooleanProperty LIT = BlockStateProperties.LIT;

  private static final String NAME = "weather_sensor";

  public BlockWeatherSensor(Properties properties) {
    super(properties);
    this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(LIT);
  }

  @SuppressWarnings("deprecation")
  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TileWeatherSensor(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    if (level.isClientSide) {
      return null;
    }
    return createTickerHelper(type, ModTiles.WEATHER_SENSOR.get(), (lvl, pos, st, tile) -> tile.serverTick());
  }

  // ------------------------------------------------------------- redstone

  @SuppressWarnings("deprecation")
  @Override
  public boolean isSignalSource(BlockState state) {
    return true;
  }

  /** Même signal sur toutes les faces : la force réglée dans l'interface si la condition est remplie. */
  @SuppressWarnings("deprecation")
  @Override
  public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    if (level.getBlockEntity(pos) instanceof TileWeatherSensor tile) {
      return tile.getSignal();
    }
    return 0;
  }

  @SuppressWarnings("deprecation")
  @Override
  public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return this.getSignal(state, level, pos, direction);
  }

  // ------------------------------------------------------------- interaction

  /** Clic droit : ouvre l'interface de configuration (plus de réglage au clic). */
  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide) {
      return InteractionResult.SUCCESS;
    }
    if (level.getBlockEntity(pos) instanceof MenuProvider provider && player instanceof ServerPlayer sp) {
      sp.openMenu(provider, pos);
      return InteractionResult.CONSUME;
    }
    return InteractionResult.PASS;
  }

  // --------------------------------------------------------------- tooltip

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, tooltip, flag);
    TooltipUtil.main(tooltip, NAME);
    tooltip.add(Component.translatable("block.automastic.weather_sensor.tooltip.usage1").withStyle(ChatFormatting.DARK_AQUA));
  }
}
