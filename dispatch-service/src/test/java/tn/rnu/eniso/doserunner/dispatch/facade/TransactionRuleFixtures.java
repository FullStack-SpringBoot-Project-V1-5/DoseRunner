package tn.rnu.eniso.doserunner.dispatch.facade;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

public final class TransactionRuleFixtures {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Transactional
    public @interface SpringTransaction { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @jakarta.transaction.Transactional
    public @interface JakartaTransaction { }

    @Transactional
    public static class DirectSpring {
        @Transactional public void action() { }
    }

    @SpringTransaction
    public static class ComposedSpring {
        @SpringTransaction public void action() { }
    }

    @jakarta.transaction.Transactional
    public static class DirectJakarta {
        @jakarta.transaction.Transactional public void action() { }
    }

    @JakartaTransaction
    public static class ComposedJakarta {
        @JakartaTransaction public void action() { }
    }

    private TransactionRuleFixtures() { }
}
