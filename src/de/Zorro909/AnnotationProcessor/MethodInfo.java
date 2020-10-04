package de.Zorro909.AnnotationProcessor;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedList;

import de.Zorro909.AnnotationProcessor.interfaces.ParameterProvider;

public class MethodInfo<T extends Annotation> {

    private T annotation;
    private Method annotatedMethod;
    private WeakReference<Object> instance = null;

    private LinkedList<Object[]> parameters = new LinkedList<>();

    public MethodInfo(T annot, Method annotatedMethod, Object instance) {
        this.annotation = annot;
        this.annotatedMethod = annotatedMethod;
        if (instance != null)
            this.instance = new WeakReference<Object>(instance);

        computeParameters();
    }

    public T getAnnotation() {
        return annotation;
    }

    public Class<?> getDeclaringClass() {
        return annotatedMethod.getDeclaringClass();
    }

    public Method getAnnotatedMethod() {
        return annotatedMethod;
    }

    private void computeParameters() {
        Annotation[][] ann = annotatedMethod.getParameterAnnotations();
        int i = 0;
        for (Parameter parameter : annotatedMethod.getParameters()) {
            boolean found = false;
            for (Annotation annotation : ann[i]) {
                parameters.add(new Object[] { parameter.getType(), annotation, parameter });
                found = true;
            }
            i++;
            if (!found) {
                parameters.add(new Object[] { parameter.getType(), null, null });
            }
        }
    }

    public Object execute(Object... localParameters)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return execute(new HashMap<>(), localParameters);
    }

    public Object execute(
            HashMap<Class<? extends Annotation>, ParameterProvider> localParameterProviders,
            Object... localParameters)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (instance != null && instance.get() == null) {
            throw new RuntimeException("Instance is already garbage collected!");
        }
        Object[] params = new Object[parameters.size()];
        int localParametersPointer = 0;
        int paramsPointer = 0;
        for (Object[] para : parameters) {
            Class<?> type = (Class<?>) para[0];
            Annotation annot = (Annotation) para[1];
            Parameter param = (Parameter) para[2];
            if (annot != null) {
                if (localParameterProviders.containsKey(annot.annotationType())) {
                    params[paramsPointer] = localParameterProviders.get(annot.annotationType())
                            .provide(annot, this, param);
                    if (!type.isAssignableFrom(params[paramsPointer].getClass())) {
                        throw new IllegalArgumentException("Method " + annotatedMethod.getName()
                                + " expects parameter '" + param.getName() + "' to be of Type '"
                                + type.getName() + "' instead of "
                                + params[paramsPointer].getClass().getName());
                    }
                } else {
                    params[paramsPointer] = AnnotationAPI.provideParameterForMethod(annot, this,
                            param);
                }
            } else {
                if (localParametersPointer >= localParameters.length) {
                    throw new IllegalArgumentException("Method " + annotatedMethod.getName() + " expects "
                            + params.length
                            + " Parameters, but not all Parameters are provided (either explicit or implicitly)");
                }
                if (type.isAssignableFrom(localParameters[localParametersPointer].getClass())) {
                    params[paramsPointer] = localParameters[localParametersPointer];
                    localParametersPointer++;
                }
            }
            paramsPointer++;
        }
        if (instance != null) {
            return annotatedMethod.invoke(instance.get(), params);
        } else {
            return annotatedMethod.invoke(null, params);
        }
    }

}