package com.personal.finance.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the layered architecture by class naming convention: controllers
 * only depend on services, services only on repositories and common code, and
 * repositories never depend back on services or controllers.
 */
class LayeredArchitectureTest {

    private static final JavaClasses classes =
            new ClassFileImporter().importPackages("com.personal.finance");

    @Test
    void controllersMustNotDependOnRepositories() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
        rule.check(classes);
    }

    @Test
    void repositoriesMustNotDependOnServicesOrControllers() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Service")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Controller");
        rule.check(classes);
    }
}
