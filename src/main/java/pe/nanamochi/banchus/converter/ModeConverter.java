package pe.nanamochi.banchus.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pe.nanamochi.banchus.entities.Mode;

@Converter(autoApply = true)
public class ModeConverter implements AttributeConverter<Mode, Integer> {

  @Override
  public Integer convertToDatabaseColumn(Mode mode) {
    return mode != null ? mode.getValue() : null;
  }

  @Override
  public Mode convertToEntityAttribute(Integer value) {
    return value != null ? Mode.fromValue(value) : null;
  }
}
