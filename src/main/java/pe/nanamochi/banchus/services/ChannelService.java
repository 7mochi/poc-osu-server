package pe.nanamochi.banchus.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.Privileges;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.repositories.ChannelRepository;
import pe.nanamochi.banchus.utils.PrivilegesUtil;

import java.util.List;

@Service
public class ChannelService {

    @Autowired private ChannelRepository channelRepository;

    public List<Channel> findByAutoJoinTrue() {
        return channelRepository.findByAutoJoinTrue();
    }

    public Channel findByName(String name) {
        return channelRepository.findByName(name);
    }

    public boolean canReadChannel(Channel channel, int userPrivileges) {
        if (channel.getReadPrivileges() == Privileges.UNRESTRICTED.bitPosition()) {
            return true;
        }

        return PrivilegesUtil.has(channel.getReadPrivileges(), userPrivileges);
    }
}
