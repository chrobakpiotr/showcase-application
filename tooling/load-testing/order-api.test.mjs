import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const source = await readFile(new URL('./order-api.js', import.meta.url), 'utf8');

async function scenario(environment = {}, respond = () => undefined) {
    const requests = [];
    const context = vm.createContext({
        __ENV: { ACCESS_TOKEN: 'synthetic-token', ITERATIONS: '4', VUS: '2', ...environment },
        __VU: 1,
        __ITER: 0,
    });
    const response = (status, body) => ({
        status,
        body: JSON.stringify(body),
        timings: { duration: 1 },
        json: (key) => key ? body[key] : body,
    });
    function request(method, url, body, parameters) {
        const entry = { method, url, body, parameters };
        requests.push(entry);
        return respond(entry) ?? (url.includes('/catalog/')
            ? response(200, { sku: 'DEMO-MOUSE-001', name: 'Wireless Mouse', unitPrice: 39.9, active: true })
            : response(method === 'POST' && url.endsWith('/order') ? 201 : 200,
                { orderNumber: 'probe-123' }));
    }
    const imports = {
        'k6/http': { default: {
            get: (url, parameters) => request('GET', url, undefined, parameters),
            post: (url, body, parameters) => request('POST', url, body, parameters),
        } },
        'k6': {
            check: (value, checks) => Object.values(checks).every(check => check(value)),
            sleep: () => {},
            fail: (message) => { throw new Error(message); },
        },
        'k6/execution': { default: { test: { abort: (message) => {
            const error = new Error(message);
            error.name = 'TestAbort';
            throw error;
        } } } },
        'k6/metrics': { Trend: class { add() {} } },
    };
    const module = new vm.SourceTextModule(source, { context });
    await module.link((name) => {
        const values = imports[name];
        assert.ok(values, `Unexpected dependency ${name}`);
        return new vm.SyntheticModule(Object.keys(values), function () {
            for (const [key, value] of Object.entries(values)) this.setExport(key, value);
        }, { context });
    });
    await module.evaluate();
    return { api: module.namespace, requests, response };
}

test('requires a bearer token before any HTTP request', async () => {
    const { api, requests } = await scenario({ ACCESS_TOKEN: '' });
    assert.throws(() => api.setup(), /ACCESS_TOKEN/);
    assert.equal(requests.length, 0);
});

test('prepares stock for the bounded run and submits a complete order', async () => {
    const { api, requests } = await scenario();
    const data = api.setup();
    api.default(data);
    const stock = requests.find(request => request.url.endsWith('/receive'));
    assert.equal(JSON.parse(stock.body).quantity, 4);
    const placement = requests.find(request => request.url.endsWith('/api/order'));
    const order = JSON.parse(placement.body);
    assert.equal(order.paymentMethod, 'CARD');
    assert.equal(order.customer.email, 'load-test@example.com');
    assert.equal(order.items[0].sku, 'DEMO-MOUSE-001');
    assert.equal(order.items[0].quantity, 1);
    assert.equal(order.items[0].unitPrice, 39.9);
    assert.equal(placement.parameters.headers.Authorization, 'Bearer synthetic-token');
    assert.equal(api.options.scenarios.order_traffic.executor, 'shared-iterations');
    assert.equal(api.options.scenarios.order_traffic.iterations, 4);
});

test('reads orderNumber from the JSON response and encodes the lookup path', async () => {
    const { api, requests } = await scenario({}, request => request.url.endsWith('/api/order')
        ? { status: 201, body: '{"orderNumber":"order/123"}', json: () => ({ orderNumber: 'order/123' }), timings: { duration: 1 } }
        : undefined);
    api.default(api.setup());
    assert.equal(requests.at(-1).url, 'http://localhost:9080/home/api/order/order%2F123');
});

test('stops when placement fails instead of issuing a lookup', async () => {
    const { api, requests } = await scenario({}, request => request.url.endsWith('/api/order')
        ? { status: 409, body: 'private error', timings: { duration: 1 } } : undefined);
    assert.throws(() => api.default(api.setup()), /409/);
    assert.ok(!requests.some(request => request.url.includes('/api/order/')));
});

test('rejects missing orderNumber without exposing the response body', async () => {
    const { api } = await scenario({}, request => request.url.endsWith('/api/order')
        ? { status: 201, body: '{"private":"payload"}', json: () => ({ private: 'payload' }), timings: { duration: 1 } }
        : undefined);
    assert.throws(() => api.default(api.setup()), error => /orderNumber/.test(error.message) && !/payload/.test(error.message));
});

test('stops setup after expired token without changing inventory', async () => {
    const { api, requests } = await scenario({}, () => ({ status: 401, body: 'private error' }));
    assert.throws(() => api.setup(), /401/);
    assert.equal(requests.length, 1);
});

for (const value of ['0', '-1', '1.5', 'NaN', '10001']) {
    test(`rejects invalid iteration count ${value}`, async () => {
        await assert.rejects(scenario({ ITERATIONS: value }), /ITERATIONS/);
    });
}

test('stops setup when stock preparation is rejected', async () => {
    const { api } = await scenario({}, request => request.url.endsWith('/receive')
        ? { status: 403, body: 'private error' } : undefined);
    assert.throws(() => api.setup(), /403/);
});

test('rejects inactive products before writing inventory', async () => {
    const { api, requests } = await scenario({}, request => request.url.includes('/catalog/')
        ? { status: 200, json: () => ({ active: false }) } : undefined);
    assert.throws(() => api.setup(), /active/);
    assert.equal(requests.length, 1);
});

test('reports malformed placement JSON without echoing it', async () => {
    const { api } = await scenario({}, request => request.url.endsWith('/api/order')
        ? { status: 201, timings: { duration: 1 }, json: () => { throw new Error('private response'); } }
        : undefined);
    assert.throws(() => api.default(api.setup()), error => error.name === 'TestAbort' && /invalid JSON/.test(error.message) && !/private/.test(error.message));
});

test('reports a rejected lookup', async () => {
    const { api } = await scenario({}, request => request.url.includes('/api/order/')
        ? { status: 404, timings: { duration: 1 } } : undefined);
    assert.throws(() => api.default(api.setup()), /404/);
});

test('requires VUS not to exceed the iteration count', async () => {
    await assert.rejects(scenario({ VUS: '5', ITERATIONS: '4' }), /VUS/);
});
