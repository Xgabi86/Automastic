package fr.aethernode.automastic.machine.harvester;

import java.util.List;
import javax.annotation.Nullable;
import com.mojang.serialization.MapCodec;
import fr.aethernode.automastic.config.AutomasticConfig;
import fr.aethernode.automastic.core.BaseMachineBlock;
import fr.aethernode.automastic.core.TooltipUtil;
import fr.aethernode.automastic.registry.ModTiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlockHarvester extends BaseMachineBlock {

  public static final MapCodec<BlockHarvester> CODEC = simpleCodec(BlockHarvester::new);

  /** Nom de la machine dans les clés de traduction ({@code block.automastic.harvester.*}). */
  private static final String NAME = "harvester";

  public BlockHarvester(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TileHarvester(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    return serverTicker(level, type, ModTiles.HARVESTER.get());
  }

  /**
   * Description de l'item : ce que fait la moissonneuse et ses valeurs actuelles (énergie,
   * cadence). Lues dans la config à chaque affichage, donc à jour après un redémarrage.
   */
  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, tooltip, flag);
    final int cost = TooltipUtil.value(AutomasticConfig.HARVESTER_ENERGY_PER_PLANT);
    final int side = 2 * TileHarvester.MAX_RADIUS + 1;

    TooltipUtil.main(tooltip, NAME);
    TooltipUtil.detail(tooltip, NAME, "area", ChatFormatting.DARK_AQUA, side, side, TileHarvester.MAX_HEIGHT);
    TooltipUtil.detail(tooltip, NAME, "rate", ChatFormatting.DARK_AQUA,
        TooltipUtil.seconds(TooltipUtil.value(AutomasticConfig.HARVESTER_TICKS_BETWEEN_ACTIONS)),
        TooltipUtil.seconds(TooltipUtil.value(AutomasticConfig.HARVESTER_TICKS_BETWEEN_AREA_PASSES)));
    if (cost > 0) {
      TooltipUtil.detail(tooltip, NAME, "energy", ChatFormatting.RED, cost);
    }
    else {
      TooltipUtil.detail(tooltip, NAME, "no_energy", ChatFormatting.RED);
    }
    TooltipUtil.detail(tooltip, NAME, "output", ChatFormatting.DARK_GRAY, TileHarvester.INVENTORY_SIZE);
  }
}
