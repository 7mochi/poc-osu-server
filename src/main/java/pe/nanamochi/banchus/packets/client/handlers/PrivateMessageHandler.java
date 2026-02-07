package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.PrivateMessagePacket;

@Component
public class PrivateMessageHandler extends AbstractPacketHandler<PrivateMessagePacket> {
  private static final Logger logger = LoggerFactory.getLogger(PrivateMessageHandler.class);

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_PRIVATE_MESSAGE;
  }

  @Override
  public Class<PrivateMessagePacket> getPacketClass() {
    return PrivateMessagePacket.class;
  }

  @Override
  public void handle(
      PrivateMessagePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
  }
}
