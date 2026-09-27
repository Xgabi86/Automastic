package fr.aethernode.automastic.machine.weathersensor;

import javax.annotation.Nullable;
import fr.aethernode.automastic.core.IFieldHolder;
import fr.aethernode.automastic.registry.ModTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Capteur météo / horloge : émet un signal redstone (force réglable) sur toutes ses faces quand
 * une condition configurable est remplie, avec une option pour inverser la logique.
 * <p>
 * Deux modes de condition, réglés depuis une vraie interface (clic droit ouvre le menu) :
 * <ul>
 *   <li>{@link #MODE_WEATHER} : le temps du monde correspond à l'une des météos activées
 *       (clair / pluie / orage, chacune activable indépendamment) ;</li>
 *   <li>{@link #MODE_TIME} : l'heure du jour ({@code level.getDayTime() % 24000}) est comprise
 *       dans l'intervalle {@code [timeStart, timeEnd]}. Si {@code timeStart > timeEnd},
 *       l'intervalle traverse minuit (ex. 22000 -> 2000 couvre la nuit).</li>
 * </ul>
 */
public class TileWeatherSensor extends BlockEntity implements IFieldHolder, MenuProvider {

  public static final int MODE_WEATHER = 0;
  public static final int MODE_TIME = 1;

  public static final int MAX_TIME = 24000;
  public static final int MAX_STRENGTH = 15;

  // ---- indices des champs exposés (synchro / réseau)
  public static final int FIELD_MODE = 0;
  public static final int FIELD_WEATHER_CLEAR = 1;
  public static final int FIELD_WEATHER_RAIN = 2;
  public static final int FIELD_WEATHER_THUNDER = 3;
  public static final int FIELD_TIME_START = 4;
  public static final int FIELD_TIME_END = 5;
  public static final int FIELD_STRENGTH = 6;
  public static final int FIELD_INVERTED = 7;
  /** Lecture seule : reflète l'état redstone actuellement émis (pour le renderer/tooltip). */
  public static final int FIELD_POWERED = 8;
  public static final int FIELD_COUNT = 9;

  private int mode = MODE_WEATHER;
  private boolean weatherClear = true;
  private boolean weatherRain = false;
  private boolean weatherThunder = false;
  private int timeStart = 0;
  private int timeEnd = 13000;
  private int strength = MAX_STRENGTH;
  private boolean inverted = false;

  /** Dernier résultat calculé, exposé en lecture seule et utilisé pour l'état LIT du bloc. */
  private boolean powered = false;
  /** Ne recalcule qu'une fois par seconde : la météo/l'heure ne varient pas plus vite. */
  private int timer = 0;

  public TileWeatherSensor(BlockPos pos, BlockState state) {
    super(ModTiles.WEATHER_SENSOR.get(), pos, state);
  }

  // ==================================================================  réglages

  public int getMode() {
    return this.mode;
  }

  public boolean isWeatherClear() {
    return this.weatherClear;
  }

  public boolean isWeatherRain() {
    return this.weatherRain;
  }

  public boolean isWeatherThunder() {
    return this.weatherThunder;
  }

  public int getTimeStart() {
    return this.timeStart;
  }

  public int getTimeEnd() {
    return this.timeEnd;
  }

  public int getStrength() {
    return this.strength;
  }

  public boolean isInverted() {
    return this.inverted;
  }

  public boolean isPowered() {
    return this.powered;
  }

  /** Recalcule immédiatement au prochain tick serveur, sans attendre le minuteur d'une seconde. */
  private void forceRecheck() {
    this.timer = 0;
  }

  // ===================================================================  tick

  /** Appelé chaque tick côté serveur uniquement. */
  public void serverTick() {
    if (!(this.level instanceof ServerLevel serverLevel)) {
      return;
    }
    if (--this.timer > 0) {
      return;
    }
    this.timer = 20; // une vérification par seconde suffit largement
    boolean conditionMet = this.mode == MODE_TIME
        ? this.isTimeInRange(serverLevel.getDayTime())
        : this.isWeatherMatching(serverLevel);
    boolean shouldPower = this.inverted != conditionMet; // XOR : inversion de la logique
    if (shouldPower != this.powered) {
      this.powered = shouldPower;
      this.setLit(shouldPower);
      // notifie les voisins pour qu'ils réagissent au nouveau signal redstone
      this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
      this.syncToClients();
    }
  }

  private boolean isWeatherMatching(ServerLevel level) {
    boolean raining = level.isRaining();
    boolean thundering = level.isThundering();
    boolean clear = !raining;
    boolean rainOnly = raining && !thundering;
    if (this.weatherClear && clear) {
      return true;
    }
    if (this.weatherRain && rainOnly) {
      return true;
    }
    if (this.weatherThunder && thundering) {
      return true;
    }
    return false;
  }

  private boolean isTimeInRange(long dayTime) {
    int time = (int) (dayTime % MAX_TIME);
    if (this.timeStart <= this.timeEnd) {
      return time >= this.timeStart && time <= this.timeEnd;
    }
    return time >= this.timeStart || time <= this.timeEnd; // intervalle traversant minuit
  }

  private void setLit(boolean lit) {
    BlockState st = this.getBlockState();
    if (st.hasProperty(BlockWeatherSensor.LIT) && st.getValue(BlockWeatherSensor.LIT) != lit) {
      if (this.level != null) {
        this.level.setBlock(this.worldPosition, st.setValue(BlockWeatherSensor.LIT, lit), 3);
      }
    }
  }

  // ------------------------------------------------------------- redstone

  /** Force du signal émis quand la condition (éventuellement inversée) est remplie. */
  public int getSignal() {
    return this.powered ? this.strength : 0;
  }

  // =================================================================  IFieldHolder

  @Override
  public int getFieldCount() {
    return FIELD_COUNT;
  }

  @Override
  public int getField(int id) {
    return switch (id) {
      case FIELD_MODE -> this.mode;
      case FIELD_WEATHER_CLEAR -> this.weatherClear ? 1 : 0;
      case FIELD_WEATHER_RAIN -> this.weatherRain ? 1 : 0;
      case FIELD_WEATHER_THUNDER -> this.weatherThunder ? 1 : 0;
      case FIELD_TIME_START -> this.timeStart;
      case FIELD_TIME_END -> this.timeEnd;
      case FIELD_STRENGTH -> this.strength;
      case FIELD_INVERTED -> this.inverted ? 1 : 0;
      case FIELD_POWERED -> this.powered ? 1 : 0;
      default -> 0;
    };
  }

  @Override
  public void setField(int id, int value) {
    switch (id) {
      case FIELD_MODE -> this.mode = value == MODE_TIME ? MODE_TIME : MODE_WEATHER;
      case FIELD_WEATHER_CLEAR -> this.weatherClear = value != 0;
      case FIELD_WEATHER_RAIN -> this.weatherRain = value != 0;
      case FIELD_WEATHER_THUNDER -> this.weatherThunder = value != 0;
      case FIELD_TIME_START -> this.timeStart = clamp(value, 0, MAX_TIME);
      case FIELD_TIME_END -> this.timeEnd = clamp(value, 0, MAX_TIME);
      case FIELD_STRENGTH -> this.strength = clamp(value, 0, MAX_STRENGTH);
      case FIELD_INVERTED -> this.inverted = value != 0;
      default -> {}
    }
    this.setChanged();
    this.forceRecheck();
  }

  @Override
  public boolean isFieldEditable(int id) {
    return id != FIELD_POWERED; // état calculé par le serveur : jamais modifiable par un joueur
  }

  @Override
  public int getFieldMin(int id) {
    return 0;
  }

  @Override
  public int getFieldMax(int id) {
    return switch (id) {
      case FIELD_TIME_START, FIELD_TIME_END -> MAX_TIME;
      case FIELD_STRENGTH -> MAX_STRENGTH;
      default -> 1; // les autres champs sont des booléens
    };
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  // ===================================================================  NBT

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    this.mode = tag.getInt("mode") == MODE_TIME ? MODE_TIME : MODE_WEATHER;
    if (tag.contains("weatherClear") || tag.contains("weatherRain") || tag.contains("weatherThunder")) {
      this.weatherClear = tag.getBoolean("weatherClear");
      this.weatherRain = tag.getBoolean("weatherRain");
      this.weatherThunder = tag.getBoolean("weatherThunder");
    }
    else if (tag.contains("weather")) {
      // migration depuis l'ancien format (une seule météo choisie parmi 0/1/2)
      int legacy = clamp(tag.getInt("weather"), 0, 2);
      this.weatherClear = legacy == 0;
      this.weatherRain = legacy == 1;
      this.weatherThunder = legacy == 2;
    }
    this.timeStart = clamp(tag.getInt("timeStart"), 0, MAX_TIME);
    this.timeEnd = tag.contains("timeEnd") ? clamp(tag.getInt("timeEnd"), 0, MAX_TIME) : 13000;
    this.strength = tag.contains("strength") ? clamp(tag.getInt("strength"), 0, MAX_STRENGTH) : MAX_STRENGTH;
    this.inverted = tag.getBoolean("inverted");
    this.powered = tag.getBoolean("powered");
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("mode", this.mode);
    tag.putBoolean("weatherClear", this.weatherClear);
    tag.putBoolean("weatherRain", this.weatherRain);
    tag.putBoolean("weatherThunder", this.weatherThunder);
    tag.putInt("timeStart", this.timeStart);
    tag.putInt("timeEnd", this.timeEnd);
    tag.putInt("strength", this.strength);
    tag.putBoolean("inverted", this.inverted);
    tag.putBoolean("powered", this.powered);
  }

  // ==================================================================  synchro

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return this.saveWithoutMetadata(registries);
  }

  @Nullable
  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  private void syncToClients() {
    if (this.level != null && !this.level.isClientSide) {
      this.setChanged();
      BlockState st = this.getBlockState();
      this.level.sendBlockUpdated(this.worldPosition, st, st, 3);
    }
  }

  // ===================================================================  MenuProvider

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.automastic.weather_sensor");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
    return new MenuWeatherSensor(containerId, playerInventory, this);
  }
}
