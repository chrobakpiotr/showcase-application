package archunit;

import java.util.ArrayList;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Class representing domain of hexagonal architecture pattern.
 */
public class Domain extends ArchitectureElement {

    private static final String USE_CASES_PACKAGE = "usecase";

    private static final String INCOMING_PORTS_PACKAGE = "port.incoming";

    private static final String OUTGOING_PORTS_PACKAGE = "port.outgoing";

    private final transient HexagonalArchitecture parentContext;

    private final transient List<String> incomingPortsPackages = new ArrayList<>();

    private final transient List<String> outgoingPortsPackages = new ArrayList<>();

    private final transient List<String> usecasePackages = new ArrayList<>();

    public Domain(final String basePackage, final HexagonalArchitecture parentContext) {

        super(basePackage);
        this.parentContext = parentContext;
    }

    public Domain incomingPorts() {

        this.incomingPortsPackages.add(fullQualifiedPackage(INCOMING_PORTS_PACKAGE));
        return this;
    }

    public Domain outgoingPorts() {

        this.outgoingPortsPackages.add(fullQualifiedPackage(OUTGOING_PORTS_PACKAGE));
        return this;
    }

    public Domain usecases() {

        this.usecasePackages.add(fullQualifiedPackage(USE_CASES_PACKAGE));
        return this;
    }

    public HexagonalArchitecture and() {

        return parentContext;
    }

    public void doesNotDependOn(final JavaClasses classes) {

        denyDependency(classes);
    }

    public void incomingAndOutgoingPortsDoNotDependOnEachOther(final JavaClasses classes) {

        denyAnyDependency(this.incomingPortsPackages, this.outgoingPortsPackages, classes);
        denyAnyDependency(this.outgoingPortsPackages, this.incomingPortsPackages, classes);
    }

    private List<String> allPackages() {

        final List<String> allPackages = new ArrayList<>();
        allPackages.addAll(incomingPortsPackages);
        allPackages.addAll(outgoingPortsPackages);
        allPackages.addAll(usecasePackages);
        return allPackages;
    }

    void doesNotContainEmptyPackages() {

        denyEmptyPackages(allPackages());
    }

}
