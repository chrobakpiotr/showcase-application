package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.StoreOrderExportOutPort;
import com.google.gson.Gson;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Implementation of {@link StoreOrderExportOutPort} that stores a JSON export of each processed order in an S3 bucket, wrapped
 * behind a circuit-breaker and retry.
 */
@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.aws.s3.enabled", havingValue = "true")
public class StoreOrderExportAdapter implements StoreOrderExportOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "storeOrderExport";

    private static final String S3_KEY_PREFIX = "orders/";

    private static final String S3_KEY_SUFFIX = ".json";

    private final S3Client s3Client;

    private final String bucketName;

    private final ResilientExecutor resilientExecutor;

    private final Gson gson;

    @Override
    public void store(final Order order) {

        final String json = gson.toJson(order);
        final String key = S3_KEY_PREFIX + order.getOrderNumber() + S3_KEY_SUFFIX;
        log.info("Storing order export to S3: bucket={}, key={}", bucketName, key);
        resilientExecutor.runResilient(RESILIENCE_INSTANCE_NAME, () -> {
            final PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(key).build();
            s3Client.putObject(request, RequestBody.fromString(json));
        });
    }

}
