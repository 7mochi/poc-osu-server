package pe.nanamochi.banchus.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import pe.nanamochi.banchus.entities.ServerPrivileges;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {
  String name();

  ServerPrivileges[] privileges() default {ServerPrivileges.UNRESTRICTED};

  String documentation() default "";

  boolean multiplayer() default false;
}
