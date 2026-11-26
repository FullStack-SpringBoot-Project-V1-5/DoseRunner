package tn.rnu.eniso.doserunner.custody;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Pyramid rules (pres → facade → bo → dao), copied from the identity-service template.
 */
@AnalyzeClasses(packagesOf = CustodyServiceApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class PyramidArchitectureTest {

    @ArchTest
    static final ArchRule presDoesNotTouchDao = noClasses().that().resideInAPackage("..pres..")
            .should().dependOnClassesThat().resideInAPackage("..dao..")
            .because("controllers talk to facades; entities never leave the facade");

    @ArchTest
    static final ArchRule boIsFreeOfWebAndPersistence = noClasses().that().resideInAPackage("..bo..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.persistence..",
                    "jakarta.servlet..")
            .because("business rules are plain Java, testable without a server or a database");

    @ArchTest
    static final ArchRule lowerLayersDoNotCallUpwards = noClasses().that().resideInAnyPackage("..bo..", "..dao..")
            .should().dependOnClassesThat().resideInAnyPackage("..facade..", "..pres..");

    @ArchTest
    static final ArchRule transactionalClassesOnlyInFacade = classes().that().areAnnotatedWith(Transactional.class)
            .should().resideInAPackage("..facade..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactionalMethodsOnlyInFacade = methods().that().areAnnotatedWith(Transactional.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..facade..");
}
