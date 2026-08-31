package com.cp.ecommerce.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link PageQuery}.
 */
class PageQueryTest {

    @Test
    void shouldExposePageAndSize() {

        final PageQuery pageQuery = new PageQuery(2, 50);

        assertThat(pageQuery.page()).isEqualTo(2);
        assertThat(pageQuery.size()).isEqualTo(50);
    }

    @Test
    void shouldRejectNegativePage() {

        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(-1, PageQuery.DEFAULT_SIZE))
                .withMessageContaining("page");
    }

    @Test
    void shouldRejectSizeBelowOne() {

        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(0, 0)).withMessageContaining("size");
    }

    @Test
    void shouldRejectSizeAboveMax() {

        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(0, PageQuery.MAX_SIZE + 1))
                .withMessageContaining("size");
    }

    @Test
    void shouldAcceptSizeAtMax() {

        final PageQuery pageQuery = new PageQuery(0, PageQuery.MAX_SIZE);

        assertThat(pageQuery.size()).isEqualTo(PageQuery.MAX_SIZE);
    }

}
