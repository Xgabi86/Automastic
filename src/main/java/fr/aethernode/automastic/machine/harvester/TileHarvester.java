package fr.aethernode.automastic.machine.harvester;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import fr.aethernode.automastic.config.AutomasticConfig;
import fr.aethernode.automastic.core.BaseMachineTile;
import fr.aethernode.automastic.core.InventoryUtil;
import fr.aethernode.automastic.registry.ModBlocks;
import fr.aethernode.automastic.registry.ModTiles;
import fr.aethernode.automastic.util.HarvestUtil;
import fr.aethernode.automastic.util.ShapeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Harvester : récolte les cultures mûres dans une zone devant lui et range les drops dans un
 * inventaire de 27 slots (comportement du Cyclic 1.12), avec la zone 3D du Cyclic 1.21.
 */
public class TileHarvester extends BaseMachineTile {

  // ---- indices des champs exposés à l'interface (ordre = ordinal, ne pas réordonner à la légère)
  public static final int FIELD_REDSTONE = 0;
  public static final int FIELD_PREVIEW = 1;
  public static final int FIELD_RADIUS = 2;
  public static final int FIELD_HEIGHT = 3;
  public static final int FIELD_DIRECTION_UP = 4;
  public static final int FIELD_AREA_MODE = 5;
  /** Lecture seule : 1 si l'énergie est active (coût > 0). Calculé serveur, lu par l'interface. */
  public static final int FIELD_ENERGY_ENABLED = 6;
  /**
   * Lecture seule : capacité configurée du tampon, en milliers de FE (tient sur 16 bits dans un
   * DataSlot : 0..32767 => jusqu'à ~32 millions de FE).
   */
  public static final int FIELD_CAPACITY_KFE = 7;
  public static final int FIELD_COUNT = 8;

  /** Rayon 0..12 = zone de 1x1 à 25x25. */
  public static final int MAX_RADIUS = 12;
  /** Couches supplémentaires en hauteur (0 = une seule couche). */
  public static final int MAX_HEIGHT = 16;

  public static final int INVENTORY_SIZE = 27;

  /**
   * Le constructeur du tile s'exécute aussi côté client, où la config serveur n'est PAS chargée
   * (lire {@code .get()} ici ferait planter). On démarre donc avec une capacité par défaut, et la
   * valeur configurée est appliquée côté serveur au premier tick puis synchronisée au client.
   */
  private static final int BOOT_CAPACITY = DEFAULT_ENERGY_BOOT_CAPACITY;

  /** Identifiant du son joué sur chaque plante (résolu à l'usage, une fois les registres prêts). */
  private static final ResourceLocation HARVEST_SOUND_ID = ResourceLocation.withDefaultNamespace("item.crop.plant");

  private int radius = 3;
  private int height = 0;
  private boolean directionUp = true;
  private boolean areaMode = false;
  private boolean previewVisible = false;

  /** Copie synchronisée du « coût > 0 » : le client ne peut pas lire la config serveur. */
  private boolean energyEnabled = true;
  /** Copie synchronisée de la capacité configurée (en FE). */
  private int syncedCapacity = BOOT_CAPACITY;

  private int timer = 0;
  private int scanIndex = 0;
  /** Dernière position visée (pour la preview). Client : non synchronisée, sert juste au rendu. */
  @Nullable
  private BlockPos lastTarget = null;

  /** Inventaire réel, complet (utilisé en interne). */
  private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {

    @Override
    protected void onContentsChanged(int slot) {
      TileHarvester.this.setChanged();
    }
  };

  /** Vue exposée aux tuyaux/hoppers : extraction seule, insertion refusée. */
  private final IItemHandler extractOnly = new IItemHandler() {

    @Override
    public int getSlots() {
      return inventory.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return stack; // on ne peut rien insérer de l'extérieur
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
      return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return false;
    }
  };

  public TileHarvester(BlockPos pos, BlockState state) {
    super(ModTiles.HARVESTER.get(), pos, state, BOOT_CAPACITY, BOOT_CAPACITY);
  }

  // =====================================================================  tick

  @Override
  public void serverTick() {
    if (!(this.level instanceof ServerLevel serverLevel)) {
      return;
    }
    final int cost = AutomasticConfig.HARVESTER_ENERGY_PER_PLANT.get();
    final int capacity = AutomasticConfig.HARVESTER_ENERGY_CAPACITY.get();
    this.energyEnabled = cost > 0;
    if (this.syncedCapacity != capacity) { // config modifiée (ou premier tick) : on applique et on synchronise
      this.syncedCapacity = capacity;
      this.setEnergyCapacity(capacity, capacity);
    }
    boolean ready = this.redstoneAllows() && this.canPay(cost) && !InventoryUtil.isFull(this.inventory);
    this.setLit(ready);
    if (!ready) {
      return;
    }
    if (this.timer > 0) {
      this.timer--;
      return;
    }
    if (this.areaMode) {
      this.timer = AutomasticConfig.HARVESTER_TICKS_BETWEEN_AREA_PASSES.get();
      this.harvestWholeArea(serverLevel, cost);
    }
    else {
      this.timer = AutomasticConfig.HARVESTER_TICKS_BETWEEN_ACTIONS.get();
      this.harvestNextTarget(serverLevel, cost);
    }
  }

  /** Mode par défaut : une position à la fois, en balayant la zone. */
  private void harvestNextTarget(ServerLevel level, int cost) {
    List<BlockPos> shape = this.getShape();
    if (shape.isEmpty()) {
      return;
    }
    if (this.scanIndex < 0 || this.scanIndex >= shape.size()) {
      this.scanIndex = 0;
    }
    BlockPos target = shape.get(this.scanIndex);
    this.scanIndex++;
    this.lastTarget = target;
    if (this.tryHarvest(level, target, cost) == HarvestOutcome.BLOCKED_BY_FULL_INVENTORY) {
      this.setLit(false); // évite d'attendre le prochain tick pour refléter l'état "plein"
    }
  }

  /** Mode Area : tout le champ d'un coup (s'arrête si plus d'énergie ou inventaire plein). */
  private void harvestWholeArea(ServerLevel level, int cost) {
    for (BlockPos target : this.getShape()) {
      if (!this.canPay(cost) || InventoryUtil.isFull(this.inventory)) {
        return;
      }
      // Dès que CETTE récolte a rempli le dernier espace disponible, on arrête net : on ne veut
      // plus récolter (et donc plus faire déborder au sol) tant qu'il reste des slots occupés par
      // un type de drop incompatible avec ce que la culture suivante produirait.
      if (this.tryHarvest(level, target, cost) == HarvestOutcome.BLOCKED_BY_FULL_INVENTORY) {
        return;
      }
    }
  }

  /** Résultat d'une tentative de récolte, utilisé pour savoir si on doit arrêter la boucle. */
  private enum HarvestOutcome {
    /** Rien n'a été récolté (pas mûr, pas assez d'énergie, etc.) : on continue normalement. */
    NOTHING,
    /** Récolté et intégralement rangé, il reste de la place : on continue normalement. */
    HARVESTED,
    /**
     * La récolte a été annulée (repousse restaurée) car ses drops ne rentraient pas
     * intégralement dans l'inventaire : il faut arrêter la machine ici, pas continuer sur la
     * prochaine plante.
     */
    BLOCKED_BY_FULL_INVENTORY
  }

  private HarvestOutcome tryHarvest(ServerLevel level, BlockPos target, int cost) {
    if (!this.canPay(cost)) {
      return HarvestOutcome.NOTHING; // pas assez d'énergie pour CETTE plante : on ne la touche pas
    }
    // On garde l'état AVANT récolte : HarvestUtil ne casse jamais le bloc, il le remet juste à
    // l'âge 0 ("repousse"). Si les drops ne rentrent pas dans l'inventaire, on peut donc annuler
    // proprement en restaurant cet état, comme si la plante n'avait jamais été touchée.
    BlockState before = level.getBlockState(target);
    HarvestUtil.Result result = HarvestUtil.harvest(level, target);
    if (!result.harvested()) {
      return HarvestOutcome.NOTHING;
    }
    // Vérification EXHAUSTIVE avant de rien ranger : on simule l'insertion de tous les drops.
    // L'ancien code rangeait ce qui rentrait et jetait le surplus au sol dès qu'un seul slot avait
    // de la place (ex. un item unique d'un mod tiers), même si le type réellement dropé (ex.
    // Inferium Essence de Mystical Agriculture) n'avait lui plus AUCUNE place. Résultat : la
    // machine ne s'arrêtait jamais tant qu'un slot quelconque restait libre, et balançait les
    // stacks concernées au sol en boucle.
    if (!InventoryUtil.canInsertAll(this.inventory, result.drops())) {
      level.setBlockAndUpdate(target, before); // on annule la repousse : rien n'est perdu
      return HarvestOutcome.BLOCKED_BY_FULL_INVENTORY; // pour CE type de drop, l'inventaire est plein
    }
    this.pay(cost);
    this.playHarvestEffects(level, target);
    InventoryUtil.insertAll(this.inventory, result.drops());
    this.setChanged();
    return InventoryUtil.isFull(this.inventory) ? HarvestOutcome.BLOCKED_BY_FULL_INVENTORY : HarvestOutcome.HARVESTED;
  }

  /** Son et particules sur chaque plante récoltée (envoyés aux joueurs proches). */
  private void playHarvestEffects(ServerLevel level, BlockPos target) {
    SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(HARVEST_SOUND_ID);
    if (sound != null) { // null si le son n'existe pas : on ne plante pas, on joue juste sans son
      level.playSound(null, target, sound, SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
    }
    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
        target.getX() + 0.5, target.getY() + 0.4, target.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.0);
  }

  // ======================================================================  zone

  /** Centre de la zone : devant le bloc, à {@code radius + 1} pour l'horizontal (comme Cyclic). */
  private BlockPos getAreaCenter() {
    Direction facing = this.getFacing();
    if (facing.getAxis().isVertical()) {
      return this.worldPosition.relative(facing, 1);
    }
    return this.worldPosition.relative(facing, this.radius + 1);
  }

  /**
   * Nombre signé de couches : positif = vers le haut. Le sens est UNIQUEMENT décidé par le bouton
   * de direction, quelle que soit l'orientation du bloc (contrairement à Cyclic, où un bloc posé
   * face au sol/plafond ignorait le bouton).
   */
  private int signedHeight() {
    return (this.directionUp ? 1 : -1) * this.height;
  }

  /** Zone complète à récolter. */
  public List<BlockPos> getShape() {
    List<BlockPos> base = ShapeUtil.squareHorizontalFull(this.getAreaCenter(), this.radius);
    return ShapeUtil.repeatVertically(base, this.signedHeight());
  }

  /** Contour affiché par la preview (côté client). */
  public List<BlockPos> getPreviewShape() {
    List<BlockPos> outline = ShapeUtil.repeatVertically(
        ShapeUtil.squareHorizontalHollow(this.getAreaCenter(), this.radius), this.signedHeight());
    if (this.lastTarget != null) {
      outline = new ArrayList<>(outline);
      outline.add(this.lastTarget);
    }
    return outline;
  }

  public boolean isPreviewVisible() {
    return this.previewVisible;
  }

  /** Le renderer de preview doit voir la zone même si le bloc est hors de l'écran. */
  public AABB getRenderBox() {
    return new AABB(this.worldPosition).inflate(MAX_RADIUS * 2 + 2, MAX_HEIGHT + 2, MAX_RADIUS * 2 + 2);
  }

  /** Capacité à afficher : celle de la config, telle que reçue du serveur. */
  @Override
  public int getEnergyCapacity() {
    return this.syncedCapacity;
  }

  // ================================================================  inventaire

  public ItemStackHandler getInventory() {
    return this.inventory;
  }

  @Override
  @Nullable
  public IItemHandler getInventoryForDrops() {
    return this.inventory;
  }

  @Override
  @Nullable
  public IItemHandler getItemHandler(@Nullable Direction side) {
    return this.extractOnly;
  }

  // =================================================================  IFieldHolder

  @Override
  public int getFieldCount() {
    return FIELD_COUNT;
  }

  @Override
  public int getField(int id) {
    return switch (id) {
      case FIELD_REDSTONE -> this.needsRedstone;
      case FIELD_PREVIEW -> this.previewVisible ? 1 : 0;
      case FIELD_RADIUS -> this.radius;
      case FIELD_HEIGHT -> this.height;
      case FIELD_DIRECTION_UP -> this.directionUp ? 1 : 0;
      case FIELD_AREA_MODE -> this.areaMode ? 1 : 0;
      case FIELD_ENERGY_ENABLED -> this.energyEnabled ? 1 : 0;
      case FIELD_CAPACITY_KFE -> Math.min(32767, (this.syncedCapacity + 999) / 1000); // arrondi HAUT : le client ne plafonne jamais l'énergie du serveur
      default -> 0;
    };
  }

  @Override
  public void setField(int id, int value) {
    switch (id) {
      case FIELD_REDSTONE -> this.needsRedstone = value == 0 ? 0 : 1;
      case FIELD_PREVIEW -> this.previewVisible = value != 0;
      case FIELD_RADIUS -> {
        this.radius = clamp(value, 0, MAX_RADIUS);
        this.scanIndex = 0;
      }
      case FIELD_HEIGHT -> {
        this.height = clamp(value, 0, MAX_HEIGHT);
        this.scanIndex = 0;
      }
      case FIELD_DIRECTION_UP -> {
        this.directionUp = value != 0;
        this.scanIndex = 0;
      }
      case FIELD_AREA_MODE -> this.areaMode = value != 0;
      case FIELD_ENERGY_ENABLED -> this.energyEnabled = value != 0; // reçu du serveur via DataSlot
      case FIELD_CAPACITY_KFE -> {
        // Reçu du serveur via DataSlot. Le client ne lance jamais serverTick : on aligne donc aussi
        // la capacité du stockage local, sinon l'énergie reçue serait plafonnée à tort.
        this.syncedCapacity = value * 1000;
        this.setEnergyCapacity(this.syncedCapacity, this.syncedCapacity);
      }
      default -> {}
    }
  }

  @Override
  public boolean isFieldEditable(int id) {
    // états calculés par le serveur : jamais modifiables par un joueur
    return id != FIELD_ENERGY_ENABLED && id != FIELD_CAPACITY_KFE;
  }

  @Override
  public int getFieldMin(int id) {
    return 0;
  }

  @Override
  public int getFieldMax(int id) {
    return switch (id) {
      case FIELD_RADIUS -> MAX_RADIUS;
      case FIELD_HEIGHT -> MAX_HEIGHT;
      default -> 1; // les autres champs sont des booléens
    };
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  // ===================================================================  NBT

  @Override
  public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    this.radius = clamp(tag.getInt("radius"), 0, MAX_RADIUS);
    this.height = clamp(tag.getInt("height"), 0, MAX_HEIGHT);
    this.directionUp = !tag.contains("directionUp") || tag.getBoolean("directionUp");
    this.areaMode = tag.getBoolean("areaMode");
    this.previewVisible = tag.getBoolean("preview");
    this.scanIndex = tag.getInt("scanIndex");
    if (tag.contains("inventory")) {
      this.inventory.deserializeNBT(registries, tag.getCompound("inventory"));
    }
  }

  @Override
  public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("radius", this.radius);
    tag.putInt("height", this.height);
    tag.putBoolean("directionUp", this.directionUp);
    tag.putBoolean("areaMode", this.areaMode);
    tag.putBoolean("preview", this.previewVisible);
    tag.putInt("scanIndex", this.scanIndex);
    tag.put("inventory", this.inventory.serializeNBT(registries));
  }

  // ==================================================================  menu

  @Override
  public Component getDisplayName() {
    return ModBlocks.HARVESTER.get().getName();
  }

  @Override
  public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
    return new MenuHarvester(containerId, playerInventory, this);
  }
}
