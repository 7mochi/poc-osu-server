package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.ServerPrivileges;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.StartSpectatingPacket;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.*;

@Component
@RequiredArgsConstructor
public class StartSpectatingHandler extends AbstractPacketHandler<StartSpectatingPacket> {
  private static final Logger logger = LoggerFactory.getLogger(StartSpectatingHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final SpectatorService spectatorService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_START_SPECTATING;
  }

  @Override
  public Class<StartSpectatingPacket> getPacketClass() {
    return StartSpectatingPacket.class;
  }

  @Override
  public void handle(
      StartSpectatingPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());

    if (session.getUser().isRestricted()) {
      return;
    }

    Session hostSession = sessionService.getPrimarySessionByUserId(packet.getUserId());

    if (hostSession == null) {
      logger.warn(
          "The user ({}) attempted to spectate another user ({}) who is offline.",
          session.getUser().getId(),
          packet.getUserId());
      return;
    }

    if (packet.getUserId() == session.getUser().getId()) {
      logger.warn("Failed to start spectating: Player tried to spectate himself.");
      return;
    }

    if (packet.getUserId() == 1) {
      logger.warn("Tried to spectate BanchoBot.");
      return;
    }

    // TODO: tournament client check

    spectatorService.add(hostSession.getId(), session.getId());

    session.setSpectatorHostSessionId(hostSession.getId());
    session = sessionService.updateSession(session);

    // Fetch the #spectator channel
    Channel spectatorChannel = channelService.findByName("#spec_" + hostSession.getId());
    if (spectatorChannel == null) {
      spectatorChannel = new Channel();
      spectatorChannel.setName("#spec_" + hostSession.getId());
      spectatorChannel.setTopic("Channel for spectator host ID " + hostSession.getId());
      spectatorChannel.setReadPrivileges(ServerPrivileges.UNRESTRICTED.getValue());
      spectatorChannel.setWritePrivileges(ServerPrivileges.UNRESTRICTED.getValue());
      spectatorChannel.setAutoJoin(false);
      spectatorChannel.setTemporary(true);
      spectatorChannel = channelService.save(spectatorChannel);

      // Add to and inform both host and spectator of the #spectator channel
      for (Session bothSession : List.of(session, hostSession)) {
        channelMembersService.addMemberToChannel(spectatorChannel, bothSession);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        packetWriter.writePacket(
            stream,
            new ChannelAvailableAutoJoinPacket("#spectator", spectatorChannel.getTopic(), 2));
        packetWriter.writePacket(stream, new ChannelJoinSuccessPacket("#spectator"));
        packetBundleService.enqueue(bothSession.getId(), new PacketBundle(stream.toByteArray()));
      }
    } else {
      // Join the #spectator channel
      channelMembersService.addMemberToChannel(spectatorChannel, session);

      // Inform everyone in the #spectator channel that we joined
      Set<UUID> currentChannelMembersId =
          channelMembersService.getMembers(spectatorChannel.getId());
      for (UUID memberSessionId : currentChannelMembersId) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        packetWriter.writePacket(
            stream,
            new ChannelAvailablePacket(
                "#spectator", spectatorChannel.getTopic(), currentChannelMembersId.size()));
        packetBundleService.enqueue(memberSessionId, new PacketBundle(stream.toByteArray()));
      }
    }

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new SpectatorJoinedPacket(session.getUser().getId()));
    packetBundleService.enqueue(hostSession.getId(), new PacketBundle(stream.toByteArray()));

    for (UUID spectatorSessionId : spectatorService.getMembers(hostSession.getId())) {
      if (spectatorSessionId.equals(session.getId())) {
        continue;
      }

      stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new SpectatorJoinedPacket(session.getUser().getId()));
      packetBundleService.enqueue(spectatorSessionId, new PacketBundle(stream.toByteArray()));
    }
  }
}
