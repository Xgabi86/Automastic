package fr.aethernode.automastic.net;

import fr.aethernode.automastic.Automastic;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Enregistrement des paquets réseau. Une ligne par nouveau paquet. */
public final class PacketRegistry {

  private static final String PROTOCOL_VERSION = "1";

  private PacketRegistry() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar(Automastic.MODID).versioned(PROTOCOL_VERSION);
    registrar.playToServer(PacketTileField.TYPE, PacketTileField.STREAM_CODEC, PacketTileField::handle);
  }
}
