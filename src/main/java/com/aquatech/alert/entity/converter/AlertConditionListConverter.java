package com.aquatech.alert.entity.converter;

import com.aquatech.alert.model.AlertCondition;
import com.aquatech.alert.utils.CommonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class AlertConditionListConverter implements AttributeConverter<List<AlertCondition>, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<AlertCondition> attribute) {
        if (CommonUtils.isEmptyCollection(attribute)) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Error converting alert conditions to JSON", e);
        }
    }

    @Override
    public List<AlertCondition> convertToEntityAttribute(String dbData) {
        if (CommonUtils.isEmptyString(dbData)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Error converting alert conditions from JSON", e);
        }
    }
}
