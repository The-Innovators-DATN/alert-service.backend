package com.aquatech.alert.payload.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class SuccessApiResponse<T> extends ApiResponse<T> {
    private T data;

    public SuccessApiResponse(T data) {
        super("Success");
        this.success = (Boolean) true;
        this.data = data;
    }

    public SuccessApiResponse(String message, T data) {
        this.message = message;
        this.success = (Boolean) true;
        this.data = data;
    }

    public SuccessApiResponse(String message) {
        super(message);
        this.success = (Boolean) true;
    }

    public SuccessApiResponse() {
        super("Success");
        this.success = (Boolean) true;
    }
}