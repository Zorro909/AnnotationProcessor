package de.Zorro909.AnnotationProcessor.interfaces;

import java.lang.reflect.Field;

public interface InstancedFieldGetter<T> {
    public void process(T annot, Field annotatedField, Object currentValue, Object instance);
}