package pe.nanamochi.banchus.packets.core;

import java.io.IOException;
import java.io.OutputStream;
import pe.nanamochi.banchus.io.data.IDataWriter;

public interface ServerPacket extends Packet {
  void write(IDataWriter writer, OutputStream stream) throws IOException;
}
