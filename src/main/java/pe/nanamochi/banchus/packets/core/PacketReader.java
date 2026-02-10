package pe.nanamochi.banchus.packets.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.io.data.BanchoDataReader;
import pe.nanamochi.banchus.io.data.IDataReader;

@Component
public class PacketReader {
  private final Map<Integer, ClientPacket> clientPackets;
  private final IDataReader reader = new BanchoDataReader();

  public PacketReader(List<ClientPacket> packetPrototypes) {
    this.clientPackets =
        packetPrototypes.stream().collect(Collectors.toMap(p -> p.getPacketType().getId(), p -> p));
  }

  public Packet readPacket(InputStream stream) throws IOException {
    int packetId = reader.readUint16(stream);
    reader.readUint8(stream); // padding
    int length = reader.readInt32(stream);
    byte[] data = stream.readNBytes(length);

    ClientPacket prototype = clientPackets.get(packetId);
    if (prototype == null) return null;

    try {
      ClientPacket packet = prototype.getClass().getDeclaredConstructor().newInstance();
      packet.read(reader, new ByteArrayInputStream(data));
      return packet;
    } catch (Exception e) {
      throw new IOException("Error instantiating packet " + packetId, e);
    }
  }

  public List<Packet> readPackets(byte[] data) throws IOException {
    List<Packet> packets = new ArrayList<>();
    ByteArrayInputStream bis = new ByteArrayInputStream(data);

    while (bis.available() > 0) {
      Packet p = readPacket(bis);
      if (p != null) {
        packets.add(p);
      }
    }
    return packets;
  }
}
