package archunit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Class representing adapter of hexagonal architecture pattern.
 */
public class Adapter extends ArchitectureElement {

    private static final List<String> ADAPTER_PACKAGES = Arrays
            .asList("persistence.", "web.", "mail.", "security.", "amqp.", "aws.");
    /**
     * Special cases list to omit checks for duplicated package names in path, like ex. "security.security"
     */
    private static final List<String> DUPLICATED_PACKAGE_NAMES_SPECIAL_CASES = List.of("security.");
    private final transient HexagonalArchitecture parentContext;
    private final transient List<String> adapterPackages = new ArrayList<>();

    Adapter(final HexagonalArchitecture parentContext, final String basePackage) {

        super(basePackage);
        this.parentContext = parentContext;
    }

    public Adapter domain(final String packageName) {

        ADAPTER_PACKAGES.forEach(adapterPackage -> {

            if (DUPLICATED_PACKAGE_NAMES_SPECIAL_CASES.contains(adapterPackage)) {

                this.adapterPackages.add(fullQualifiedPackage(packageName));
            } else {

                this.adapterPackages.add(fullQualifiedPackage(adapterPackage + packageName));
            }
        });
        return this;
    }

    List<String> allAdapterPackages() {

        return adapterPackages;
    }

    public HexagonalArchitecture and() {

        return parentContext;
    }

    void dontDependOnEachOther(final JavaClasses classes) {

        final List<String> allAdapters = allAdapterPackages();
        for (String adapter1 : allAdapters) {
            for (String adapter2 : allAdapters) {
                if (!adapter1.equals(adapter2)) {
                    denyDependency(classes);
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
