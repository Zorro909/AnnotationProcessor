package de.Zorro909.AnnotationProcessor;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;

public class Test extends AutoInstance {

    @Under
    public String testString;

    public Test() {
        super();
        // AnnotationAPI.registerInstance(this);
    }

    public static Test test;

    public static int testInt = 0;

    public static void main(String[] args) throws InterruptedException {
        AnnotationAPI.registerParameterProvider(Over.class,
                (annot, info, param) -> annot.value() + ++testInt);

        AnnotationProcessor<Ping> proc = new AnnotationProcessor<>(Ping.class);
        proc.setMethodRegistrar((Ping annot, MethodInfo info) -> {
            try {
                final int testIn = testInt;
                info.execute("Hello World!" + testIn);
                new Thread(() -> {
                    try {
                        Thread.sleep(20000L);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    try {
                        System.out.println("Trying to execute");
                        info.execute("Bye" + testIn);
                    } catch (IllegalAccessException | IllegalArgumentException
                            | InvocationTargetException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }).start();
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return true;
        });
        System.out.println(proc.processPackage("de.Zorro909.AnnotationProcessor"));
        boolean created = false;
        while (true) {
            Thread.sleep(5000L);
            System.gc();
            if (!created) {
                new Test();
                created = true;
            }
        }
    }

    @Ping
    public void ping(String text, @Over("hello") String ping) {
        System.out.println(ping);
        System.out.println(text);
    }

}

@Retention(RetentionPolicy.RUNTIME)
@interface Ping {

}

@Retention(RetentionPolicy.RUNTIME)
@interface Under {

}

@Retention(RetentionPolicy.RUNTIME)
@interface Over {
    String value();
}