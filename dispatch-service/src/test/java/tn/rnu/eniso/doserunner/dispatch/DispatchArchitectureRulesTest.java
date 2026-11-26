package tn.rnu.eniso.doserunner.dispatch;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.dispatch.facade.TransactionRuleFixtures;
import tn.rnu.eniso.doserunner.dispatch.facade.TransactionRuleFixtures.JakartaTransaction;
import tn.rnu.eniso.doserunner.dispatch.facade.TransactionRuleFixtures.SpringTransaction;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchArchitectureRulesTest {
    @ParameterizedTest
    @MethodSource("transactionCases")
    void rejectsTransactionsOutsideFacadesAndAllowsFacadeTransactions(
            ArchRule rule, Class<?> rejected, Class<?> allowed) {
        var importer = new ClassFileImporter();
        assertThat(rule.evaluate(importer.importClasses(rejected)).hasViolation())
                .as("%s must reject %s", rule.getDescription(), rejected.getSimpleName()).isTrue();
        rule.check(importer.importClasses(allowed, NonTransactional.class));
    }

    static Stream<Arguments> transactionCases() {
        return Stream.of(
                Arguments.of(DispatchArchitectureTest.springTransactionClassesBelongToFacades,
                        DirectSpring.class, TransactionRuleFixtures.DirectSpring.class),
                Arguments.of(DispatchArchitectureTest.springTransactionClassesBelongToFacades,
                        ComposedSpring.class, TransactionRuleFixtures.ComposedSpring.class),
                Arguments.of(DispatchArchitectureTest.springTransactionMethodsBelongToFacades,
                        DirectSpring.class, TransactionRuleFixtures.DirectSpring.class),
                Arguments.of(DispatchArchitectureTest.springTransactionMethodsBelongToFacades,
                        ComposedSpring.class, TransactionRuleFixtures.ComposedSpring.class),
                Arguments.of(DispatchArchitectureTest.jakartaTransactionClassesBelongToFacades,
                        DirectJakarta.class, TransactionRuleFixtures.DirectJakarta.class),
                Arguments.of(DispatchArchitectureTest.jakartaTransactionClassesBelongToFacades,
                        ComposedJakarta.class, TransactionRuleFixtures.ComposedJakarta.class),
                Arguments.of(DispatchArchitectureTest.jakartaTransactionMethodsBelongToFacades,
                        DirectJakarta.class, TransactionRuleFixtures.DirectJakarta.class),
                Arguments.of(DispatchArchitectureTest.jakartaTransactionMethodsBelongToFacades,
                        ComposedJakarta.class, TransactionRuleFixtures.ComposedJakarta.class));
    }

    @Transactional
    static class DirectSpring {
        @Transactional public void action() { }
    }

    static class NonTransactional {
        public void action() { }
    }

    @SpringTransaction
    static class ComposedSpring {
        @SpringTransaction public void action() { }
    }

    @jakarta.transaction.Transactional
    static class DirectJakarta {
        @jakarta.transaction.Transactional public void action() { }
    }

    @JakartaTransaction
    static class ComposedJakarta {
        @JakartaTransaction public void action() { }
    }
}
