package fr.aethernode.automastic.net;

import fr.aethernode.automastic.Automastic;
import fr.aethernode.automastic.core.IFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Joueur → serveur : « je règle le champ {@code field} de la machine en {@code pos} sur {@code value} ».
 * <p>
 * Le serveur ne fait confiance à rien : distance, existence de la machine, champ modifiable et
 * bornes sont revérifiés (l'original Cyclic ne le faisait pas).
 */
public record PacketTileField(BlockPos pos, int field, int value) implements CustomPacketPayload {

  public static final Type<PacketTileField> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(Automastic.MODID, "tile_field"));

  public static final StreamCodec<RegistryFriendlyByteBuf, PacketTileField> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, PacketTileField::pos,
      ByteBufCodecs.VAR_INT, PacketTileField::field,
      ByteBufCodecs.VAR_INT, PacketTileField::value,
      PacketTileField::new);

  /** Portée max d'interaction (comme Container#stillValid : 8 blocs, au carré). */
  private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handle(PacketTileField msg, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      if (!(ctx.player() instanceof ServerPlayer player)) {
        return;
      }
      // le joueur doit être proche, et le chunk chargé (évite de forcer le chargement de chunks)
      if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > MAX_DISTANCE_SQ
          || !player.level().isLoaded(msg.pos)) {
        return;
      }
      BlockEntity be = player.level().getBlockEntity(msg.pos);
      if (!(be instanceof IFieldHolder holder)) {
        return;
      }
      if (msg.field < 0 || msg.field >= holder.getFieldCount() || !holder.isFieldEditable(msg.field)) {
        return;
      }
      int value = Math.max(holder.getFieldMin(msg.field), Math.min(holder.getFieldMax(msg.field), msg.value));
      holder.setField(msg.field, value);
      be.setChanged();
      // rafraîchit le rendu de preview chez les clients proches
      var state = be.getBlockState();
      player.level().sendBlockUpdated(msg.pos, state, state, 3);
    });
  }
}
