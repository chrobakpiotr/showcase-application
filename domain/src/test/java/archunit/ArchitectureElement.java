package archunit;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import static com.tngtech.archunit.base.DescribedPredicate.greaterThanOrEqualTo;
import static com.tngtech.archunit.lang.conditions.ArchConditions.containNumberOfElements;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Abstract class representing atomic architecture element.
 */
public class ArchitectureElement {

    public static final String MAIN_PACKAGE_IDENTIFIER = "com.cp.ecommerce..";

    public static final String DOMAIN_PACKAGE_IDENTIFIER = "com.cp.ecommerce.domain..";

    public static final String APPLICATION_PACKAGE_IDENTIFIER = "com.cp.ecommerce.application..";

    public static final String PACKAGE_SEPARATOR = ".";

    final transient String basePackage;

    public ArchitectureElement(final String basePackage) {

        this.basePackage = basePackage;
    }

    static void denyDependency(final JavaClasses classes) {

        noClasses().that()
                .resideInAPackage(DOMAIN_PACKAGE_IDENTIFIER)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(APPLICATION_PACKAGE_IDENTIFIER)
                .check(classes);
    }

    static void denyAnyDependency(final List<String> fromPackages, final List<String> toPackages, final JavaClasses classes) {

        for (String fromPackage : fromPackages) {
            for (String toPackage : toPackages) {
                noClasses().that()
                        .resideInAPackage(matchAllClassesInPackage(fromPackage))
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(matchAllClassesInPackage(toPackage))
                        .check(classes);
            }
        }
    }

    static String matchAllClassesInPackage(final String packageName) {

        return packageName + "..";
    }

    String fullQualifiedPackage(final String relativePackage) {

        return this.basePackage + PACKAGE_SEPARATOR + relativePackage;
    }

    EvaluationResult evaluateEmptyPackage(final String packageName) {

        // allowEmptyShould(true): without it, ArchUnit throws instead of gracefully evaluating whenever the imported
        // package literally contains zero classes (e.g. a bounded context with no persistence adapter at all) - which
        // would otherwise abort evaluateEmptyPackagesViolation's loop over ALL adapter packages after only the first
        // empty one, never reaching a later adapter package that IS populated (see ADR 0020 / the "assistant" bounded
        // context, whose only adapter is a web controller with no persistence counterpart).
        return classes().that()
                .resideInAPackage(matchAllClassesInPackage(packageName))
                .should(containNumberOfElements(greaterThanOrEqualTo(1)))
                .allowEmptyShould(true)
                .evaluate(classesInPackage(packageName));
    }

    void denyEmptyPackage(final String packageName) {

        classes().that()
                .resideInAPackage(matchAllClassesInPackage(packageName))
                .should(containNumberOfElements(greaterThanOrEqualTo(1)))
                .allowEmptyShould(true)
                .check(classesInPackage(packageName));
    }

    private JavaClasses classesInPackage(final String packageName) {

        return new ClassFileImporter().importPackages(packageName);
    }

    void denyEmptyPackages(final List<String> packages) {

        final boolean hasViolations = evaluateEmptyPackagesViolation(packages);
        if (hasViolations) {

            for (String packageName : packages) {

                denyEmptyPackage(packageName);
            }
        }
    }

    boolean evaluateEmptyPackagesViolation(final List<String> packages) {

        for (String packageName : packages) {

            if (!evaluateEmptyPackage(packageName).hasViolation()) {

                return false;
            }
        }
        return true;
    }

}
