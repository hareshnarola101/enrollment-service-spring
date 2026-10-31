package com.hareshnarola.enrollment.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super("%s %s was not found".formatted(resource, id));
    }
}
