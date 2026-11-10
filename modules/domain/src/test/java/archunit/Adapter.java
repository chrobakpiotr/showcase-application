package archunit;

import java.util.ArrayList;
import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Class representing adapter of hexagonal architecture pattern.
 */
public class Adapter extends ArchitectureElement {

    private static final List<String> ADAPTER_PACKAGES = List
            .of("persistence", "web", "mail", "security", "amqp", "aws", "kafka", "camel", "ai");
    private final transient HexagonalArchitecture parentContext;
    private final transient List<String> adapterPackages = new ArrayList<>();

    Adapter(final HexagonalArchitecture parentContext, final String basePackage) {

        super(basePackage);
        this.parentContext = parentContext;
    }

    public Adapter domain(final String packageName) {

        ADAPTER_PACKAGES
                .forEach(adapterPackage -> this.adapterPackages.add(fullQualifiedPackage(adapterPackage + "." + packageName)));
        return this;
    }

    List<String> allAdapterPackages() {

        return adapterPackages;
    }

    public HexagonalArchitecture and() {

        return parentContext;
    }

    public void dontDependOnEachOther(final JavaClasses classes) {

        for (String source : ADAPTER_PACKAGES) {
            for (String target : ADAPTER_PACKAGES) {
                if (!source.equals(target)) {
                    final String targetPackage = fullQualifiedPackage(target);
                    final String operatorProvider = fullQualifiedPackage("security.authentication.CurrentOperatorProvider");
                    final DescribedPredicate<JavaClass> forbiddenTarget = DescribedPredicate.describe(
                            "reside in " + targetPackage + " except the web operator-identity boundary",
                            candidate -> (candidate.getPackageName().equals(targetPackage)
                                    || candidate.getPackageName().startsWith(targetPackage + "."))
                                    && !(source.equals("web") && target.equals("security")
                                            && candidate.getName().equals(operatorProvider)));
                    noClasses().that()
                            .resideInAPackage(fullQualifiedPackage(source) + "..")
                            .should()
                            .dependOnClassesThat(forbiddenTarget)
                            .allowEmptyShould(true)
                            .check(classes);
                }
            }
        }
    }

    void doesNotDependOn(final JavaClasses classes) {

        denyDependency(classes);
    }

    void doesNotContainEmptyPackages() {

        denyEmptyPackages(allAdapterPackages());
    }

}
