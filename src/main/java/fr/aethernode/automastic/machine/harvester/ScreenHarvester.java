package fr.aethernode.automastic.machine.harvester;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.client.FieldSlider;
import fr.aethernode.automastic.client.FieldToggleButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Interface du harvester : boutons (redstone, aperçu, direction, mode zone), sliders (taille et
 * hauteur), barre d'énergie (masquée si l'énergie est désactivée dans la config) et 27 slots de sortie.
 */
public class ScreenHarvester extends AbstractContainerScreen<MenuHarvester> {

  private static final ResourceLocation BACKGROUND =
      ResourceLocation.fromNamespaceAndPath(Automastic.MODID, "textures/gui/harvester.png");

  // barre d'énergie : cadre dessiné dans la texture en (154,16), intérieur 13x37
  private static final int BAR_X = 155;
  private static final int BAR_Y = 17;
  private static final int BAR_W = 13;
  private static final int BAR_H = 37;

  private FieldToggleButton redstone;
  private FieldToggleButton preview;
  private FieldToggleButton direction;
  private FieldToggleButton mode;
  private FieldSlider size;
  private FieldSlider height;
  /** Slider actuellement tenu par la souris (clic maintenu) : il reçoit le glissement même hors de sa barre. */
  private FieldSlider draggedSlider = null;

  public ScreenHarvester(MenuHarvester menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    this.imageWidth = 176;
    this.imageHeight = 226;
    this.inventoryLabelY = 1000; // masque « Inventory » (trop serré avec les 27 slots)
  }

  private TileHarvester tile() {
    return this.menu.getTile();
  }

  @Override
  protected void init() {
    super.init();
    BlockPos pos = this.tile().getBlockPos();
    final int x = this.leftPos + 8;
    final int y = this.topPos + 16;

    // Rangée du haut : 4 boutons 20x20 (icônes) ; la barre d'énergie occupe la droite.
    this.redstone = this.addRenderableWidget(new FieldToggleButton(x, y, pos,
        TileHarvester.FIELD_REDSTONE, "redstone_off", "redstone_on", "gui.automastic.common.redstone",
        () -> this.tile().getField(TileHarvester.FIELD_REDSTONE)));
    this.preview = this.addRenderableWidget(new FieldToggleButton(x + 22, y, pos,
        TileHarvester.FIELD_PREVIEW, "preview_off", "preview_on", "gui.automastic.harvester.preview",
        () -> this.tile().getField(TileHarvester.FIELD_PREVIEW)));
    this.direction = this.addRenderableWidget(new FieldToggleButton(x + 44, y, pos,
        TileHarvester.FIELD_DIRECTION_UP, "direction_down", "direction_up", "gui.automastic.harvester.direction",
        () -> this.tile().getField(TileHarvester.FIELD_DIRECTION_UP)));
    this.mode = this.addRenderableWidget(new FieldToggleButton(x + 66, y, pos,
        TileHarvester.FIELD_AREA_MODE, "mode_single", "mode_area", "gui.automastic.harvester.mode",
        () -> this.tile().getField(TileHarvester.FIELD_AREA_MODE)));

    // Sliders EMPILÉS, chacun en pleine largeur (137 px, jusqu'à la barre d'énergie) : plus faciles
    // à viser, et le libellé complet reste lisible.
    final int sliderW = 137;
    this.size = this.addRenderableWidget(new FieldSlider(x, y + 22, sliderW, 12, pos,
        TileHarvester.FIELD_RADIUS, 0, TileHarvester.MAX_RADIUS,
        this.tile().getField(TileHarvester.FIELD_RADIUS),
        v -> Component.translatable("gui.automastic.harvester.size", 2 * v + 1)));
    this.height = this.addRenderableWidget(new FieldSlider(x, y + 35, sliderW, 12, pos,
        TileHarvester.FIELD_HEIGHT, 0, TileHarvester.MAX_HEIGHT,
        this.tile().getField(TileHarvester.FIELD_HEIGHT),
        v -> Component.translatable("gui.automastic.harvester.height", v)));
    this.refreshWidgets();
  }

  /** Aligne les widgets sur l'état reçu du serveur (une autre personne peut modifier la machine). */
  private void refreshWidgets() {
    TileHarvester t = this.tile();
    this.redstone.refresh(t.getField(TileHarvester.FIELD_REDSTONE));
    this.preview.refresh(t.getField(TileHarvester.FIELD_PREVIEW));
    this.direction.refresh(t.getField(TileHarvester.FIELD_DIRECTION_UP));
    this.mode.refresh(t.getField(TileHarvester.FIELD_AREA_MODE));
    if (!this.size.isFocused()) {
      this.size.syncFrom(t.getField(TileHarvester.FIELD_RADIUS));
    }
    if (!this.height.isFocused()) {
      this.height.syncFrom(t.getField(TileHarvester.FIELD_HEIGHT));
    }
  }

  // ------------------------------------------------------------------ souris
  //
  // AbstractContainerScreen gère lui-même le glissement (répartition d'un stack sur les slots) et ne
  // le transmet PAS aux widgets enfants : sans ce routage, un slider reçoit le clic mais jamais le
  // glissement. Cyclic contourne le même problème dans ScreenBase#mouseDragged.

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0) {
      for (FieldSlider slider : new FieldSlider[] {this.size, this.height}) {
        if (slider.isMouseOver(mouseX, mouseY)) {
          this.draggedSlider = slider;
          break;
        }
      }
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
    if (this.draggedSlider != null && button == 0) {
      // le slider suit la souris même si elle sort de sa barre pendant le glissement
      return this.draggedSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
  }

  @Override
  public boolean mouseReleased(double mouseX, double mouseY, int button) {
    if (button == 0) {
      if (this.draggedSlider != null) {
        this.draggedSlider.stopDragging();
      }
      this.draggedSlider = null;
    }
    return super.mouseReleased(mouseX, mouseY, button);
  }

  @Override
  public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
    this.refreshWidgets();
    this.renderBackground(gg, mouseX, mouseY, partialTick); // fond assombri (comme Cyclic)
    super.render(gg, mouseX, mouseY, partialTick);
    this.renderTooltip(gg, mouseX, mouseY);
    this.renderEnergyTooltip(gg, mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
    gg.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    if (this.tile().getField(TileHarvester.FIELD_ENERGY_ENABLED) == 0) {
      // énergie désactivée : on recouvre le cadre de la barre avec la couleur du panneau
      gg.fill(this.leftPos + BAR_X - 2, this.topPos + BAR_Y - 2,
          this.leftPos + BAR_X + BAR_W + 1, this.topPos + BAR_Y + BAR_H + 1, 0xFFC6C6C6);
      return;
    }
    int cap = this.tile().getEnergyCapacity();
    int stored = this.tile().getEnergyStored();
    int filled = cap <= 0 ? 0 : Math.min(BAR_H, (int) ((long) BAR_H * stored / cap));
    // remplissage du bas vers le haut, rouge
    gg.fill(this.leftPos + BAR_X, this.topPos + BAR_Y + BAR_H - filled,
        this.leftPos + BAR_X + BAR_W, this.topPos + BAR_Y + BAR_H, 0xFFD02020);
  }

  @Override
  protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
    gg.drawString(this.font, this.title, this.titleLabelX, 5, 0x404040, false);
    // titre à gauche : l'espace à droite est réservé à la barre d'énergie
  }

  private void renderEnergyTooltip(GuiGraphics gg, int mouseX, int mouseY) {
    int x0 = this.leftPos + BAR_X;
    int y0 = this.topPos + BAR_Y;
    if (mouseX < x0 || mouseX >= x0 + BAR_W || mouseY < y0 || mouseY >= y0 + BAR_H) {
      return;
    }
    Component text = this.tile().getField(TileHarvester.FIELD_ENERGY_ENABLED) == 0
        ? Component.translatable("gui.automastic.common.energy.disabled")
        : Component.translatable("gui.automastic.common.energy", this.tile().getEnergyStored(), this.tile().getEnergyCapacity());
    gg.renderTooltip(this.font, text, mouseX, mouseY);
  }
}
