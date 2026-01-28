package pe.nanamochi.banchus.services;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.repositories.ChannelMembersRedisRepository;

@Service
public class ChannelMembersRedisService {
  private final ChannelMembersRedisRepository channelMembersRedisRepository;

  public ChannelMembersRedisService(ChannelMembersRedisRepository channelMembersRedisRepository) {
    this.channelMembersRedisRepository = channelMembersRedisRepository;
  }

  public void addMemberToChannel(Channel channel, Session session) {
    channelMembersRedisRepository.add(channel.getId(), session.getId());
  }

  public Set<UUID> getMembers(UUID channelId) {
    return channelMembersRedisRepository.getMembers(channelId);
  }

  public UUID removeMemberFromChannel(Channel channel, Session session) {
    return channelMembersRedisRepository.remove(channel.getId(), session.getId());
  }
}
