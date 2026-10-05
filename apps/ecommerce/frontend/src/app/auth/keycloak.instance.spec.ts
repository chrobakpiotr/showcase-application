import { TestBed } from '@angular/core/testing';

import { KEYCLOAK } from '@app/auth/keycloak.instance';

describe('KEYCLOAK', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('creates the official Keycloak JavaScript adapter', () => {
    TestBed.configureTestingModule({});

    const keycloak = TestBed.inject(KEYCLOAK);

    expect(keycloak).toBeTruthy();
    expect(keycloak.init).toEqual(expect.any(Function));
    expect(keycloak.login).toEqual(expect.any(Function));
    expect(keycloak.updateToken).toEqual(expect.any(Function));
  });
});
