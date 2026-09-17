// k6 load test for the order API (POST /api/order, GET /api/order/{orderNumber}).
//
// Prerequisites: the app + Keycloak must be running (docker compose up -d, or
// docker compose up -d --build for the fully containerized stack).
//
// Run with:
//   k6 run tooling/load-testing/order-api.js
//
// Override defaults via environment variables, e.g. against the plain docker-compose stack
// (app on 9080, Keycloak on 8081) vs. a different host/port:
//   k6 run -e BASE_URL=http://localhost:9080 -e KEYCLOAK_URL=http://localhost:8081 \
//       tooling/load-testing/order-api.js
//
// Results (requests/sec, latency percentiles, error rate) print to stdout; pair this with the
// Grafana dashboard (http://localhost:3000) to watch request rate/latency/circuit-breaker metrics
// live while the test runs.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9080';
const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8081';
const REALM = __ENV.REALM || 'ecommerce';
const CLIENT_ID = __ENV.CLIENT_ID || 'ecommerce-app';
const USERNAME = __ENV.USERNAME || 'order-admin';
const PASSWORD = __ENV.PASSWORD || 'password';

const placeOrderTrend = new Trend('place_order_duration', true);
const findOrderTrend = new Trend('find_order_duration', true);

export const options = {
    scenarios: {
        order_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 }, // ramp up
                { duration: '2m', target: 10 }, // sustained load
                { duration: '30s', target: 0 }, // ramp down
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        place_order_duration: ['p(95)<1000'],
        find_order_duration: ['p(95)<500'],
    },
};

/**
 * Fetches a fresh JWT via the Resource Owner Password Credentials grant (fine for a local demo
 * client with directAccessGrantsEnabled=true - never use ROPC against a real production IdP).
 */
function fetchToken() {

    const tokenUrl = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
    const response = http.post(
        tokenUrl,
        {
            grant_type: 'password',
            client_id: CLIENT_ID,
            username: USERNAME,
            password: PASSWORD,
        },
        { tags: { name: 'KeycloakToken' } },
    );
    check(response, { 'obtained access token': (r) => r.status === 200 && r.json('access_token') });
    return response.json('access_token');
}

export function setup() {

    return { token: fetchToken() };
}

export default function (data) {

    const headers = {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.token}`,
    };

    const placeResponse = http.post(
        `${BASE_URL}/home/api/order`,
        JSON.stringify({ remarks: `k6 load test order ${Date.now()}` }),
        { headers, tags: { name: 'PlaceOrder' } },
    );
    placeOrderTrend.add(placeResponse.timings.duration);
    const placed = check(placeResponse, { 'order placed (201)': (r) => r.status === 201 });

    if (placed) {

        const orderNumber = placeResponse.body.replace(/"/g, '');
        const findResponse = http.get(`${BASE_URL}/home/api/order/${orderNumber}`, {
            headers,
            tags: { name: 'FindOrder' },
        });
        findOrderTrend.add(findResponse.timings.duration);
        check(findResponse, { 'order found (200)': (r) => r.status === 200 });
    }

    sleep(1);
}
