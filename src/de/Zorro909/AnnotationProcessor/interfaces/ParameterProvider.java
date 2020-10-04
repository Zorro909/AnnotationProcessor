package de.Zorro909.AnnotationProcessor.interfaces;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

import de.Zorro909.AnnotationProcessor.MethodInfo;

public interface ParameterProvider<T extends Annotation> {
    public Object provide(T annotation, MethodInfo<?> method, Parameter param);
}