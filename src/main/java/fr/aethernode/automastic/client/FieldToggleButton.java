package fr.aethernode.automastic.client;

import java.util.function.IntSupplier;
import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.net.PacketTileField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Bouton à deux états lié à un champ 0/1 d'une machine, affiché avec une icône par état.
 * Le clic envoie l'inverse de la valeur courante au serveur ; l'icône et l'infobulle suivent
 * la valeur reçue du serveur (donc identiques pour tous les joueurs qui regardent la machine).
 */
public class FieldToggleButton extends Button {

  private static final int ICON_SIZE = 16;

  private final int field;
  private final ResourceLocation iconOff;
  private final ResourceLocation iconOn;
  private final String tooltipKey; // « <clé>.0 » / « <clé>.1 »
  private int shown = -1;

  /**
   * @param iconOff nom de l'icône (sans extension) affichée quand la valeur vaut 0
   * @param iconOn nom de l'icône affichée quand la valeur vaut 1
   */
  public FieldToggleButton(int x, int y, BlockPos pos, int field, String iconOff, String iconOn,
      String tooltipKey, IntSupplier current) {
    super(x, y, 20, 20, Component.empty(),
        b -> PacketDistributor.sendToServer(new PacketTileField(pos, field, current.getAsInt() == 0 ? 1 : 0)),
        DEFAULT_NARRATION);
    this.field = field;
    this.iconOff = icon(iconOff);
    this.iconOn = icon(iconOn);
    this.tooltipKey = tooltipKey;
  }

  private static ResourceLocation icon(String name) {
    return ResourceLocation.fromNamespaceAndPath(Automastic.MODID, "textures/gui/icons/" + name + ".png");
  }

  public int getField() {
    return this.field;
  }

  /** À appeler à chaque frame avec la valeur actuelle : met à jour l'icône et l'infobulle si elle change. */
  public void refresh(int value) {
    if (value != this.shown) {
      this.shown = value;
      this.setTooltip(Tooltip.create(Component.translatable(this.tooltipKey + "." + value)));
    }
  }

  /** Dessine le bouton vanilla (fond), puis l'icône centrée par-dessus. */
  @Override
  public void renderWidget(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
    super.renderWidget(gg, mouseX, mouseY, partialTick);
    ResourceLocation tex = this.shown == 1 ? this.iconOn : this.iconOff;
    int ix = this.getX() + (this.getWidth() - ICON_SIZE) / 2;
    int iy = this.getY() + (this.getHeight() - ICON_SIZE) / 2;
    // blit(texture, x, y, u, v, largeur, hauteur, largeurTexture, hauteurTexture)
    gg.blit(tex, ix, iy, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
  }
}
