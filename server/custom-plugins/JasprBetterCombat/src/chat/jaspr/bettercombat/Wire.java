package chat.jaspr.bettercombat;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/** Minecraft PacketBuffer UTF-8 string (VarInt byte length), NOT DataInput.readUTF. */
public final class Wire {
    private Wire() {}
    public static String decode(byte[] bytes) throws IOException {
        if(bytes.length>512) throw new IOException("oversize");
        int len=0, shift=0, i=0, b;
        do {
            if(i>=bytes.length || shift>=21) throw new IOException("bad length");
            b=bytes[i++]&255; len|=(b&127)<<shift; shift+=7;
        } while((b&128)!=0);
        if(len!=bytes.length-i) throw new IOException("truncated/trailing data");
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes,i,len)).toString(); }
        catch(CharacterCodingException e) { throw new IOException(e); }
    }
    public static byte[] encode(String s) {
        byte[] text=s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out=new ByteArrayOutputStream(text.length+3);
        int n=text.length;
        do { int b=n&127; n>>>=7; out.write(n==0?b:b|128); } while(n!=0);
        out.write(text,0,text.length); return out.toByteArray();
    }
}
