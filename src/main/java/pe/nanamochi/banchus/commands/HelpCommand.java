package pe.nanamochi.banchus.commands;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.User;

@Component
@Command(name = "help", documentation = "Displays a list of available commands.")
@RequiredArgsConstructor
public class HelpCommand extends BaseCommand {
  private final ApplicationContext context;

  @Value("${banchus.command-prefix}")
  private String commandPrefix;

  @Override
  public String processCommand(User user, String trigger, String[] args) {
    StringBuilder output = new StringBuilder();
    context
        .getBeansWithAnnotation(Command.class)
        .values()
        .forEach(
            processor -> {
              BaseCommand commandProcessor = (BaseCommand) processor;
              Command commandAnnotation = commandProcessor.getClass().getAnnotation(Command.class);
              output
                  .append(commandPrefix)
                  .append(commandAnnotation.name())
                  .append(" - ")
                  .append(commandAnnotation.documentation())
                  .append("\n");
            });
    return output.toString();
  }
}
