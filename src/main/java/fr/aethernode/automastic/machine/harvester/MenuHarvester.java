package fr.aethernode.automastic.machine.harvester;

import fr.aethernode.automastic.core.BaseMachineMenu;
import fr.aethernode.automastic.registry.ModBlocks;
import fr.aethernode.automastic.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu du harvester : 27 slots de sortie (le joueur peut retirer, pas déposer) + inventaire joueur.
 * Disposition reprise du Cyclic 1.12 (3 rangées de 9, départ x=8).
 */
public class MenuHarvester extends BaseMachineMenu<TileHarvester> {

  public static final int OUT_X = 8;
  public static final int OUT_Y = 72;
  public static final int PLAYER_X = 8;
  public static final int PLAYER_Y = 144;
  private static final int OUT_SLOTS = TileHarvester.INVENTORY_SIZE;

  /** Constructeur serveur. */
  public MenuHarvester(int containerId, Inventory playerInventory, TileHarvester tile) {
    super(ModMenus.HARVESTER.get(), containerId, playerInventory, tile, ModBlocks.HARVESTER.get());
    this.trackFields();
    this.trackEnergy();
    IItemHandler out = tile.getInventory();
    for (int i = 0; i < OUT_SLOTS; i++) {
      this.addSlot(new OutputSlot(out, i, OUT_X + (i % 9) * 18, OUT_Y + (i / 9) * 18));
    }
    this.addPlayerSlots(PLAYER_X, PLAYER_Y);
  }

  /** Constructeur client : retrouve la machine à partir de la position reçue du serveur. */
  public MenuHarvester(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
    this(containerId, playerInventory, resolveTile(playerInventory, data.readBlockPos()));
  }

  private static TileHarvester resolveTile(Inventory playerInventory, BlockPos pos) {
    var be = playerInventory.player.level().getBlockEntity(pos);
    if (be instanceof TileHarvester tile) {
      return tile;
    }
    throw new IllegalStateException("Harvester introuvable en " + pos + " pour ouvrir le menu");
  }

  /** Slot de sortie : le joueur peut retirer mais pas déposer. */
  private static class OutputSlot extends SlotItemHandler {

    OutputSlot(IItemHandler handler, int index, int x, int y) {
      super(handler, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return false;
    }
  }

  /**
   * Shift-clic : depuis la machine vers le joueur uniquement (on ne peut rien y déposer).
   */
  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    Slot slot = this.slots.get(index);
    if (slot == null || !slot.hasItem()) {
      return ItemStack.EMPTY;
    }
    // seuls les slots de la machine (0..OUT_SLOTS-1) sont déplaçables vers le joueur
    if (index >= OUT_SLOTS) {
      return ItemStack.EMPTY;
    }
    ItemStack inSlot = slot.getItem();
    ItemStack copy = inSlot.copy();
    if (!this.moveItemStackTo(inSlot, OUT_SLOTS, this.slots.size(), true)) {
      return ItemStack.EMPTY;
    }
    if (inSlot.isEmpty()) {
      slot.setByPlayer(ItemStack.EMPTY);
    }
    else {
      slot.setChanged();
    }
    return copy;
  }
}
