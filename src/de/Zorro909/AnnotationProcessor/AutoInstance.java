package de.Zorro909.AnnotationProcessor;

public abstract class AutoInstance {

    public AutoInstance() {
        AnnotationAPI.registerInstance(this);
    }

}
