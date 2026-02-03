package pe.nanamochi.banchus.commands;

import java.util.Arrays;
import java.util.List;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.User;

@Component
public class CommandProcessor {
  private final List<? extends BaseCommand> commands;

  public CommandProcessor(List<? extends BaseCommand> beans) {
    this.commands =
        beans.stream()
            .filter(bean -> AnnotationUtils.findAnnotation(bean.getClass(), Command.class) != null)
            .map(BaseCommand.class::cast)
            .toList();
  }

  public String handle(String prefix, String message, User user, Channel channel) {
    BaseCommand command =
        this.commands.stream()
            .filter(
                processor ->
                    processor.shouldExecute(
                        prefix,
                        message,
                        user.getPrivileges(),
                        "#multiplayer".equals(channel.getName())))
            .findFirst()
            .orElse(null);
    if (command == null) return null;

    String[] parts = message.split(" ");
    String trigger = parts[0];
    String[] args = Arrays.copyOfRange(parts, 1, parts.length);

    return command.processCommand(user, trigger, args);
  }
}
