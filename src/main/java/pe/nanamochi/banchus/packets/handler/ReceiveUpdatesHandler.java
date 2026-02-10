package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.client.ReceiveUpdatesPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component
@HandleClientPacket(value = Packets.OSU_RECEIVE_UPDATES, checkForRestriction = true)
public class ReceiveUpdatesHandler extends AbstractPacketHandler<ReceiveUpdatesPacket> {
  private static final Logger logger = LoggerFactory.getLogger(ReceiveUpdatesHandler.class);

  @Override
  public void handle(
      ReceiveUpdatesPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {}
}
