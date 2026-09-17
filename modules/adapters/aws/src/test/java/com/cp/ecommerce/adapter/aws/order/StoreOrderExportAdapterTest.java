package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.google.gson.Gson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link StoreOrderExportAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class StoreOrderExportAdapterTest {

    @Mock
    transient S3Client s3Client;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Mock
    transient Gson gson;

    @Test
    void shouldStoreOrderExportInS3() {

        final Order order = mockOrder();
        final StoreOrderExportAdapter adapter = new StoreOrderExportAdapter(s3Client, "test-bucket", resilientExecutor, gson);
        runResilientActionEagerly();
        given(gson.toJson(any(Object.class))).willReturn("{}");
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        adapter.store(order);

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldUseCorrectBucketAndKey() {

        final Order order = mockOrder();
        final String bucket = "ecommerce-order-exports";
        final StoreOrderExportAdapter adapter = new StoreOrderExportAdapter(s3Client, bucket, resilientExecutor, gson);
        runResilientActionEagerly();
        given(gson.toJson(any(Object.class))).willReturn("{}");
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        adapter.store(order);

        verify(s3Client).putObject((PutObjectRequest) org.mockito.ArgumentMatchers.argThat(req -> {
            final PutObjectRequest r = (PutObjectRequest) req;
            return bucket.equals(r.bucket()) && r.key().startsWith("orders/") && r.key().endsWith(".json");
        }), any(RequestBody.class));
    }

    private void runResilientActionEagerly() {

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

}
