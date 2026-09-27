package fr.aethernode.automastic.machine.weathersensor;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.client.FieldSlider;
import fr.aethernode.automastic.client.FieldToggleButton;
import fr.aethernode.automastic.net.PacketTileField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Interface du capteur météo :
 * <ul>
 *   <li>un bouton bascule le mode surveillé (heure / météo) ;</li>
 *   <li>en mode heure : deux champs numériques (début / fin de l'intervalle) ;</li>
 *   <li>en mode météo : trois boutons à bascule (beau temps / pluie / orage), cumulables ;</li>
 *   <li>dans les deux modes : un slider pour la force du signal redstone émis, et un bouton pour
 *       inverser la logique (émet sauf si la condition est remplie).</li>
 * </ul>
 * Pas d'inventaire, pas de slots.
 */
public class ScreenWeatherSensor extends AbstractContainerScreen<MenuWeatherSensor> {

  private static final ResourceLocation BACKGROUND =
      ResourceLocation.fromNamespaceAndPath(Automastic.MODID, "textures/gui/weather_sensor.png");

  // sous-zone "mode" dans la feuille de texture : rangée dédiée sous le panel de base (176x150),
  // une variante pour le mode heure et une pour le mode météo (tailles légèrement différentes,
  // elles ne peuvent donc pas cohabiter sur un seul fond statique). Voir gen_textures.py.
  private static final int TIME_ZONE_V = 150;
  private static final int TIME_ZONE_H = 26;
  private static final int TIME_ZONE_TOP = 46; // y locale (dans le panel) où commence le crop, = 48 - pad(2)
  private static final int WEATHER_ZONE_V = 176;
  private static final int WEATHER_ZONE_H = 28;
  private static final int WEATHER_ZONE_TOP = 45; // y locale où commence le crop, = 47 - pad(2)

  private Button modeButton;

  // ---- mode heure
  private EditBox timeStartBox;
  private EditBox timeEndBox;

  // ---- mode météo
  private FieldToggleButton weatherClear;
  private FieldToggleButton weatherRain;
  private FieldToggleButton weatherThunder;

  // ---- commun
  private FieldSlider strength;
  private FieldToggleButton inverted;
  private FieldSlider draggedSlider = null;

  private int shownMode = -1;

  public ScreenWeatherSensor(MenuWeatherSensor menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    this.imageWidth = 176;
    this.imageHeight = 150;
    this.inventoryLabelY = 1000; // pas d'inventaire à étiqueter
  }

  private TileWeatherSensor tile() {
    return this.menu.getTile();
  }

  @Override
  protected void init() {
    super.init();
    BlockPos pos = this.tile().getBlockPos();
    final int x = this.leftPos + 8;
    final int y = this.topPos + 24;

    // -------- bouton de mode (pleine largeur, en haut)
    this.modeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
      int next = this.tile().getMode() == TileWeatherSensor.MODE_TIME
          ? TileWeatherSensor.MODE_WEATHER : TileWeatherSensor.MODE_TIME;
      PacketDistributor.sendToServer(new PacketTileField(pos, TileWeatherSensor.FIELD_MODE, next));
    }).bounds(x, y, 160, 20).build());

    // -------- mode heure : 2 champs texte côte à côte
    final int boxY = y + 26;
    this.timeStartBox = new EditBox(this.font, x, boxY, 76, 18, Component.translatable("gui.automastic.weather_sensor.time_start"));
    this.timeStartBox.setMaxLength(5);
    this.timeStartBox.setFilter(ScreenWeatherSensor::isDigitsOnly);
    this.timeStartBox.setResponder(s -> this.commitTime(this.timeStartBox, TileWeatherSensor.FIELD_TIME_START));
    this.addRenderableWidget(this.timeStartBox);

    this.timeEndBox = new EditBox(this.font, x + 84, boxY, 76, 18, Component.translatable("gui.automastic.weather_sensor.time_end"));
    this.timeEndBox.setMaxLength(5);
    this.timeEndBox.setFilter(ScreenWeatherSensor::isDigitsOnly);
    this.timeEndBox.setResponder(s -> this.commitTime(this.timeEndBox, TileWeatherSensor.FIELD_TIME_END));
    this.addRenderableWidget(this.timeEndBox);

    // -------- mode météo : 3 boutons à bascule, un par condition, centrés sur la largeur totale (160px,
    // comme le bouton mode et le slider) avec un espacement égal entre eux et sur les bords
    final int rowWidth = 160;
    final int btnSize = 20;
    final int weatherGap = (rowWidth - 3 * btnSize) / 4; // 4 intervalles égaux : bord, entre1-2, entre2-3, bord
    final int weatherRowY = boxY - 1;
    final int weatherX0 = x + weatherGap;
    final int weatherX1 = x + 2 * weatherGap + btnSize;
    final int weatherX2 = x + 3 * weatherGap + 2 * btnSize;
    this.weatherClear = this.addRenderableWidget(new FieldToggleButton(weatherX0, weatherRowY, pos,
        TileWeatherSensor.FIELD_WEATHER_CLEAR, "weather_clear_off", "weather_clear_on",
        "gui.automastic.weather_sensor.weather_clear", () -> this.tile().getField(TileWeatherSensor.FIELD_WEATHER_CLEAR)));
    this.weatherRain = this.addRenderableWidget(new FieldToggleButton(weatherX1, weatherRowY, pos,
        TileWeatherSensor.FIELD_WEATHER_RAIN, "weather_rain_off", "weather_rain_on",
        "gui.automastic.weather_sensor.weather_rain", () -> this.tile().getField(TileWeatherSensor.FIELD_WEATHER_RAIN)));
    this.weatherThunder = this.addRenderableWidget(new FieldToggleButton(weatherX2, weatherRowY, pos,
        TileWeatherSensor.FIELD_WEATHER_THUNDER, "weather_thunder_off", "weather_thunder_on",
        "gui.automastic.weather_sensor.weather_thunder", () -> this.tile().getField(TileWeatherSensor.FIELD_WEATHER_THUNDER)));

    // -------- commun : slider de force (1px plus haut) + toggle inversé (centré sur la même largeur totale)
    final int commonY = boxY + 30 - 1;
    this.strength = this.addRenderableWidget(new FieldSlider(x, commonY, rowWidth, 18, pos,
        TileWeatherSensor.FIELD_STRENGTH, 0, TileWeatherSensor.MAX_STRENGTH,
        this.tile().getField(TileWeatherSensor.FIELD_STRENGTH),
        v -> Component.translatable("gui.automastic.weather_sensor.strength", v)));

    final int invertedX = x + (rowWidth - btnSize) / 2;
    this.inverted = this.addRenderableWidget(new FieldToggleButton(invertedX, commonY + 26, pos,
        TileWeatherSensor.FIELD_INVERTED, "inverted_off", "inverted_on",
        "gui.automastic.weather_sensor.inverted", () -> this.tile().getField(TileWeatherSensor.FIELD_INVERTED)));


    this.refreshWidgets();
  }

  /** N'autorise que des chiffres dans les champs de temps. */
  private static boolean isDigitsOnly(String s) {
    return s.chars().allMatch(Character::isDigit);
  }

  /** Valide et envoie le contenu d'un champ temps (vide/non numérique ignoré, borné à MAX_TIME). */
  private void commitTime(EditBox box, int field) {
    String text = box.getValue().trim();
    if (text.isEmpty()) {
      return;
    }
    try {
      int value = Math.max(0, Math.min(TileWeatherSensor.MAX_TIME, Integer.parseInt(text)));
      PacketDistributor.sendToServer(new PacketTileField(this.tile().getBlockPos(), field, value));
    }
    catch (NumberFormatException ignored) {
      // l'utilisateur tape encore : on attend une valeur valide
    }
  }

  /** Aligne les widgets sur l'état reçu du serveur, et bascule l'affichage heure <-> météo. */
  private void refreshWidgets() {
    TileWeatherSensor t = this.tile();
    int mode = t.getMode();

    this.modeButton.setMessage(Component.translatable(mode == TileWeatherSensor.MODE_TIME
        ? "gui.automastic.weather_sensor.mode.time" : "gui.automastic.weather_sensor.mode.weather"));

    boolean timeMode = mode == TileWeatherSensor.MODE_TIME;
    this.timeStartBox.visible = timeMode;
    this.timeEndBox.visible = timeMode;
    this.weatherClear.visible = !timeMode;
    this.weatherRain.visible = !timeMode;
    this.weatherThunder.visible = !timeMode;

    if (mode != this.shownMode) {
      // on vient de changer de mode : réinitialise le contenu des champs texte sans perdre le focus ailleurs
      if (timeMode) {
        this.timeStartBox.setValue(Integer.toString(t.getTimeStart()));
        this.timeEndBox.setValue(Integer.toString(t.getTimeEnd()));
      }
      this.shownMode = mode;
    }
    else if (timeMode) {
      if (!this.timeStartBox.isFocused()) {
        this.timeStartBox.setValue(Integer.toString(t.getTimeStart()));
      }
      if (!this.timeEndBox.isFocused()) {
        this.timeEndBox.setValue(Integer.toString(t.getTimeEnd()));
      }
    }

    this.weatherClear.refresh(t.getField(TileWeatherSensor.FIELD_WEATHER_CLEAR));
    this.weatherRain.refresh(t.getField(TileWeatherSensor.FIELD_WEATHER_RAIN));
    this.weatherThunder.refresh(t.getField(TileWeatherSensor.FIELD_WEATHER_THUNDER));
    this.inverted.refresh(t.getField(TileWeatherSensor.FIELD_INVERTED));
    if (!this.strength.isFocused()) {
      this.strength.syncFrom(t.getField(TileWeatherSensor.FIELD_STRENGTH));
    }
  }

  // ------------------------------------------------------------------ souris (glissement du slider)

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && this.strength.isMouseOver(mouseX, mouseY)) {
      this.draggedSlider = this.strength;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
    if (this.draggedSlider != null && button == 0) {
      return this.draggedSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
  }

  @Override
  public boolean mouseReleased(double mouseX, double mouseY, int button) {
    if (button == 0 && this.draggedSlider != null) {
      this.draggedSlider.stopDragging();
      this.draggedSlider = null;
    }
    return super.mouseReleased(mouseX, mouseY, button);
  }

  @Override
  public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
    this.refreshWidgets();
    this.renderBackground(gg, mouseX, mouseY, partialTick);
    super.render(gg, mouseX, mouseY, partialTick);
    this.renderTooltip(gg, mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
    gg.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    boolean timeMode = this.tile().getMode() == TileWeatherSensor.MODE_TIME;
    if (timeMode) {
      gg.blit(BACKGROUND, this.leftPos, this.topPos + TIME_ZONE_TOP, 0, TIME_ZONE_V, this.imageWidth, TIME_ZONE_H);
    }
    else {
      gg.blit(BACKGROUND, this.leftPos, this.topPos + WEATHER_ZONE_TOP, 0, WEATHER_ZONE_V, this.imageWidth, WEATHER_ZONE_H);
    }
  }

  @Override
  protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
    gg.drawString(this.font, this.title, this.titleLabelX, 6, 0x404040, false);
    this.renderCurrentConditionLabel(gg);
  }

  /** Coin en haut à droite : météo actuelle du monde (mode météo) ou heure actuelle (mode heure). */
  private void renderCurrentConditionLabel(GuiGraphics gg) {
    if (this.minecraft == null || this.minecraft.level == null) {
      return;
    }
    var level = this.minecraft.level;
    Component text;
    if (this.tile().getMode() == TileWeatherSensor.MODE_TIME) {
      int time = (int) (level.getDayTime() % TileWeatherSensor.MAX_TIME);
      text = Component.translatable("gui.automastic.weather_sensor.current_time", time);
    }
    else {
      String key = level.isThundering() ? "gui.automastic.weather_sensor.current_weather.thunder"
          : level.isRaining() ? "gui.automastic.weather_sensor.current_weather.rain"
          : "gui.automastic.weather_sensor.current_weather.clear";
      text = Component.translatable("gui.automastic.weather_sensor.current_weather", Component.translatable(key));
    }
    int textWidth = this.font.width(text);
    gg.drawString(this.font, text, this.imageWidth - 8 - textWidth, 6, 0x404040, false);
  }
}
