package pe.nanamochi.banchus.services;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.repositories.ChannelMembersRepository;

@Service
@RequiredArgsConstructor
public class ChannelMembersService {
  private final ChannelMembersRepository channelMembersRepository;

  public void addMemberToChannel(Channel channel, Session session) {
    channelMembersRepository.add(channel.getId(), session.getId());
  }

  public Set<UUID> getMembers(UUID channelId) {
    return channelMembersRepository.getMembers(channelId);
  }

  public UUID removeMemberFromChannel(Channel channel, Session session) {
    return channelMembersRepository.remove(channel.getId(), session.getId());
  }
}
