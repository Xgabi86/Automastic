package fr.aethernode.automastic.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Portage 1.21 de {@code UtilInventoryTransfer.dumpToIInventory} et
 * {@code TileEntityHarvester.setOutputItems} du Cyclic 1.12 :
 * on range les drops dans l'inventaire, et ce qui ne rentre pas est renvoyé à l'appelant.
 */
public final class InventoryUtil {

  private InventoryUtil() {}

  /**
   * Insère chaque stack dans {@code handler} (fusion avec les stacks existants d'abord,
   * puis slots vides), et retourne ce qui n'a PAS pu être rangé.
   * Ne modifie pas les stacks passés en argument.
   */
  public static List<ItemStack> insertAll(IItemHandler handler, List<ItemStack> stacks) {
    List<ItemStack> remaining = new ArrayList<>();
    for (ItemStack original : stacks) {
      ItemStack left = ItemHandlerHelper.insertItemStacked(handler, original.copy(), false);
      if (!left.isEmpty()) {
        remaining.add(left);
      }
    }
    return remaining;
  }

  /**
   * Vrai si TOUTES les stacks pourraient être rangées dans {@code handler} (fusion + slots vides),
   * sans rien modifier réellement dans {@code handler}. Utilisé pour décider d'annuler une
   * récolte plutôt que de ranger partiellement et jeter le surplus au sol.
   * <p>
   * On ne peut pas enchaîner plusieurs {@code insertItem(simulate = true)} directement sur
   * {@code handler} en supposant qu'ils « se voient » entre eux : chaque appel simulé part du
   * même état réel et ignore les autres simulations, donc deux stacks pourraient sembler tenir
   * chacune dans le même slot vide alors qu'une seule y tient réellement. La solution fiable est
   * de rejouer une insertion réelle (non simulée) sur une copie jetable du handler ; l'original
   * n'est jamais touché.
   */
  public static boolean canInsertAll(IItemHandler handler, List<ItemStack> stacks) {
    ItemStackHandler shadow = new ItemStackHandler(handler.getSlots());
    for (int i = 0; i < handler.getSlots(); i++) {
      shadow.setStackInSlot(i, handler.getStackInSlot(i).copy());
    }
    for (ItemStack original : stacks) {
      ItemStack left = ItemHandlerHelper.insertItemStacked(shadow, original.copy(), false);
      if (!left.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /** Comme le 1.12 : ce qui ne rentre pas est lâché au sol au-dessus de {@code pos}. */
  public static void dropAbove(Level level, BlockPos pos, List<ItemStack> stacks) {
    for (ItemStack stack : stacks) {
      if (stack.isEmpty()) {
        continue;
      }
      ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack.copy());
      entity.setDefaultPickUpDelay();
      level.addFreshEntity(entity);
    }
  }

  /**
   * Vrai si aucun slot ne peut accepter quoi que ce soit : tous pleins.
   * Équivalent de {@code isInventoryFull()} du 1.12.
   */
  public static boolean isFull(IItemHandler handler) {
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack s = handler.getStackInSlot(i);
      if (s.isEmpty() || s.getCount() < handler.getSlotLimit(i) && s.getCount() < s.getMaxStackSize()) {
        return false;
      }
    }
    return true;
  }
}
