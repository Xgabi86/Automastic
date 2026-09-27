package fr.aethernode.automastic.client;

import java.util.function.IntFunction;
import fr.aethernode.automastic.net.PacketTileField;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Slider entier lié à un champ d'une machine : un déplacement envoie la nouvelle valeur au serveur.
 * Le libellé est produit par une fonction (ex. « Zone : 7x7 »).
 * <p>
 * Comme le slider de Cyclic, {@link #onDrag} envoie la valeur PENDANT le glissement (et pas
 * seulement au relâchement), et {@link #syncFrom} ne touche jamais au slider tant que le joueur
 * le manipule : sinon la valeur serveur, en retard d'un aller-retour réseau, le ferait « revenir
 * en arrière » sous la souris.
 */
public class FieldSlider extends AbstractSliderButton {

  private final BlockPos pos;
  private final int field;
  private final int min;
  private final int max;
  private final IntFunction<Component> label;
  private int lastSent;
  /** Vrai entre le clic et le relâchement : la valeur locale fait foi. */
  private boolean dragging = false;

  public FieldSlider(int x, int y, int width, int height, BlockPos pos, int field, int min, int max,
      int initial, IntFunction<Component> label) {
    super(x, y, width, height, Component.empty(), toRatio(initial, min, max));
    this.pos = pos;
    this.field = field;
    this.min = min;
    this.max = max;
    this.label = label;
    this.lastSent = initial;
    this.updateMessage();
  }

  private static double toRatio(int value, int min, int max) {
    return max == min ? 0 : (double) (value - min) / (double) (max - min);
  }

  public int getIntValue() {
    return Mth.floor(Mth.clampedLerp(this.min, this.max, this.value) + 0.5D);
  }

  /** Aligne le slider sur la valeur du serveur, sauf si le joueur est en train de le manipuler. */
  public void syncFrom(int serverValue) {
    if (this.dragging) {
      return;
    }
    if (serverValue != this.getIntValue()) {
      this.value = toRatio(serverValue, this.min, this.max);
      this.lastSent = serverValue;
      this.updateMessage();
    }
  }

  @Override
  protected void updateMessage() {
    this.setMessage(this.label.apply(this.getIntValue()));
  }

  @Override
  protected void applyValue() {
    int v = this.getIntValue();
    if (v != this.lastSent) { // pas de paquet tant que l'entier n'a pas changé
      this.lastSent = v;
      PacketDistributor.sendToServer(new PacketTileField(this.pos, this.field, v));
    }
  }

  @Override
  public void onClick(double mouseX, double mouseY) {
    this.dragging = true;
    super.onClick(mouseX, mouseY); // place le curseur sous la souris
  }

  /** Glissement : on met à jour ET on envoie, comme Cyclic (le parent ne le fait qu'au clic). */
  @Override
  protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
    super.onDrag(mouseX, mouseY, dragX, dragY);
    this.applyValue();
    this.updateMessage();
  }

  @Override
  public void onRelease(double mouseX, double mouseY) {
    super.onRelease(mouseX, mouseY);
    this.dragging = false;
  }

  /**
   * Fin de manipulation, appelée par l'écran au relâchement. Un AbstractContainerScreen ne relaie
   * pas toujours {@code mouseReleased} au widget : sans cet appel, {@code dragging} resterait vrai
   * et le slider ne se resynchroniserait plus jamais avec le serveur.
   */
  public void stopDragging() {
    this.dragging = false;
  }

  /** Flèches gauche/droite : ±1 (Maj : ±5, Alt : ±10), pratique pour un réglage précis. */
  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == 263 || keyCode == 262) { // GLFW_KEY_LEFT / GLFW_KEY_RIGHT
      int step = (modifiers & 1) != 0 ? 5 : (modifiers & 4) != 0 ? 10 : 1; // bit 1 = Shift, bit 4 = Alt
      int next = Mth.clamp(this.getIntValue() + (keyCode == 263 ? -step : step), this.min, this.max);
      this.value = toRatio(next, this.min, this.max);
      this.updateMessage();
      this.applyValue();
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }
}
