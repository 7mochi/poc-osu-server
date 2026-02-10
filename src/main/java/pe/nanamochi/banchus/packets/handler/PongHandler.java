package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.client.PongPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component
@HandleClientPacket(value = Packets.OSU_PONG, checkForRestriction = true)
public class PongHandler extends AbstractPacketHandler<PongPacket> {
  private static final Logger logger = LoggerFactory.getLogger(PongHandler.class);

  @Override
  public void handle(PongPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {}
}
