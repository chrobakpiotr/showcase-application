package com.cp.ecommerce.adapter.web.common;

import java.util.List;
import java.util.function.IntFunction;

import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;

/**
 * Centralizes HAL pagination assembly for web adapters.
 *
 * <p>
 * Controllers remain responsible for endpoint-specific link generation, while this class owns the repeated
 * first/previous/self/next/last relation policy and page metadata construction.
 */
public final class PagedModelAssembler {

    private PagedModelAssembler() {
    }

    public static Page page(final int number, final int size, final long totalElements, final int totalPages) {

        return new Page(number, size, totalElements, totalPages);
    }

    public static <T> PagedModel<T> assemble(final List<T> content, final Page page, final IntFunction<Link> linkForPage) {

        final PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                page.size(),
                page.number(),
                page.totalElements(),
                page.totalPages());
        final PagedModel<T> model = PagedModel.of(content, metadata, linkForPage.apply(page.number()).withSelfRel());
        final int lastPage = Math.max(page.totalPages() - 1, 0);

        model.add(linkForPage.apply(0).withRel(IanaLinkRelations.FIRST));
        if (page.number() > 0) {
            model.add(linkForPage.apply(page.number() - 1).withRel(IanaLinkRelations.PREV));
        }
        if (page.number() < lastPage) {
            model.add(linkForPage.apply(page.number() + 1).withRel(IanaLinkRelations.NEXT));
        }
        model.add(linkForPage.apply(lastPage).withRel(IanaLinkRelations.LAST));

        return model;
    }

    public record Page(int number, int size, long totalElements, int totalPages) {
    }
}
