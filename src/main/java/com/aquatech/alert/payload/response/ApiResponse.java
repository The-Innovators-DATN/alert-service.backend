package com.aquatech.alert.payload.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ApiResponse<T> {
    protected String message;

    protected Boolean success;

    public ApiResponse(String message) {
        this.message = message;
    }
}
