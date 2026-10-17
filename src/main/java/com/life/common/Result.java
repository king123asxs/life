package com.life.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result<T> {
    private Boolean success;
    private T data;
    private String error;

    public static <T> Result<T> ok(T data) { return new Result<>(true, data, null); }
    public static Result<Void> ok() { return new Result<>(true, null, null); }
    public static <T> Result<T> fail(String msg) { return new Result<>(false, null, msg); }
}
