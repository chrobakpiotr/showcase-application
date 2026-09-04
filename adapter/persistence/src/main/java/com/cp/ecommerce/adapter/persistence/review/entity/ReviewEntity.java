package com.cp.ecommerce.adapter.persistence.review.entity;

import java.util.Date;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Review} in database.
 *
 * <p>
 * Uses the review id directly as its primary key rather than a surrogate id, mirroring {@code CartEntity} (ADR 0027) - no
 * cross-referencing requirement here would benefit from a numeric surrogate id instead.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "REVIEW")
public class ReviewEntity {

    @Id
    @Column(name = "REVIEW_ID", length = 40, nullable = false)
    private String reviewId;

    @Column(name = "SKU", length = 40, nullable = false)
    private String sku;

    @Column(name = "AUTHOR_NAME", length = 80, nullable = false)
    private String authorName;

    @Column(name = "RATING", nullable = false)
    private int rating;

    @Column(name = "COMMENT_TEXT", length = 2000, nullable = false)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ReviewStatus status;

    @Column(name = "CREATED_DATE", nullable = false)
    private Date createdDate;

}
