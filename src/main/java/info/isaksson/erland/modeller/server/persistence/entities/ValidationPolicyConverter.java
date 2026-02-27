package info.isaksson.erland.modeller.server.persistence.entities;

import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists ValidationPolicy as a stable lowercase string (none|basic|strict).
 */
@Converter(autoApply = false)
public class ValidationPolicyConverter implements AttributeConverter<ValidationPolicy, String> {

    @Override
    public String convertToDatabaseColumn(ValidationPolicy attribute) {
        return attribute == null ? null : attribute.wireValue();
    }

    @Override
    public ValidationPolicy convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return ValidationPolicy.tryParse(dbData).orElse(ValidationPolicy.NONE);
    }
}
