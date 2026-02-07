package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.PongPacket;

@Component
public class PongHandler extends AbstractPacketHandler<PongPacket> {
  private static final Logger logger = LoggerFactory.getLogger(PongHandler.class);

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_PONG;
  }

  @Override
  public Class<PongPacket> getPacketClass() {
    return PongPacket.class;
  }

  @Override
  public void handle(PongPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
  }
}
