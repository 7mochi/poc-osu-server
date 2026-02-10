package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.client.UserStatsRequestPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component
@HandleClientPacket(value = Packets.OSU_USER_STATS_REQUEST, checkForRestriction = true)
public class UserStatsRequestHandler extends AbstractPacketHandler<UserStatsRequestPacket> {
  private static final Logger logger = LoggerFactory.getLogger(UserStatsRequestHandler.class);

  @Override
  public void handle(
      UserStatsRequestPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {}
}
