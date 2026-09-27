package fr.aethernode.automastic.machine.sprinkler;

import javax.annotation.Nullable;
import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.config.AutomasticConfig;
import fr.aethernode.automastic.registry.ModTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Logique du sprinkler, reprise de {@code TileSprinkler} de Cyclic 1.12 (Lothrazar, MIT).
 * <p>
 * Tous les {@code ticksBetweenWatering} ticks (config), chaque bloc de la zone (à la hauteur du
 * sprinkler) a {@code percentPerWatering} % de chances d'être arrosé. Un bloc arrosé reçoit un {@code randomTick}
 * supplémentaire, exactement comme si le jeu lui en avait donné un : la culture pousse donc à
 * son rythme normal, seulement plus souvent. On n'écrit jamais l'âge directement.
 * <p>
 * Différence avec Cyclic : au lieu d'exiger de l'eau posée dessous, le sprinkler possède un
 * réservoir interne (10 000 mB par défaut) qui se remplit par n'importe quel tuyau à fluide
 * (capability {@code FluidHandler}) ou avec un seau. Chaque arrosage consomme de l'eau
 * (500 mB par défaut). Réservoir et coût sont configurables ; un coût de 0 désactive le réservoir.
 * <p>
 * Adaptations 1.21 : {@code IPlantable} n'existe plus, on cible {@link BonemealableBlock} (toutes
 * les cultures vanilla et modées, dont Mystical Agriculture). {@code updateTick} devient
 * {@code BlockState.randomTick}, qui déclenche aussi les événements de croissance NeoForge.
 */
public class TileSprinkler extends BlockEntity {

  /**
   * Valeur de départ du compte à rebours, avant que la config serveur soit lue (le constructeur
   * du tile s'exécute aussi côté client, où {@code .get()} planterait). Au premier tick serveur,
   * le compte à rebours est ramené à l'intervalle configuré s'il est plus long.
   */
  private static final int BOOT_TIMER = 100;
  /** Anti-rebond du bouton particules, en ticks. */
  private static final int TOGGLE_COOLDOWN = 15;

  /**
   * Capacité avant que la config serveur soit lue. Le constructeur du tile s'exécute aussi côté
   * client, où la config serveur n'est PAS chargée (lire {@code .get()} ici ferait planter) : on
   * démarre donc avec une valeur par défaut, remplacée au premier tick serveur.
   */
  private static final int BOOT_CAPACITY = 10_000;

  private static final String NBT_PARTICLES = "spawnParticles";
  private static final String NBT_TANK = "tank";
  private static final String NBT_RUNNING = "running";
  private static final String NBT_CAPACITY = "capacity";

  private boolean spawnParticles = true;
  private int timer = BOOT_TIMER;
  private int timerUserToggle = 0;

  /**
   * Vrai si le sprinkler peut arroser (eau disponible, ou réservoir désactivé). Calculé serveur
   * et synchronisé au client : le renderer s'en sert pour faire tourner le rotor.
   */
  private boolean running = false;
  /** Copie de la capacité configurée, pour détecter un changement de config. */
  private int syncedCapacity = BOOT_CAPACITY;

  /** Réservoir : n'accepte que de l'eau (tag {@code minecraft:water}, donc aussi l'eau modée). */
  private final FluidTank tank = new FluidTank(BOOT_CAPACITY, stack -> stack.is(FluidTags.WATER)) {

    @Override
    protected void onContentsChanged() {
      TileSprinkler.this.setChanged();
    }
  };

  /** Vue exposée aux tuyaux : remplissage uniquement, on ne peut rien en extraire. */
  private final IFluidHandler fillOnly = new IFluidHandler() {

    @Override
    public int getTanks() {
      return tank.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int t) {
      return tank.getFluidInTank(t);
    }

    @Override
    public int getTankCapacity(int t) {
      return tank.getTankCapacity(t);
    }

    @Override
    public boolean isFluidValid(int t, FluidStack stack) {
      return tank.isFluidValid(t, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
      return tank.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
      return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
      return FluidStack.EMPTY;
    }
  };

  public TileSprinkler(BlockPos pos, BlockState state) {
    super(ModTiles.SPRINKLER.get(), pos, state);
  }

  // ==================================================================  état

  /**
   * Vrai si le sprinkler arrose : réservoir désactivé (coût 0), ou assez d'eau pour un arrosage.
   * <p>
   * Lit la config : à n'appeler que côté serveur. Le client lit {@link #isAnimating()}.
   */
  public boolean isRunning() {
    int cost = AutomasticConfig.SPRINKLER_WATER_PER_WATERING.get();
    return cost <= 0 || this.tank.getFluidAmount() >= cost;
  }

  /** Lu par le renderer côté client : vrai si le serveur a indiqué que le sprinkler tourne. */
  public boolean isAnimating() {
    return this.running;
  }

  public boolean isSpawningParticles() {
    return this.spawnParticles;
  }

  /** Bascule les particules, avec un anti-rebond (un clic peut être reçu plusieurs fois). */
  public void toggleSpawnParticles() {
    if (this.timerUserToggle > 0) {
      return;
    }
    this.spawnParticles = !this.spawnParticles;
    this.timerUserToggle = TOGGLE_COOLDOWN;
    this.setChanged();
  }

  // ==================================================================  eau

  public FluidTank getTank() {
    return this.tank;
  }

  /**
   * Capability fluide exposée aux tuyaux, quelle que soit la face : remplissage seul.
   * Renvoie {@code null} si le réservoir est désactivé (coût 0) : inutile d'avaler l'eau des tuyaux.
   */
  @Nullable
  public IFluidHandler getFluidHandler(@Nullable Direction side) {
    // La config serveur n'existe pas côté client (un mod d'info type Jade/WTHIT peut interroger la
    // capability là-bas) : on y expose toujours le handler, le serveur reste seul juge du coût.
    if (this.level == null || this.level.isClientSide) {
      return this.fillOnly;
    }
    return AutomasticConfig.SPRINKLER_WATER_PER_WATERING.get() > 0 ? this.fillOnly : null;
  }

  public int getWaterAmount() {
    return this.tank.getFluidAmount();
  }

  public int getWaterCapacity() {
    return this.tank.getCapacity();
  }

  // ===================================================================  tick

  /** Appelé chaque tick côté serveur uniquement. */
  public void serverTick() {
    if (!(this.level instanceof ServerLevel serverLevel)) {
      return;
    }
    if (this.timerUserToggle > 0) {
      this.timerUserToggle--;
    }
    // config modifiée (ou premier tick) : on applique la nouvelle capacité
    int capacity = AutomasticConfig.SPRINKLER_TANK_CAPACITY.get();
    if (capacity != this.syncedCapacity) {
      this.syncedCapacity = capacity;
      this.tank.setCapacity(capacity);
      // ramène le contenu sous la nouvelle limite
      if (this.tank.getFluidAmount() > capacity) {
        this.tank.setFluid(this.tank.getFluid().copyWithAmount(capacity));
      }
      this.syncToClients();
    }
    boolean nowRunning = this.isRunning();
    if (nowRunning != this.running) {
      this.running = nowRunning;
      this.syncToClients(); // le renderer doit savoir s'il faut faire tourner le rotor
    }
    if (!nowRunning) {
      return;
    }
    // config modifiée : un compte à rebours plus long que le nouvel intervalle est raccourci
    final int interval = AutomasticConfig.SPRINKLER_TICKS_BETWEEN_WATERING.get();
    if (this.timer > interval) {
      this.timer = interval;
    }
    if (--this.timer > 0) {
      return;
    }
    this.timer = interval;
    this.waterArea(serverLevel);
  }

  /**
   * Un arrosage complet. L'eau n'est débitée que si au moins une plante a réellement été arrosée :
   * un sprinkler au milieu d'un champ déjà mûr ne vide donc pas son réservoir pour rien.
   */
  private void waterArea(ServerLevel level) {
    final int radius = AutomasticConfig.SPRINKLER_RADIUS.get();
    final float chance = AutomasticConfig.SPRINKLER_PERCENT_PER_WATERING.get() / 100.0F;
    final BlockPos origin = this.worldPosition;
    final BlockPos.MutableBlockPos current = new BlockPos.MutableBlockPos();
    boolean wateredAny = false;
    for (int x = -radius; x <= radius; x++) {
      for (int z = -radius; z <= radius; z++) {
        if (level.random.nextFloat() >= chance) {
          continue; // un tirage différent pour chaque bloc, comme Cyclic
        }
        current.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
        if (!level.isLoaded(current)) {
          continue; // ne jamais forcer le chargement d'un chunk voisin
        }
        wateredAny |= this.waterBlock(level, current);
      }
    }
    if (wateredAny) {
      this.consumeWater();
    }
  }

  /** Débite le coût d'un arrosage (sans effet si le réservoir est désactivé). */
  private void consumeWater() {
    int cost = AutomasticConfig.SPRINKLER_WATER_PER_WATERING.get();
    if (cost > 0) {
      this.tank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
    }
  }

  /** @return vrai si une plante a été arrosée. */
  private boolean waterBlock(ServerLevel level, BlockPos pos) {
    BlockState state = level.getBlockState(pos);
    if (state.isAir()) {
      return false;
    }
    Block block = state.getBlock();
    if (!(block instanceof BonemealableBlock growable)) {
      return false; // pas une plante
    }
    // Déjà à maturité (ou ne peut plus pousser) : on ne gaspille ni tick, ni eau, ni particules
    if (!growable.isValidBonemealTarget(level, pos, state)) {
      return false;
    }
    if (this.spawnParticles && AutomasticConfig.SPRINKLER_PARTICLES.get()) {
      level.sendParticles(ParticleTypes.SPLASH,
          pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.0);
    }
    try {
      // Un tick aléatoire de plus : la plante suit ses propres règles (lumière, sol, événements)
      state.randomTick(level, pos, level.random);
    }
    catch (Exception e) {
      Automastic.LOGGER.error("Sprinkler: erreur pendant la croissance de {}", block, e);
    }
    return true;
  }

  // ===================================================================  rendu

  /** Le rotor déborde légèrement du bloc : on garde la boîte de rendu par défaut (1 bloc). */
  public AABB getRenderBox() {
    return new AABB(this.worldPosition);
  }

  // ==================================================================  NBT

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    // absent = ancienne sauvegarde : particules activées par défaut
    this.spawnParticles = !tag.contains(NBT_PARTICLES) || tag.getBoolean(NBT_PARTICLES);
    this.running = tag.getBoolean(NBT_RUNNING);
    if (tag.contains(NBT_CAPACITY)) {
      this.syncedCapacity = tag.getInt(NBT_CAPACITY);
      this.tank.setCapacity(this.syncedCapacity);
    }
    this.tank.readFromNBT(registries, tag.getCompound(NBT_TANK));
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putBoolean(NBT_PARTICLES, this.spawnParticles);
    tag.putBoolean(NBT_RUNNING, this.running);
    tag.putInt(NBT_CAPACITY, this.syncedCapacity);
    tag.put(NBT_TANK, this.tank.writeToNBT(registries, new CompoundTag()));
  }

  // ================================================================  synchro

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
}
