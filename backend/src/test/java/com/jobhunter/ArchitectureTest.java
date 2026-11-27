package com.jobhunter;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Module boundaries of the modular monolith. */
@AnalyzeClasses(packages = "com.jobhunter", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Domain must not depend on web or persistence adapters (the shared kernel's error type is allowed). */
    @ArchTest
    static final ArchRule domainIsIndependent = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat(resideInAPackage("..api..").or(resideInAPackage("..infrastructure.."))
                    .and(resideOutsideOfPackage("com.jobhunter.shared..")))
            .allowEmptyShould(true);

    /** Controllers go through application services, never straight to repositories. */
    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    /** The shared kernel depends on no business module. */
    @ArchTest
    static final ArchRule sharedIsLeaf = noClasses().that().resideInAPackage("com.jobhunter.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("com.jobhunter.auth..", "com.jobhunter.users..",
                    "com.jobhunter.profiles..", "com.jobhunter.jobs..", "com.jobhunter.ai..",
                    "com.jobhunter.resumes..", "com.jobhunter.integrations..", "com.jobhunter.searches..",
                    "com.jobhunter.matching..");

    /** No module reaches into another module's API layer (controllers/DTOs are entry points only). */
    @ArchTest
    static final ArchRule noCrossModuleApiAccess = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat(resideInAPackage("..api..").and(resideOutsideOfPackage("com.jobhunter.shared..")));
}
