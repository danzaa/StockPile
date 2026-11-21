package dev.danzaa.stockpile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded JSON over Minecraft's authenticated play connection, never the dashboard's HTTP port. */
public record SharedPayload(String json) implements CustomPacketPayload {
    public static final Type<SharedPayload> TYPE=new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("stockpile","shared_v1"));
    public static final int MAX_EXPANDED=8*1024*1024, MAX_WIRE=900000;
    public static final StreamCodec<RegistryFriendlyByteBuf,SharedPayload> CODEC=CustomPacketPayload.codec(
            (payload,buf)->buf.writeByteArray(compress(payload.json())),buf->new SharedPayload(expand(buf.readByteArray(MAX_WIRE))));
    public static final StreamCodec<RegistryFriendlyByteBuf,SharedPayload> SERVERBOUND_CODEC=CustomPacketPayload.codec(
            (payload,buf)->{byte[] data=compress(payload.json());if(payload.json().length()>256000||data.length>28000)throw new IllegalArgumentException("Reservation too large");buf.writeByteArray(data);},
            buf->new SharedPayload(expand(buf.readByteArray(28000),256000)));
    @Override public Type<SharedPayload> type(){return TYPE;}
    public static byte[] compress(String json){
        byte[] input=json.getBytes(StandardCharsets.UTF_8);
        if(input.length>MAX_EXPANDED)throw new IllegalArgumentException("Shared workspace exceeds 8 MB");
        try {var out=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(out)){zip.write(input);}byte[] data=out.toByteArray();
            if(data.length>MAX_WIRE)throw new IllegalArgumentException("Shared workspace exceeds packet limit");return data;
        }catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public static String expand(byte[] data){return expand(data,MAX_EXPANDED);}
    private static String expand(byte[] data,int limit){
        if(data.length>MAX_WIRE)throw new IllegalArgumentException("Shared packet too large");
        try(var zip=new GZIPInputStream(new ByteArrayInputStream(data))){byte[] value=zip.readNBytes(limit+1);
            if(value.length>limit)throw new IllegalArgumentException("Expanded shared packet too large");return new String(value,StandardCharsets.UTF_8);
        }catch(IOException e){throw new IllegalArgumentException("Invalid shared packet",e);}
    }
}
