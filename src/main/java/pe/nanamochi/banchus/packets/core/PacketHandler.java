package pe.nanamochi.banchus.packets.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;

@Component
public class PacketHandler {
  private static final Logger logger = LoggerFactory.getLogger(PacketHandler.class);
  private final Map<Packets, AbstractPacketHandler<?>> handlers;

  public PacketHandler(List<AbstractPacketHandler<?>> beans) {
    this.handlers =
        beans.stream()
            .collect(Collectors.toMap(AbstractPacketHandler::getPacketType, Function.identity()));
  }

  public void handlePacket(Packet packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (packet == null) return;

    AbstractPacketHandler<?> handler = handlers.get(packet.getPacketType());
    if (handler == null) return;

    dispatch(handler, packet, session, responseStream);
  }

  private <T extends Packet> void dispatch(
      AbstractPacketHandler<T> handler,
      Packet packet,
      Session session,
      ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", packet.getPacketType());
    HandleClientPacket annotation = handler.getClass().getAnnotation(HandleClientPacket.class);
    boolean shouldCheck = (annotation != null) && annotation.checkForRestriction();

    if (shouldCheck && session.getUser().isRestricted()) return;

    handler.handle(handler.getPacketClass().cast(packet), session, responseStream);
  }
}
