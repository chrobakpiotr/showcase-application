package archunit;

import java.util.Optional;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Class representing whole hexagonal architecture pattern for tests.
 */
public class HexagonalArchitecture extends ArchitectureElement {

    private static final String MAIN_PACKAGE = "com.cp.ecommerce";

    private static final String MAIN_DOMAIN_PACKAGE = "domain.";

    private static final String MAIN_ADAPTER_PACKAGE = "adapter";

    private transient Adapter adapters;

    private transient Domain domain;

    public HexagonalArchitecture(final String basePackage) {

        super(basePackage);
    }

    public static HexagonalArchitecture basePackage() {

        return new HexagonalArchitecture(MAIN_PACKAGE);
    }

    public Adapter withAdapters() {

        this.adapters = new Adapter(this, fullQualifiedPackage(MAIN_ADAPTER_PACKAGE));
        return this.adapters;
    }

    public Domain withDomain(final String domainPackage) {

        this.domain = new Domain(fullQualifiedPackage(MAIN_DOMAIN_PACKAGE + domainPackage), this);
        return this.domain;
    }

    public void check(final JavaClasses classes) {

        if (Optional.ofNullable(this.adapters).isPresent()) {
            this.adapters.doesNotContainEmptyPackages();
            this.adapters.dontDependOnEachOther(classes);
            this.adapters.doesNotDependOn(classes);
        }
        this.domain.doesNotContainEmptyPackages();
        this.domain.doesNotDependOn(classes);
        this.domain.incomingAndOutgoingPortsDoNotDependOnEachOther(classes);
    }

}
