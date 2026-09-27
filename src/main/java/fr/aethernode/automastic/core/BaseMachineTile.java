package fr.aethernode.automastic.core;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Socle des machines : redstone, énergie optionnelle, propriété LIT, sérialisation
 * et synchronisation client. Une machine concrète implémente {@link #serverTick()}
 * et déclare ses propres champs via {@link IFieldHolder}.
 */
public abstract class BaseMachineTile extends BlockEntity implements MenuProvider, IFieldHolder {

  public static final String NBT_ENERGY = "energy";
  public static final String NBT_REDSTONE = "needsRedstone";
  /** Capacité initiale avant que la config serveur soit lue (remplacée dès le premier tick serveur). */
  protected static final int DEFAULT_ENERGY_BOOT_CAPACITY = 1_000_000;

  /** 0 = toujours actif, 1 = nécessite un signal redstone. */
  protected int needsRedstone = 0;

  /** Stockage d'énergie, {@code null} si la machine n'en utilise pas. */
  @Nullable
  protected final MachineEnergy energy;

  protected BaseMachineTile(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int energyMaxReceive) {
    super(type, pos, state);
    this.energy = energyCapacity > 0 ? new MachineEnergy(energyCapacity, energyMaxReceive) : null;
  }

  protected BaseMachineTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    this(type, pos, state, 0, 0);
  }

  /** Appelé chaque tick côté serveur uniquement. */
  public abstract void serverTick();

  /** Inventaire à vider quand le bloc est cassé, ou {@code null}. */
  @Nullable
  public IItemHandler getInventoryForDrops() {
    return null;
  }

  /** Capability item exposée aux tuyaux/hoppers, par face. {@code null} = rien d'exposé. */
  @Nullable
  public IItemHandler getItemHandler(@Nullable Direction side) {
    return null;
  }

  /** Capability énergie exposée, par face. */
  @Nullable
  public IEnergyStorage getEnergyHandler(@Nullable Direction side) {
    return this.energy;
  }

  // ---------------------------------------------------------------- redstone

  public boolean requiresRedstone() {
    return this.needsRedstone == 1;
  }

  public boolean isPowered() {
    return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
  }

  /** Vrai si la redstone autorise la machine à fonctionner. */
  protected boolean redstoneAllows() {
    return !this.requiresRedstone() || this.isPowered();
  }

  // ------------------------------------------------------------------ énergie

  public boolean usesEnergy() {
    return this.energy != null;
  }

  public int getEnergyStored() {
    return this.energy == null ? 0 : this.energy.getEnergyStored();
  }

  public int getEnergyCapacity() {
    return this.energy == null ? 0 : this.energy.getMaxEnergyStored();
  }

  /** Vrai si la machine peut payer {@code cost} (toujours vrai si cost <= 0). */
  protected boolean canPay(int cost) {
    return cost <= 0 || (this.energy != null && this.energy.getEnergyStored() >= cost);
  }

  /** Débite {@code cost} (sans effet si cost <= 0). */
  protected void pay(int cost) {
    if (cost > 0 && this.energy != null) {
      this.energy.drain(cost);
    }
  }

  /** Applique une nouvelle capacité (appelé côté serveur avec la valeur de la config). */
  protected void setEnergyCapacity(int capacity, int maxReceive) {
    if (this.energy != null) {
      this.energy.setCapacity(capacity, maxReceive);
    }
  }

  /** Force la valeur de l'énergie (utilisé pour la synchro client). */
  public void setEnergyClient(int value) {
    if (this.energy != null) {
      this.energy.setStored(value);
    }
  }

  // ------------------------------------------------------------------- état

  /** Met à jour la propriété LIT du bloc si elle change (évite les mises à jour inutiles). */
  protected void setLit(boolean lit) {
    if (this.level == null || this.level.isClientSide) {
      return;
    }
    BlockState st = this.getBlockState();
    if (st.hasProperty(BaseMachineBlock.LIT) && st.getValue(BaseMachineBlock.LIT) != lit) {
      this.level.setBlock(this.worldPosition, st.setValue(BaseMachineBlock.LIT, lit), 3);
    }
  }

  public Direction getFacing() {
    return this.getBlockState().getValue(BaseMachineBlock.FACING);
  }

  // ------------------------------------------------------ sérialisation NBT

  @Override
  public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    this.needsRedstone = tag.getInt(NBT_REDSTONE);
    if (this.energy != null && tag.contains(NBT_ENERGY)) {
      this.energy.setStored(tag.getInt(NBT_ENERGY));
    }
  }

  @Override
  public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt(NBT_REDSTONE, this.needsRedstone);
    if (this.energy != null) {
      tag.putInt(NBT_ENERGY, this.energy.getEnergyStored());
    }
  }

  // ------------------------------------------- synchro chunk (rendu preview)

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    // le renderer de preview lit la zone (rayon/hauteur/direction) côté client
    return this.saveWithoutMetadata(registries);
  }

  @Nullable
  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  /** Notifie les clients proches que les données ont changé. */
  protected void syncToClients() {
    if (this.level != null && !this.level.isClientSide) {
      this.setChanged();
      BlockState st = this.getBlockState();
      this.level.sendBlockUpdated(this.worldPosition, st, st, 3);
    }
  }

  /**
   * Stockage d'énergie autonome (implémente uniquement l'API publique {@link IEnergyStorage}).
   * Accepte l'énergie de l'extérieur, refuse toute extraction externe (c'est un carburant,
   * pas une batterie), et permet un débit/réglage interne direct.
   */
  public static class MachineEnergy implements IEnergyStorage {

    private int capacity;
    private int maxReceive;
    private int stored;

    public MachineEnergy(int capacity, int maxReceive) {
      this.capacity = capacity;
      this.maxReceive = maxReceive;
    }

    /** Change la capacité (config) ; l'énergie stockée est ramenée sous la nouvelle limite. */
    public void setCapacity(int capacity, int maxReceive) {
      this.capacity = Math.max(0, capacity);
      this.maxReceive = Math.max(0, maxReceive);
      this.stored = Math.min(this.stored, this.capacity);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
      int accepted = Math.max(0, Math.min(Math.min(this.capacity - this.stored, this.maxReceive), toReceive));
      if (!simulate) {
        this.stored += accepted;
      }
      return accepted;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
      return 0; // pas d'extraction externe
    }

    @Override
    public int getEnergyStored() {
      return this.stored;
    }

    @Override
    public int getMaxEnergyStored() {
      return this.capacity;
    }

    @Override
    public boolean canExtract() {
      return false;
    }

    @Override
    public boolean canReceive() {
      return this.maxReceive > 0;
    }

    /** Réglage direct (chargement NBT, synchro client). */
    public void setStored(int value) {
      this.stored = Math.max(0, Math.min(value, this.capacity));
    }

    /** Débit interne. */
    public void drain(int amount) {
      this.stored = Math.max(0, this.stored - amount);
    }
  }
}
