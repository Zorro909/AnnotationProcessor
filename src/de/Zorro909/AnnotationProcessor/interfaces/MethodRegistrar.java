package de.Zorro909.AnnotationProcessor.interfaces;

import de.Zorro909.AnnotationProcessor.MethodInfo;

public interface MethodRegistrar<T> {
    public boolean process(T annot, MethodInfo methodInfo);
}