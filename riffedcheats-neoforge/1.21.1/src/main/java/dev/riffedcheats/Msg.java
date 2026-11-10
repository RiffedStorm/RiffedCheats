package dev.riffedcheats;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The one packet between the menu and the server part of the mod. */
public record Msg(String data) implements CustomPacketPayload {
    public static final Type<Msg> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("riffedcheats", "msg"));
    public static final StreamCodec<ByteBuf, Msg> CODEC = ByteBufCodecs.stringUtf8(4 << 20).map(Msg::new, Msg::data);

    @Override public Type<Msg> type() { return TYPE; }
}
