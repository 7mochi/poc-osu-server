package pe.nanamochi.banchus.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pe.nanamochi.banchus.entities.BeatmapRankedStatus;

@Converter(autoApply = true)
public class BeatmapRankedStatusConverter
    implements AttributeConverter<BeatmapRankedStatus, Integer> {

  @Override
  public Integer convertToDatabaseColumn(BeatmapRankedStatus status) {
    return status != null ? status.getValue() : null;
  }

  @Override
  public BeatmapRankedStatus convertToEntityAttribute(Integer value) {
    return value != null ? BeatmapRankedStatus.fromValue(value) : null;
  }
}
