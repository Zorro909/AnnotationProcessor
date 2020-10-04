package de.Zorro909.AnnotationProcessor.interfaces;

import java.lang.reflect.Field;

public interface FieldSetter<T> {
    public Object process(T annot, Field annotatedField, Class container);
}