package fr.aethernode.automastic.machine.weathersensor;

import fr.aethernode.automastic.registry.ModBlocks;
import fr.aethernode.automastic.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu du capteur météo : pas d'inventaire (ni celui de la machine, ni celui du joueur), juste
 * les champs de réglage synchronisés (voir {@link TileWeatherSensor}).
 */
public class MenuWeatherSensor extends AbstractContainerMenu {

  private final TileWeatherSensor tile;

  /** Constructeur serveur. */
  public MenuWeatherSensor(int containerId, Inventory playerInventory, TileWeatherSensor tile) {
    super(ModMenus.WEATHER_SENSOR.get(), containerId);
    this.tile = tile;
    for (int i = 0; i < tile.getFieldCount(); i++) {
      final int id = i;
      this.addDataSlot(new DataSlot() {

        @Override
        public int get() {
          return MenuWeatherSensor.this.tile.getField(id);
        }

        @Override
        public void set(int value) {
          MenuWeatherSensor.this.tile.setField(id, value);
        }
      });
    }
  }

  /** Constructeur client : retrouve la machine à partir de la position reçue du serveur. */
  public MenuWeatherSensor(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
    this(containerId, playerInventory, resolveTile(playerInventory, data.readBlockPos()));
  }

  private static TileWeatherSensor resolveTile(Inventory playerInventory, BlockPos pos) {
    var be = playerInventory.player.level().getBlockEntity(pos);
    if (be instanceof TileWeatherSensor tile) {
      return tile;
    }
    throw new IllegalStateException("Capteur météo introuvable en " + pos + " pour ouvrir le menu");
  }

  public TileWeatherSensor getTile() {
    return this.tile;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY; // aucun slot
  }

  @Override
  public boolean stillValid(Player player) {
    var level = this.tile.getLevel();
    if (level == null) {
      return false;
    }
    return stillValid(ContainerLevelAccess.create(level, this.tile.getBlockPos()), player, ModBlocks.WEATHER_SENSOR.get());
  }
}
