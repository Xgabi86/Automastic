package fr.aethernode.automastic.core;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import javax.annotation.Nullable;

/**
 * Menu de base : inventaire du joueur, synchro des champs entiers (côté client) et de l'énergie.
 * <p>
 * Note : vanilla transmet chaque DataSlot sur 16 bits. Une énergie de 64 000 FE ne tient pas
 * dans un short signé : on la découpe donc en deux DataSlots (16 bits bas / 16 bits haut).
 */
public abstract class BaseMachineMenu<T extends BaseMachineTile> extends AbstractContainerMenu {

  protected final T tile;
  protected final Inventory playerInventory;
  private final Block block;

  protected BaseMachineMenu(@Nullable MenuType<?> type, int containerId, Inventory playerInventory, T tile, Block block) {
    super(type, containerId);
    this.tile = tile;
    this.playerInventory = playerInventory;
    this.block = block;
  }

  public T getTile() {
    return this.tile;
  }

  /** Synchronise tous les champs de la machine (serveur → client). */
  protected void trackFields() {
    for (int i = 0; i < this.tile.getFieldCount(); i++) {
      final int id = i;
      this.addDataSlot(new DataSlot() {

        @Override
        public int get() {
          return BaseMachineMenu.this.tile.getField(id);
        }

        @Override
        public void set(int value) {
          BaseMachineMenu.this.tile.setField(id, value);
        }
      });
    }
  }

  /** Synchronise l'énergie (2 DataSlots de 16 bits). */
  protected void trackEnergy() {
    this.addDataSlot(new DataSlot() {

      @Override
      public int get() {
        return BaseMachineMenu.this.tile.getEnergyStored() & 0xFFFF;
      }

      @Override
      public void set(int value) {
        int current = BaseMachineMenu.this.tile.getEnergyStored();
        BaseMachineMenu.this.tile.setEnergyClient((current & 0xFFFF0000) | (value & 0xFFFF));
      }
    });
    this.addDataSlot(new DataSlot() {

      @Override
      public int get() {
        return (BaseMachineMenu.this.tile.getEnergyStored() >>> 16) & 0xFFFF;
      }

      @Override
      public void set(int value) {
        int current = BaseMachineMenu.this.tile.getEnergyStored();
        BaseMachineMenu.this.tile.setEnergyClient((current & 0x0000FFFF) | ((value & 0xFFFF) << 16));
      }
    });
  }

  /** Ajoute l'inventaire (3 rangées) + la hotbar du joueur. */
  protected void addPlayerSlots(int startX, int startY) {
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        this.addSlot(new net.minecraft.world.inventory.Slot(this.playerInventory, col + row * 9 + 9, startX + col * 18, startY + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      this.addSlot(new net.minecraft.world.inventory.Slot(this.playerInventory, col, startX + col * 18, startY + 58));
    }
  }

  @Override
  public boolean stillValid(Player player) {
    var level = this.tile.getLevel();
    if (level == null) {
      return false;
    }
    return stillValid(ContainerLevelAccess.create(level, this.tile.getBlockPos()), player, this.block);
  }
}
