package de.Zorro909.AnnotationProcessor.interfaces;

public interface ClassAnnotationVerifier<T> {
    public boolean verify(T annot, Class clazz);
}
