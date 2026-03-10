package org.example.kalkulationsprogramm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        String message,
        String constraint,
        List<Field> fields,
        String detail
) {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Field(String field, String label, String message) { }
}
