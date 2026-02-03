package pe.nanamochi.banchus.services;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.repositories.ChannelRepository;

@Service
@RequiredArgsConstructor
public class ChannelService {
  private final ChannelRepository channelRepository;

  public List<Channel> getAllChannels() {
    return channelRepository.findAll();
  }

  public List<Channel> findByAutoJoin(boolean autoJoin) {
    return channelRepository.findByAutoJoin(autoJoin);
  }

  public Channel findByName(String name) {
    return channelRepository.findByName(name);
  }

  public Channel save(Channel channel) {
    return channelRepository.save(channel);
  }

  public void delete(Channel channel) {
    channelRepository.delete(channel);
  }

  public boolean canReadChannel(Channel channel, int userPrivileges) {
    if (channel.getReadPrivileges() == 0) {
      return true;
    }

    return (userPrivileges & channel.getReadPrivileges()) == 0;
  }

  public boolean canWriteChannel(Channel channel, int userPrivileges) {
    if (channel.getWritePrivileges() == 0) {
      return true;
    }

    return (userPrivileges & channel.getWritePrivileges()) != 0;
  }
}
