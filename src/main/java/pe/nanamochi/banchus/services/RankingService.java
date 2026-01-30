package pe.nanamochi.banchus.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.RankingRepository;

@Service
public class RankingService {
  @Autowired private RankingRepository rankingRepository;

  public long getGlobalRank(Mode mode, User user) {
    return rankingRepository.getGlobalRank(mode, user);
  }

  public long getCountryRank(Mode mode, User user, CountryCode countryCode) {
    return rankingRepository.getCountryRank(mode, user, countryCode);
  }

  public long updateRanking(Mode mode, User user, Stat stat) {
    return rankingRepository.updateRanking(mode, user, stat);
  }
}
