package pe.nanamochi.banchus.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.DirectDisplayMode;

@Component
public class DirectDisplayModeConverter implements Converter<String, DirectDisplayMode> {

  @Override
  public DirectDisplayMode convert(String source) {
    int value = Integer.parseInt(source);
    for (DirectDisplayMode mode : DirectDisplayMode.values()) {
      if (mode.getValue() == value) {
        return mode;
      }
    }
    throw new IllegalArgumentException("Invalid DirectDisplayMode: " + source);
  }
}
