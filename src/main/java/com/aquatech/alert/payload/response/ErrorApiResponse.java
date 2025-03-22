package com.aquatech.alert.payload.response;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class ErrorApiResponse<T> extends ApiResponse<T> {
    @Builder.Default
    private int errorCode = -1;
    private T errors;

    public ErrorApiResponse(String message) {
        super(message);
        this.success = (Boolean) false;
    }

    public ErrorApiResponse(String message, int errorCode) {
        super(message);
        this.success = (Boolean) false;
        this.errorCode = errorCode;
    }
}