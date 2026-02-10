package pe.nanamochi.banchus.packets.core;

import java.io.IOException;
import java.io.InputStream;
import pe.nanamochi.banchus.io.data.IDataReader;

public interface ClientPacket extends Packet {
  void read(IDataReader reader, InputStream stream) throws IOException;
}
