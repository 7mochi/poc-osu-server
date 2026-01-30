package pe.nanamochi.banchus.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pe.nanamochi.banchus.entities.SubmissionStatus;

@Converter(autoApply = true)
public class SubmissionStatusConverter implements AttributeConverter<SubmissionStatus, Integer> {
  @Override
  public Integer convertToDatabaseColumn(SubmissionStatus submissionStatus) {
    return submissionStatus != null ? submissionStatus.getValue() : null;
  }

  @Override
  public SubmissionStatus convertToEntityAttribute(Integer value) {
    return value != null ? SubmissionStatus.fromValue(value) : null;
  }
}
