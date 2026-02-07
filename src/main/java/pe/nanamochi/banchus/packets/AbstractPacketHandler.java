package pe.nanamochi.banchus.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import pe.nanamochi.banchus.entities.db.Session;

public abstract class AbstractPacketHandler<T extends Packet> {

  public boolean isRestricted() {
    return false;
  }

  public abstract Packets getPacketType();

  public abstract Class<T> getPacketClass();

  public abstract void handle(T packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException;
}
