package de.Zorro909.AnnotationProcessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.HashMap;

import de.Zorro909.AnnotationProcessor.interfaces.ParameterProvider;

public class AnnotationAPI {

	private static HashMap<Class<? extends Annotation>, AnnotationProcessor> processors = new HashMap<>();
	private static HashMap<Class<? extends Annotation>, ParameterProvider> parameterProviders = new HashMap<>();
	
	public static final String test = "Hallo";
	
	public static void registerInstance(Object obj) {
		for (AnnotationProcessor proc : processors.values()) {
			try {
				proc.processInstance(obj);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	static void registerAnnotationProcessor(AnnotationProcessor proc) {
		processors.put(proc.getAnnotation(), proc);
	}

	public static <T extends Annotation> void registerParameterProvider(Class<T> annotation,
			ParameterProvider<T> provider) {
		parameterProviders.put(annotation, provider);
	}

	public static <T extends Annotation> Object provideParameterForMethod(T annotation, MethodInfo<?> method,
			Parameter param) {
		if (parameterProviders.containsKey(annotation.annotationType())) {
			return parameterProviders.get(annotation.annotationType()).provide(annotation, method, param);
		}
		return null;
	}

	public static <T extends Annotation> boolean isParameterProvidable(T annotation) {
		return parameterProviders.containsKey(annotation.annotationType());
	}

}
