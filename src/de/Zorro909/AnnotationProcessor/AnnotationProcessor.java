package de.Zorro909.AnnotationProcessor;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.Zorro909.AnnotationProcessor.interfaces.ClassAnnotationVerifier;
import de.Zorro909.AnnotationProcessor.interfaces.FieldAnnotationValidator;
import de.Zorro909.AnnotationProcessor.interfaces.FieldSetter;
import de.Zorro909.AnnotationProcessor.interfaces.InstancedFieldGetter;
import de.Zorro909.AnnotationProcessor.interfaces.InstancedFieldSetter;
import de.Zorro909.AnnotationProcessor.interfaces.MethodRegistrar;
import de.Zorro909.AnnotationProcessor.interfaces.SimpleFieldGetter;
import de.Zorro909.AnnotationProcessor.interfaces.SimpleFieldSetter;
import de.Zorro909.AnnotationProcessor.interfaces.StaticFieldGetter;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

public class AnnotationProcessor<T extends Annotation> {

	private Class<T> annotation;

	private List<ElementType> targets = null;

	private InstancedFieldSetter<T> setter;

	private List<InstancedFieldGetter<T>> getters = new ArrayList<>();
	private List<FieldAnnotationValidator<T>> validators = new ArrayList<>();
	private List<ClassAnnotationVerifier<T>> classValidators = new ArrayList<>();

	private HashMap<T, Field> registeredStaticFields = new HashMap<T, Field>();
	private HashMap<Class<?>, List<WeakReference<Object>>> registeredInstances = new HashMap<>();
	private HashMap<Class<?>, Map<Field, T>> registeredInstancedFields = new HashMap<>();

	private MethodRegistrar<T> registrar;

	private Thread getterUpdates;

	public AnnotationProcessor(Class<T> annotation) {
		this.annotation = annotation;
		if (!annotation.isAnnotationPresent(Retention.class)
				|| annotation.getAnnotation(Retention.class).value() != RetentionPolicy.RUNTIME) {
			throw new RuntimeException(
					"Annotation needs to be available at Runtime {@Retention(RetentionPolicy.RUNTIME)}");
		}
		if (annotation.isAnnotationPresent(Target.class)) {
			targets = Arrays.asList(annotation.getAnnotation(Target.class).value());
		}
		AnnotationAPI.registerAnnotationProcessor(this);
	}

	public void registerFieldSetter(InstancedFieldSetter<T> setter) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			this.setter = setter;
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Setter!");
		}
	}

	public void registerFieldSetter(FieldSetter<T> setter) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			this.setter = (annot, field, cont, instance) -> setter.process(annot, field, cont);
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Setter!");
		}
	}

	public void registerFieldSetter(SimpleFieldSetter<T> setter) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			this.setter = (annot, field, cont, instance) -> setter.process(annot);
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Setter!");
		}
	}

	public void registerFieldGetter(InstancedFieldGetter<T> getter) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			getters.add(getter);
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Getter!");
		}
	}

	public void registerFieldGetter(StaticFieldGetter<T> getter) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			getters.add((annot, field, value, instance) -> getter.process(annot, field, value));
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Getter!");
		}
	}

	public void registerFieldGetter(SimpleFieldGetter<T> getter) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			getters.add((annot, field, value, instance) -> getter.process(annot, value));
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Getter!");
		}
	}

	public void registerFieldAnnotationValidator(FieldAnnotationValidator<T> validator) {
		if (targets == null || targets.contains(ElementType.FIELD)) {
			validators.add(validator);
		} else {
			throw new RuntimeException("Annotation " + annotation.getName()
					+ " can never target Fields and therefore can't be a field Getter!");
		}
	}

	public boolean processStaticFieldAnnotation(Class container, Field field, T specificAnnotation) {
		for (FieldAnnotationValidator<T> validator : validators) {
			if (!validator.validate(specificAnnotation, field))
				return false;
		}

		if (setter != null) {
			try {
				Object newValue = setter.process(specificAnnotation, field, container, null);
				if (newValue == null || field.getType().isAssignableFrom(newValue.getClass())) {
					field.set(null, newValue);
				} else {
					throw new RuntimeException("Setter produced wrong Type (expected: " + field.getType().getName()
							+ ", got: " + newValue.getClass().getName() + ")");
				}
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		registeredStaticFields.put(specificAnnotation, field);
		return true;
	}

	public boolean processStaticField(Class container, Field field) {
		if (!Modifier.isStatic(field.getModifiers())) {
			throw new RuntimeException("A Annotated Field needs to be static!");
		}

		T[] annotations = field.getAnnotationsByType(annotation);
		if (annotations.length == 0) {
			return false;
		}
		for (T annot : annotations) {
			if (!processStaticFieldAnnotation(container, field, annot))
				return false;
		}
		return true;
	}

	public boolean processStaticMethod(Method method, T specificAnnotation) {
		try {
			return registrar.process(specificAnnotation, new MethodInfo(specificAnnotation, method, null));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean processMethod(Method method, T specificAnnotation, Object instance) {
		try {
			return registrar.process(specificAnnotation, new MethodInfo(specificAnnotation, method, instance));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean processPackage(String packageName) {
		try (ScanResult scanResult = new ClassGraph().enableClassInfo().acceptPackages(packageName).scan()) {
			for (ClassInfo routeClassInfo : scanResult.getAllClasses()) {
				if (!processClass(Class.forName(routeClassInfo.getName()))) {
					return false;
				}
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

	public boolean processClassGraph(ClassGraph preConfiguredClassGraph) {
		preConfiguredClassGraph.enableClassInfo();
		try (ScanResult scanResult = preConfiguredClassGraph.scan()) {
			for (ClassInfo routeClassInfo : scanResult.getAllClasses()) {
				if (!processClass(Class.forName(routeClassInfo.getName()))) {
					return false;
				}
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

	public boolean processClass(Class clazz) {
		if (clazz.getAnnotationsByType(annotation).length != 0) {
			for (Annotation annot : clazz.getAnnotationsByType(annotation)) {
				for (ClassAnnotationVerifier<T> verifier : classValidators) {
					if (!verifier.verify((T) annot, clazz))
						return false;
				}
			}
		}
		for (Field field : clazz.getFields()) {
			if (field.getAnnotationsByType(annotation).length != 0) {
				if (Modifier.isStatic(field.getModifiers())) {
					if (!processStaticField(clazz, field))
						return false;
				} else {
					if (!AutoInstance.class.isAssignableFrom(clazz)) {
						return false;
					}
				}
			}
		}
		for (Method method : clazz.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())) {
				if (method.getAnnotation(annotation) != null) {
					if (!processStaticMethod(method, method.getAnnotation(annotation)))
						return false;
				}
			}
		}
		return true;
	}

	public void startGetterUpdates(final long delay) {
		if (getterUpdates != null) {
			if (getterUpdates.isAlive()) {
				getterUpdates.stop();
			}
			getterUpdates = null;
		}
		Thread getterUpdates = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				synchronized (registeredStaticFields) {
					for (T annotation : registeredStaticFields.keySet()) {
						Field field = registeredStaticFields.get(annotation);
						Object currentValue;
						try {
							currentValue = field.get(null);
						} catch (IllegalArgumentException | IllegalAccessException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							continue;
						}
						for (InstancedFieldGetter<T> getter : getters) {
							getter.process(annotation, field, currentValue, null);
						}
					}
				}
				synchronized (registeredInstances) {
					for (Class<?> clazz : registeredInstances.keySet()) {
						Map<Field, T> fields = registeredInstancedFields.get(clazz);
						Iterator<WeakReference<Object>> referenceIterator = registeredInstances.get(clazz).iterator();
						while (referenceIterator.hasNext()) {
							WeakReference<Object> instanceReference = referenceIterator.next();
							Object instance = instanceReference.get();
							if (instance == null) {
								referenceIterator.remove();
								continue;
							}
							for (Field field : fields.keySet()) {
								T annot = fields.get(field);
								Object currentValue;
								try {
									currentValue = field.get(instance);
								} catch (IllegalArgumentException | IllegalAccessException e) {
									e.printStackTrace();
									continue;
								}
								for (InstancedFieldGetter<T> getter : getters) {
									getter.process(annot, field, currentValue, null);
								}
							}
						}
					}
				}
			}
		});
		getterUpdates.setDaemon(true);
		getterUpdates.start();
	}

	public void processInstance(Object obj) throws IllegalArgumentException, IllegalAccessException {
		Class<?> clazz = obj.getClass();
		if (!registeredInstancedFields.containsKey(clazz)) {
			HashMap<Field, T> fields = new HashMap<>();
			for (Field field : clazz.getDeclaredFields()) {
				if (field.getAnnotationsByType(annotation).length != 0) {
					if (!Modifier.isStatic(field.getModifiers())) {
						for (T annot : field.getAnnotationsByType(annotation)) {
							for (FieldAnnotationValidator<T> validator : validators) {
								if (!validator.validate(annot, field))
									throw new RuntimeException("Field Annotation of " + annot.getClass().getName()
											+ " for " + clazz.getName() + ":" + field.getName()
											+ " failed the Validation!");
							}
							fields.put(field, annot);
						}
					}
				}
			}
			registeredInstancedFields.put(clazz, fields);
			registeredInstances.put(clazz, new ArrayList<WeakReference<Object>>());
		}
		registeredInstances.get(clazz).add(new WeakReference<Object>(obj));
		if (setter != null) {
			Map<Field, T> fields = registeredInstancedFields.get(clazz);
			for (Field field : fields.keySet()) {
				T annot = fields.get(field);
				try {
					Object newValue = setter.process(annot, field, clazz, obj);
					if (newValue == null || field.getType().isAssignableFrom(newValue.getClass())) {
						field.set(obj, newValue);
					} else {
						throw new RuntimeException("Setter produced wrong Type (expected: " + field.getType().getName()
								+ ", got: " + newValue.getClass().getName() + ")");
					}
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
			}
		}
		for (Method method : clazz.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers())) {
				if (method.getAnnotation(annotation) != null) {
					if (!processMethod(method, method.getAnnotation(annotation), obj)) {
						throw new RuntimeException("Processing Instanced Method " + method.getName() + " failed!");
					}
				}
			}
		}
	}

	public Class<T> getAnnotation() {
		return annotation;
	}

	public void setMethodRegistrar(MethodRegistrar<T> registrar) {
		this.registrar = registrar;
	}

	public void registerClassAnnotationVerifier(ClassAnnotationVerifier<T> verifier) {
		this.classValidators.add(verifier);
	}

}
