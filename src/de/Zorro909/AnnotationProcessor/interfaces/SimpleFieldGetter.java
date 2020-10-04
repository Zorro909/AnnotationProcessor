package de.Zorro909.AnnotationProcessor.interfaces;

public interface SimpleFieldGetter<T> {
    public void process(T annot, Object currentValue);
}