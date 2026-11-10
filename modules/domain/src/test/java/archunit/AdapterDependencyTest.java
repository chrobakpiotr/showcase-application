package archunit;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;

import fixtures.architecture.adapter.ai.AiFixture;
import fixtures.architecture.adapter.camel.CamelFixture;
import fixtures.architecture.adapter.common.CommonFixture;
import fixtures.architecture.adapter.kafka.KafkaFixture;
import fixtures.architecture.adapter.persistence.PersistenceFixture;
import fixtures.architecture.adapter.security.OtherSecurityFixture;
import fixtures.architecture.adapter.security.authentication.CurrentOperatorProvider;
import fixtures.architecture.adapter.web.AllowedWebFixture;
import fixtures.architecture.adapter.web.ForbiddenWebSecurityFixture;
import fixtures.architecture.adapter.web.WebFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdapterDependencyTest {

    @Test
    void shouldRejectWebDependingOnPersistence() {

        final var classes = new ClassFileImporter().importClasses(WebFixture.class, PersistenceFixture.class);
        final Adapter adapter = new HexagonalArchitecture("fixtures.architecture").withAdapters();

        assertThatThrownBy(() -> adapter.dontDependOnEachOther(classes)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("WebFixture")
                .hasMessageContaining("PersistenceFixture");
    }

    @Test
    void shouldRejectAiDependingOnKafka() {

        final var classes = new ClassFileImporter().importClasses(AiFixture.class, KafkaFixture.class);
        final Adapter adapter = new HexagonalArchitecture("fixtures.architecture").withAdapters();

        assertThatThrownBy(() -> adapter.dontDependOnEachOther(classes)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("AiFixture")
                .hasMessageContaining("KafkaFixture");
    }

    @Test
    void shouldRejectCamelDependingOnKafka() {

        final var classes = new ClassFileImporter().importClasses(CamelFixture.class, KafkaFixture.class);
        final Adapter adapter = new HexagonalArchitecture("fixtures.architecture").withAdapters();

        assertThatThrownBy(() -> adapter.dontDependOnEachOther(classes)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("CamelFixture");
    }

    @Test
    void shouldAllowSharedUtilitiesAndWebOperatorIdentity() {

        final var classes = new ClassFileImporter()
                .importClasses(AllowedWebFixture.class, CurrentOperatorProvider.class, CommonFixture.class);
        final Adapter adapter = new HexagonalArchitecture("fixtures.architecture").withAdapters();

        assertThatCode(() -> adapter.dontDependOnEachOther(classes)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOtherWebSecurityDependencies() {

        final var classes = new ClassFileImporter()
                .importClasses(ForbiddenWebSecurityFixture.class, OtherSecurityFixture.class);
        final Adapter adapter = new HexagonalArchitecture("fixtures.architecture").withAdapters();

        assertThatThrownBy(() -> adapter.dontDependOnEachOther(classes)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("OtherSecurityFixture");
    }
}
