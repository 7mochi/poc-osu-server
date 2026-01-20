package pe.nanamochi.poc_osu_server.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IBanchoIO extends IBanchoWriters {

    void writePacket(OutputStream stream, int packetId, byte[] data) throws IOException;
    Object readPacket(InputStream stream) throws IOException;
}
