package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.UserStatsRequestPacket;

@Component
public class UserStatsRequestHandler extends AbstractPacketHandler<UserStatsRequestPacket> {
  private static final Logger logger = LoggerFactory.getLogger(UserStatsRequestHandler.class);

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_USER_STATS_REQUEST;
  }

  @Override
  public Class<UserStatsRequestPacket> getPacketClass() {
    return UserStatsRequestPacket.class;
  }

  @Override
  public void handle(
      UserStatsRequestPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
  }
}
