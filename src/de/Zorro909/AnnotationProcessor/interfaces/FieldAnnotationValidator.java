package de.Zorro909.AnnotationProcessor.interfaces;

import java.lang.reflect.Field;

public interface FieldAnnotationValidator<T> {
    public boolean validate(T annot, Field annotatedField);
}
