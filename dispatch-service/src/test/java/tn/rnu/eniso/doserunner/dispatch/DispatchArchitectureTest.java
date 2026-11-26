package tn.rnu.eniso.doserunner.dispatch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.conditions.ArchConditions.beAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beMetaAnnotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(packages = "tn.rnu.eniso.doserunner.dispatch",
        importOptions = ImportOption.DoNotIncludeTests.class)
class DispatchArchitectureTest {
    private static final String ROOT = "tn.rnu.eniso.doserunner.dispatch.";

    @ArchTest
    static final ArchRule presentationDelegatesThroughFacades = noClasses()
            .that().resideInAPackage(ROOT + "pres..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + "dao..", ROOT + "bo..", ROOT + "messaging..", ROOT + "config..");

    @ArchTest
    static final ArchRule lowerLayersDoNotDependOnPresentation = noClasses()
            .that().resideInAnyPackage(ROOT + "facade..", ROOT + "bo..", ROOT + "dao..", ROOT + "messaging..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + "pres..");

    @ArchTest
    static final ArchRule businessObjectsRemainFrameworkFree = noClasses()
            .that().resideInAPackage(ROOT + "bo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.servlet..", "jakarta.jms..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule businessObjectsDoNotDependOnHigherLayers = noClasses()
            .that().resideInAPackage(ROOT + "bo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + "pres..", ROOT + "facade..", ROOT + "messaging..", ROOT + "config..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnHigherLayers = noClasses()
            .that().resideInAPackage(ROOT + "dao..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + "pres..", ROOT + "facade..", ROOT + "bo..", ROOT + "messaging..", ROOT + "config..");

    @ArchTest
    static final ArchRule springTransactionClassesBelongToFacades = noClasses()
            .that().resideOutsideOfPackage(ROOT + "facade..")
            .should(beAnnotatedWith(Transactional.class).or(beMetaAnnotatedWith(Transactional.class)));

    @ArchTest
    static final ArchRule springTransactionMethodsBelongToFacades = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage(ROOT + "facade..")
            .should(beAnnotatedWith(Transactional.class).or(beMetaAnnotatedWith(Transactional.class)));

    @ArchTest
    static final ArchRule jakartaTransactionClassesBelongToFacades = noClasses()
            .that().resideOutsideOfPackage(ROOT + "facade..")
            .should(beAnnotatedWith(jakarta.transaction.Transactional.class)
                    .or(beMetaAnnotatedWith(jakarta.transaction.Transactional.class)));

    @ArchTest
    static final ArchRule jakartaTransactionMethodsBelongToFacades = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage(ROOT + "facade..")
            .should(beAnnotatedWith(jakarta.transaction.Transactional.class)
                    .or(beMetaAnnotatedWith(jakarta.transaction.Transactional.class)));
}
