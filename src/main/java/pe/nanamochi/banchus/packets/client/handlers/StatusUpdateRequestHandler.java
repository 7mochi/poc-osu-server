package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.StatusUpdateRequestPacket;

@Component
public class StatusUpdateRequestHandler extends AbstractPacketHandler<StatusUpdateRequestPacket> {
  private static final Logger logger = LoggerFactory.getLogger(StatusUpdateRequestHandler.class);

  @Override
  public Packets getPacketType() {
    return Packets.OSU_STATUS_UPDATE_REQUEST;
  }

  @Override
  public Class<StatusUpdateRequestPacket> getPacketClass() {
    return StatusUpdateRequestPacket.class;
  }

  @Override
  public void handle(
      StatusUpdateRequestPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
  }
}
