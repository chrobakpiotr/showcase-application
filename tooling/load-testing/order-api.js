// Run against a disposable demo stack with an operator bearer token in ACCESS_TOKEN.
// Setup receives ITERATIONS units of stock. Each iteration reserves one unit.
// See docs/testing.md for roles, token lifetime and cleanup requirements.

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:9080').replace(/\/+$/, '');
const SKU = __ENV.SKU || 'DEMO-MOUSE-001';
const ITERATIONS = positiveInteger('ITERATIONS', 20);
const VUS = positiveInteger('VUS', 2);

if (VUS > ITERATIONS) {
    throw new Error('VUS must not exceed ITERATIONS');
}

const placeOrderTrend = new Trend('place_order_duration', true);
const findOrderTrend = new Trend('find_order_duration', true);

export const options = {
    scenarios: {
        order_traffic: {
            executor: 'shared-iterations',
            vus: VUS,
            iterations: ITERATIONS,
            maxDuration: '2m',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        iterations: [`count==${ITERATIONS}`],
        http_req_failed: ['rate<0.01'],
        place_order_duration: ['p(95)<1000'],
        find_order_duration: ['p(95)<500'],
    },
};

function positiveInteger(name, fallback) {
    const value = Number(__ENV[name] || fallback);
    if (!Number.isInteger(value) || value < 1 || value > 10000) {
        throw new Error(`${name} must be an integer between 1 and 10000`);
    }
    return value;
}

export function setup() {
    const token = (__ENV.ACCESS_TOKEN || '').trim();
    if (!token || /\s/.test(token)) {
        exec.test.abort('ACCESS_TOKEN must contain an operator bearer token');
    }
    const parameters = { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, timeout: '10s' };
    const productResponse = http.get(`${BASE_URL}/home/api/catalog/products/${encodeURIComponent(SKU)}`, parameters);
    if (!check(productResponse, { 'catalog product available': response => response.status === 200 })) {
        exec.test.abort(`Catalog lookup failed with HTTP ${productResponse.status}`);
    }
    const product = productResponse.json();
    if (!product.active || typeof product.name !== 'string' || !Number.isFinite(product.unitPrice) || product.unitPrice < 0) {
        exec.test.abort('Catalog product must be active and have a valid name and unit price');
    }
    const stockResponse = http.post(`${BASE_URL}/home/api/inventory/${encodeURIComponent(SKU)}/receive`,
        JSON.stringify({ quantity: ITERATIONS }), parameters);
    if (!check(stockResponse, { 'load-test stock prepared': response => response.status === 200 })) {
        exec.test.abort(`Stock preparation failed with HTTP ${stockResponse.status}`);
    }
    return { token, product };
}

export default function (data) {
    const parameters = {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
        timeout: '10s',
        tags: { name: 'PlaceOrder' },
    };
    const order = {
        remarks: `Load test VU ${__VU} iteration ${__ITER}`,
        created: new Date().toISOString(),
        customer: {
            fullName: 'Load Test', email: 'load-test@example.com', phone: '+48 555 123 456',
            street: 'Test Street 1', postalCode: '00-001', city: 'Warsaw', countryCode: 'PL',
        },
        paymentMethod: 'CARD',
        items: [{ sku: SKU, productName: data.product.name, unitPrice: data.product.unitPrice, quantity: 1 }],
    };
    const placeResponse = http.post(`${BASE_URL}/home/api/order`, JSON.stringify(order), parameters);
    placeOrderTrend.add(placeResponse.timings.duration);
    if (!check(placeResponse, { 'order placed (201)': response => response.status === 201 })) {
        exec.test.abort(`Order placement failed with HTTP ${placeResponse.status}`);
    }
    let placement;
    try {
        placement = placeResponse.json();
    } catch {
        exec.test.abort('Order placement returned invalid JSON');
    }
    if (!check(placement, { 'placement has orderNumber': value => typeof value?.orderNumber === 'string' && value.orderNumber.trim().length > 0 })) {
        exec.test.abort('Order placement returned an invalid orderNumber');
    }
    const findResponse = http.get(`${BASE_URL}/home/api/order/${encodeURIComponent(placement.orderNumber)}`,
        { ...parameters, tags: { name: 'FindOrder' } });
    findOrderTrend.add(findResponse.timings.duration);
    if (!check(findResponse, { 'order found (200)': response => response.status === 200 })) {
        exec.test.abort(`Order lookup failed with HTTP ${findResponse.status}`);
    }
    sleep(1);
}
