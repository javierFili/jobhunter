package com.jobhunter.shared.json;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

/** Stores a {@code List<String>} in a MySQL JSON column. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return Json.write(attribute == null ? List.of() : attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        List<String> value = Json.read(dbData, TYPE);
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
