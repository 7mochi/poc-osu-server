package pe.nanamochi.banchus.commands;

import java.util.Arrays;
import pe.nanamochi.banchus.entities.ServerPrivileges;
import pe.nanamochi.banchus.entities.db.User;

public abstract class BaseCommand {
  protected boolean shouldExecute(
      String prefix, String trigger, int userPrivileges, boolean isMultiplayer) {
    Command command = this.getClass().getAnnotation(Command.class);
    if (!trigger.startsWith(prefix + command.name())) {
      return false;
    }
    if (command.privileges().length > 0) {
      var userPrivs = ServerPrivileges.fromBitmask(userPrivileges);
      boolean hasAny = Arrays.stream(command.privileges()).anyMatch(userPrivs::contains);
      if (!hasAny) return false;
    }
    return !command.multiplayer() || isMultiplayer;
  }

  abstract String processCommand(User user, String trigger, String[] args);
}
