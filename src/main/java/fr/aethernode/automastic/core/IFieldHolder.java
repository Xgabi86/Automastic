package fr.aethernode.automastic.core;

/**
 * Contrat entre une machine (tile) et son interface : des champs entiers
 * lus/écrits par index. Les clients les reçoivent via des DataSlot, et les
 * modifient via {@code PacketTileField}.
 */
public interface IFieldHolder {

  /** Nombre de champs exposés. */
  int getFieldCount();

  int getField(int id);

  void setField(int id, int value);

  /**
   * Vrai si un JOUEUR a le droit de modifier ce champ via l'interface. Les champs en lecture
   * seule (état calculé par le serveur) doivent renvoyer {@code false} : le paquet réseau les
   * refusera, quoi que le client envoie.
   */
  default boolean isFieldEditable(int id) {
    return true;
  }

  /** Borne minimale acceptée pour un champ (valide ce que le client envoie). */
  default int getFieldMin(int id) {
    return 0;
  }

  /** Borne maximale acceptée pour un champ (valide ce que le client envoie). */
  default int getFieldMax(int id) {
    return Integer.MAX_VALUE;
  }
}
