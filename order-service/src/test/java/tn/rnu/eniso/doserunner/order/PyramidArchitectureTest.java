package tn.rnu.eniso.doserunner.order;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "tn.rnu.eniso.doserunner.order", importOptions = ImportOption.DoNotIncludeTests.class)
class PyramidArchitectureTest {

    private static final String PRES = "..order.pres..";
    private static final String FACADE = "..order.facade..";
    private static final String BO = "..order.bo..";
    private static final String DAO = "..order.dao..";
    private static final String MESSAGING = "..order.messaging..";
    private static final String SHARED = "..order.shared..";

    @ArchTest
    static final ArchRule pyramid_layers_are_respected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Pres").definedBy(PRES)
            .layer("Facade").definedBy(FACADE)
            .layer("BO").definedBy(BO)
            .layer("DAO").definedBy(DAO)
            .layer("Messaging").definedBy(MESSAGING)
            .whereLayer("Pres").mayNotBeAccessedByAnyLayer()
            .whereLayer("Facade").mayOnlyBeAccessedByLayers("Pres")
            .whereLayer("BO").mayOnlyBeAccessedByLayers("Facade")
            .whereLayer("DAO").mayOnlyBeAccessedByLayers("Facade", "BO")
            .whereLayer("Messaging").mayOnlyBeAccessedByLayers("Facade");

    @ArchTest
    static final ArchRule pres_never_touches_the_dao = noClasses().that().resideInAPackage(PRES)
            .should().dependOnClassesThat().resideInAPackage(DAO);

    @ArchTest
    static final ArchRule only_the_facade_uses_messaging = noClasses()
            .that().resideOutsideOfPackages(FACADE, MESSAGING)
            .should().dependOnClassesThat().resideInAPackage(MESSAGING);

    @ArchTest
    static final ArchRule bo_stays_free_of_web_and_persistence = noClasses().that().resideInAPackage(BO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "jakarta.servlet..", "jakarta.persistence..",
                    "org.springframework.data..", "org.springframework.jms..", "jakarta.jms..");

    @ArchTest
    static final ArchRule only_facade_classes_are_transactional = classes()
            .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .or().areAnnotatedWith(jakarta.transaction.Transactional.class)
            .should().resideInAPackage(FACADE)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule only_facade_methods_are_transactional = methods()
            .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .or().areAnnotatedWith(jakarta.transaction.Transactional.class)
            .should().beDeclaredInClassesThat().resideInAPackage(FACADE);

    @ArchTest
    static final ArchRule shared_depends_on_no_layer = noClasses().that().resideInAPackage(SHARED)
            .should().dependOnClassesThat().resideInAnyPackage(PRES, FACADE, BO, DAO, MESSAGING);

    @ArchTest
    static final ArchRule controllers_live_in_pres = classes()
            .that().areAnnotatedWith(org.springframework.stereotype.Controller.class)
            .or().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage(PRES);
}
