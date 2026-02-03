package pe.nanamochi.banchus.commands;

import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.User;

@Component
@Command(
    name = "roll",
    documentation = "Roll a random number between 0 and a given number.",
    multiplayer = true)
public class RollCommand extends BaseCommand {
  private static final Logger logger = LoggerFactory.getLogger(RollCommand.class);

  @Override
  String processCommand(User user, String trigger, String[] args) {
    logger.info("Processing command !roll");
    int max = 100;
    if (args.length != 0) {
      max = Math.min(NumberUtils.toInt(args[0], max), 32767);
    }
    return user.getUsername()
        + " rolls "
        + ThreadLocalRandom.current().nextInt(0, max)
        + " points.";
  }
}
