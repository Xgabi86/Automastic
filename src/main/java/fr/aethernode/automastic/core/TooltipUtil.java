package fr.aethernode.automastic.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import fr.aethernode.automastic.config.AutomasticConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Aides communes pour les descriptions d'items (tooltips) des machines.
 * <p>
 * Convention des clés de traduction, à suivre pour chaque nouvelle machine :
 * <pre>
 *   block.automastic.&lt;machine&gt;                 nom de l'item / du bloc
 *   block.automastic.&lt;machine&gt;.tooltip        description principale (grise)
 *   block.automastic.&lt;machine&gt;.tooltip.&lt;x&gt;   lignes de détail (valeurs de config)
 *   block.automastic.&lt;machine&gt;.message.&lt;x&gt;   messages d'action bar
 *   container.automastic.&lt;machine&gt;             titre de l'interface
 *   gui.automastic.&lt;machine&gt;.&lt;x&gt;             widgets propres à une machine
 *   gui.automastic.common.&lt;x&gt;                  widgets partagés entre machines
 * </pre>
 */
public final class TooltipUtil {

  private TooltipUtil() {}

  /**
   * Vrai si la config serveur est lisible.
   * <p>
   * Les tooltips s'affichent côté client, où la config SERVER n'est synchronisée que dans un
   * monde (avec les valeurs du serveur en multijoueur). Dans le menu principal ou à la création
   * d'un monde elle n'est pas chargée et {@code .get()} lèverait une exception.
   */
  public static boolean configLoaded() {
    return AutomasticConfig.SPEC.isLoaded();
  }

  /** Valeur configurée si la config est chargée, sinon la valeur par défaut (jamais d'exception). */
  public static int value(ModConfigSpec.IntValue config) {
    return configLoaded() ? config.get() : config.getDefault();
  }

  /** Description principale de la machine, en gris. */
  public static void main(List<Component> tooltip, String machine) {
    tooltip.add(Component.translatable("block.automastic." + machine + ".tooltip").withStyle(ChatFormatting.GRAY));
  }

  /** Ligne de détail (valeurs de config), dans la couleur donnée. */
  public static void detail(List<Component> tooltip, String machine, String key, ChatFormatting color, Object... args) {
    tooltip.add(Component.translatable("block.automastic." + machine + ".tooltip." + key, args).withStyle(color));
  }

  /** Ticks -> secondes lisibles : « 5 », « 2.5 », « 0.05 » (sans zéros inutiles). */
  public static String seconds(int ticks) {
    final double seconds = ticks / 20.0D;
    return seconds == Math.floor(seconds)
        ? Integer.toString((int) seconds)
        : new BigDecimal(seconds).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }
}
