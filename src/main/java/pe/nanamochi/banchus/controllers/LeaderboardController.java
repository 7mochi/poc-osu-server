package pe.nanamochi.banchus.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.entities.db.*;
import pe.nanamochi.banchus.services.*;

@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class LeaderboardController {
  private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

  private final UserService userService;
  private final SessionService sessionService;
  private final BeatmapService beatmapService;
  private final ScoreService scoreService;

  @GetMapping("/osu-osz2-getscores.php")
  public ResponseEntity<String> getScores(
      @RequestParam(name = "us") String username,
      @RequestParam(name = "ha") String passwordMd5,
      @RequestParam(name = "s") Boolean skipScores,
      @RequestParam(name = "vv") Integer requestVersion,
      @RequestParam(name = "v") Integer leaderboardType,
      @RequestParam(name = "c") String beatmapMd5,
      @RequestParam(name = "f") String beatmapFilename,
      @RequestParam(name = "m") Integer gamemode,
      @RequestParam(name = "i") Integer beatmapSetId,
      @RequestParam(name = "mods") Integer modsBitmask,
      @RequestParam(name = "h") String mapPackageHash,
      @RequestParam(name = "a") Boolean aqnFilesFound) {
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Session session = sessionService.getPrimarySessionByUsername(user.getUsername());
    if (session == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // This is a quirk of the osu! client, where it adjusts this value only after it sends the
    // packet to the server; so we need to adjust
    Mode mode = Mode.fromValue(gamemode);
    List<Mods> mods = Mods.filterInvalidModCombinations(Mods.fromBitmask(modsBitmask), mode);

    // TODO: update user stats if they have changed

    // Fetch the beatmap with this md5
    Beatmap beatmap = beatmapService.getOrCreateBeatmap(beatmapMd5);
    if (beatmap == null) {
      return ResponseEntity.ok("-1|false");
    }

    // Create filter parameters for score fetching based on the leaderboard type
    LeaderboardType type = LeaderboardType.fromValue(leaderboardType);
    Integer modsToFilter = null;
    CountryCode country = null;

    switch (type) {
      case MODS -> modsToFilter = Mods.toBitmask(mods);
      case COUNTRY -> country = user.getCountry();
      case FRIENDS -> logger.warn("Friends leaderboard not implemented yet");
    }

    // Fetch our top 50 scores for the leaderboard
    List<Score> scores =
        scoreService.getBeatmapLeaderboard(
            beatmap, mode, modsToFilter, SubmissionStatus.BEST, country);

    // Fetch our personal best score for the beatmap
    Score personalBestScore = scoreService.getBestScore(beatmap, user);

    return ResponseEntity.ok(
        scoreService.formatLeaderboardResponse(scores, personalBestScore, user, beatmap));
  }
}
