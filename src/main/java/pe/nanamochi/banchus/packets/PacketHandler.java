package pe.nanamochi.banchus.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;

@Component
public class PacketHandler {
  private final Map<Packets, AbstractPacketHandler<?>> handlers;

  public PacketHandler(List<AbstractPacketHandler<?>> beans) {
    this.handlers =
        beans.stream()
            .collect(
                Collectors.toMap(
                    AbstractPacketHandler::getPacketType,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException("Duplicate handler for " + a.getPacketType());
                    },
                    () -> new EnumMap<>(Packets.class)));
  }

  public void handlePacket(Packet packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {

    if (packet == null) return;

    AbstractPacketHandler<?> handler = handlers.get(packet.getPacketType());
    if (handler == null) {
      return;
    }

    dispatch(handler, packet, session, responseStream);
  }

  private <T extends Packet> void dispatch(
      AbstractPacketHandler<T> handler,
      Packet packet,
      Session session,
      ByteArrayOutputStream responseStream)
      throws IOException {

    // Prevent handler to be executed for restricted users
    if (session.getUser().isRestricted() && handler.isRestricted()) return;
    handler.handle((handler.getPacketClass().cast(packet)), session, responseStream);
  }
}
