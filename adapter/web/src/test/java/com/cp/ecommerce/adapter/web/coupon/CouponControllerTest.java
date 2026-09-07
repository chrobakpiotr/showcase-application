package com.cp.ecommerce.adapter.web.coupon;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CouponBuilder;
import com.cp.ecommerce.adapter.web.coupon.mapper.CouponWebMapper;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponDetailsResource;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponResource;
import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;
import com.cp.ecommerce.domain.coupon.port.incoming.CreateCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.GetCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.ListCouponsInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.ManageCouponInPort;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.CouponBuilder.TEST_COUPON_CODE;

/**
 * Test class checking coupon controller's behavior and API responses.
 */
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    private static final String COUPON_ENDPOINT = "/api/coupons";

    private static final String CODE_JSON_PATH = "$.code";

    @Autowired
    private transient MockMvc mockMvc;

    @Autowired
    private transient ObjectMapper objectMapper;

    @MockitoBean
    private transient CreateCouponInPort createCouponInPort;

    @MockitoBean
    private transient ManageCouponInPort manageCouponInPort;

    @MockitoBean
    private transient GetCouponInPort getCouponInPort;

    @MockitoBean
    private transient ListCouponsInPort listCouponsInPort;

    @MockitoBean
    private transient CouponWebMapper couponWebMapper;

    @Test
    void shouldListCoupons() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(listCouponsInPort.listCoupons(new CouponPageQuery(0, CouponPageQuery.DEFAULT_SIZE, null)))
                .willReturn(new PagedCoupons(List.of(coupon), 0, CouponPageQuery.DEFAULT_SIZE, 1, 1));
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(get(COUPON_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.couponDetailsResourceList[0].code").value(TEST_COUPON_CODE))
                .andExpect(header().string("Content-Type", containsString("application/hal+json")));
    }

    @Test
    void shouldListCouponsWithPrevAndNextLinksForMiddlePage() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(listCouponsInPort.listCoupons(new CouponPageQuery(1, 5, true)))
                .willReturn(new PagedCoupons(List.of(coupon), 1, 5, 11, 3));
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(get(COUPON_ENDPOINT).param("page", "1").param("size", "5").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.prev.href", endsWith("/api/coupons?page=0&size=5&activeOnly=true")))
                .andExpect(jsonPath("$._links.next.href", endsWith("/api/coupons?page=2&size=5&activeOnly=true")));
    }

    @Test
    void shouldRejectInvalidPageRequest() throws Exception {

        mockMvc.perform(get(COUPON_ENDPOINT).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetCoupon() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(getCouponInPort.getCoupon(TEST_COUPON_CODE)).willReturn(coupon);
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(get(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CODE_JSON_PATH).value(TEST_COUPON_CODE));
    }

    @Test
    void shouldReturnNotFoundWhenCouponDoesNotExist() throws Exception {

        mockMvc.perform(get(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateCoupon() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        final CouponResource resource = mockCouponResource();
        given(couponWebMapper.mapToDomainObject(any(CouponResource.class))).willReturn(Optional.of(coupon));
        given(createCouponInPort.createCoupon(coupon)).willReturn(coupon);
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(
                post(COUPON_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(CODE_JSON_PATH).value(TEST_COUPON_CODE));
    }

    @Test
    void shouldRejectCreateCouponWhenPayloadMissing() throws Exception {

        given(couponWebMapper.mapToDomainObject(any(CouponResource.class))).willReturn(Optional.empty());

        mockMvc.perform(post(COUPON_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateCoupon() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(couponWebMapper.mapToDomainObject(any(CouponResource.class))).willReturn(Optional.of(coupon));
        given(manageCouponInPort.updateCoupon(TEST_COUPON_CODE, coupon)).willReturn(coupon);
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(
                put(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockCouponResource())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CODE_JSON_PATH).value(TEST_COUPON_CODE));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingCoupon() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(couponWebMapper.mapToDomainObject(any(CouponResource.class))).willReturn(Optional.of(coupon));

        mockMvc.perform(
                put(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockCouponResource())))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectUpdateCouponWhenPayloadMissing() throws Exception {

        given(couponWebMapper.mapToDomainObject(any(CouponResource.class))).willReturn(Optional.empty());

        mockMvc.perform(put(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldActivateCoupon() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(manageCouponInPort.activateCoupon(TEST_COUPON_CODE)).willReturn(coupon);
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(post(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CODE_JSON_PATH).value(TEST_COUPON_CODE));
    }

    @Test
    void shouldReturnNotFoundWhenActivatingMissingCoupon() throws Exception {

        mockMvc.perform(post(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE + "/activate")).andExpect(status().isNotFound());
    }

    @Test
    void shouldDeactivateCoupon() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(manageCouponInPort.deactivateCoupon(TEST_COUPON_CODE)).willReturn(coupon);
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.of(mockCouponDetailsResource()));

        mockMvc.perform(post(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CODE_JSON_PATH).value(TEST_COUPON_CODE));
    }

    @Test
    void shouldReturnNotFoundWhenDeactivatingMissingCoupon() throws Exception {

        mockMvc.perform(post(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE + "/deactivate")).andExpect(status().isNotFound());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMappingToResourceReturnsEmpty() throws Exception {

        final Coupon coupon = CouponBuilder.mockCoupon();
        given(getCouponInPort.getCoupon(TEST_COUPON_CODE)).willReturn(coupon);
        given(couponWebMapper.mapToResource(coupon)).willReturn(Optional.empty());

        mockMvc.perform(get(COUPON_ENDPOINT + "/" + TEST_COUPON_CODE)).andExpect(status().isInternalServerError());
    }

    private CouponResource mockCouponResource() {

        final Coupon coupon = CouponBuilder.mockCoupon();
        return CouponResource.builder()
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minimumOrderAmount(coupon.getMinimumOrderAmount())
                .maxRedemptions(coupon.getMaxRedemptions())
                .expiresAt(coupon.getExpiresAt())
                .active(coupon.isActive())
                .build();
    }

    private CouponDetailsResource mockCouponDetailsResource() {

        final Coupon coupon = CouponBuilder.mockCoupon();
        return CouponDetailsResource.builder()
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minimumOrderAmount(coupon.getMinimumOrderAmount())
                .maxRedemptions(coupon.getMaxRedemptions())
                .redemptionCount(coupon.getRedemptionCount())
                .expiresAt(coupon.getExpiresAt())
                .active(coupon.isActive())
                .build();
    }

}
