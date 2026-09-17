package com.cp.ecommerce.adapter.web.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.web.order.resource.OpsDigestResource;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.usecase.GetLatestOpsDigestUseCase;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller serving the latest AI-generated ops digest (see ADR 0022), generated eagerly on start-up and refreshed daily by
 * {@code OpsDigestScheduler}.
 *
 * <p>
 * Mapped under {@code /api/order/analytics} alongside {@link OrderAnalyticsController} and
 * {@link OrderAnalyticsAssistantController}: this is a read-only {@code GET}, so it already falls under the existing
 * {@code GET /api/order/**} -> {@code ORDER_READ} security rule with zero security-config changes.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/order/analytics")
@Tag(name = "Order ops digest", description = "Latest AI-generated, plain-English ops digest (see ADR 0022)")
public class OrderOpsDigestController {

    private final GetLatestOpsDigestUseCase getLatestOpsDigestUseCase;

    @GetMapping("/digest")
    @Operation(
            summary = "Get the latest AI-generated ops digest",
            description = "Returns the most recently generated ops digest - a short, plain-English narrative summarizing "
                    + "recent order volume and remarks-triage classification trends. Generated eagerly on application "
                    + "start-up and refreshed daily thereafter.")
    @ApiResponse(
            responseCode = "200",
            description = "Latest ops digest",
            content = @Content(schema = @Schema(implementation = OpsDigestResource.class)))
    @ApiResponse(responseCode = "204", description = "No digest has been generated yet")
    public ResponseEntity<OpsDigestResource> getLatestDigest() {

        final Optional<OpsDigest> latestDigest = getLatestOpsDigestUseCase.getLatestDigest();
        return latestDigest.map(this::toResource).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    private OpsDigestResource toResource(final OpsDigest opsDigest) {

        return OpsDigestResource.builder()
                .generatedDate(opsDigest.getGeneratedDate())
                .ordersPlacedLastDay(opsDigest.getOrdersPlacedLastDay())
                .remarksClassificationCounts(opsDigest.getRemarksClassificationSummary().countsByCategory())
                .narrative(opsDigest.getNarrative())
                .build();
    }

}
