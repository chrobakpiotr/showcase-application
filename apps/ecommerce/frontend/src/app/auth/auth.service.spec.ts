import type { Mock } from "vitest";
import { DOCUMENT } from "@angular/common";
import { TestBed } from "@angular/core/testing";
import type Keycloak from "keycloak-js";

import { AuthService } from "@app/auth/auth.service";
import { KEYCLOAK } from "@app/auth/keycloak.instance";

interface MutableKeycloak {
  authenticated?: boolean;
  token?: string;
  tokenParsed?: {
    preferred_username?: string;
  };
  realmAccess?: {
    roles: string[];
  };
  init: Mock;
  login: Mock;
  logout: Mock;
  updateToken: Mock;
  clearToken: Mock;
  onAuthSuccess?: () => void;
  onAuthRefreshSuccess?: () => void;
  onAuthLogout?: () => void;
  onTokenExpired?: () => void;
}

describe("AuthService", () => {
  beforeEach(() => {
    vi.useFakeTimers({ advanceTimeDelta: 1, shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });
  let keycloak: MutableKeycloak;
  let service: AuthService;

  function setup(baseUri = "http://localhost:9080/home/"): void {
    keycloak = {
      authenticated: false,
      init: vi.fn().mockName("init").mockResolvedValue(false),
      login: vi.fn().mockName("login").mockResolvedValue(undefined),
      logout: vi.fn().mockName("logout").mockResolvedValue(undefined),
      updateToken: vi.fn().mockName("updateToken").mockResolvedValue(false),
      clearToken: vi.fn().mockName("clearToken"),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: KEYCLOAK, useValue: keycloak as unknown as Keycloak },
        {
          provide: DOCUMENT,
          useValue: { baseURI: baseUri },
        },
      ],
    });

    service = TestBed.inject(AuthService);
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it("initializes standard Authorization Code flow with PKCE S256", async () => {
    setup();

    await service.initialize();

    expect(keycloak.init).toHaveBeenCalledWith({
      onLoad: "check-sso",
      flow: "standard",
      pkceMethod: "S256",
      checkLoginIframe: false,
    });
    expect(service.initialized()).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
  });

  it("maps authenticated Keycloak state to username and realm roles", async () => {
    setup();
    authenticate("order-viewer", ["ORDER_READ"]);
    keycloak.init.mockResolvedValue(true);

    await service.initialize();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe("order-viewer");
    expect(service.roles()).toEqual(["ORDER_READ"]);
  });

  it("keeps the application unauthenticated when callback initialization fails", async () => {
    setup();
    keycloak.init.mockImplementation(() => {
      throw new Error("invalid callback state");
    });

    await service.initialize();

    expect(service.initialized()).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBe("");
    expect(service.roles()).toEqual([]);
  });

  it("synchronizes state on Keycloak authentication success", () => {
    setup();
    authenticate("order-admin", ["ORDER_READ", "ORDER_WRITE"]);

    keycloak.onAuthSuccess?.();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe("order-admin");
    expect(service.roles()).toEqual(["ORDER_READ", "ORDER_WRITE"]);
  });

  it("synchronizes state on successful Keycloak token refresh", () => {
    setup();
    authenticate("order-viewer", ["ORDER_READ"]);

    keycloak.onAuthRefreshSuccess?.();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe("order-viewer");
    expect(service.roles()).toEqual(["ORDER_READ"]);
  });

  it("clears state on Keycloak logout callback", () => {
    setup();
    authenticate("order-admin", ["ORDER_READ"]);
    keycloak.onAuthSuccess?.();
    expect(service.isAuthenticated()).toBe(true);

    keycloak.onAuthLogout?.();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBe("");
    expect(service.roles()).toEqual([]);
  });

  it("clears state when an auth-success callback has no authenticated session", () => {
    setup();

    keycloak.onAuthSuccess?.();

    expect(service.isAuthenticated()).toBe(false);
  });

  it("clears state when an authenticated callback has no token", () => {
    setup();
    keycloak.authenticated = true;

    keycloak.onAuthRefreshSuccess?.();

    expect(service.isAuthenticated()).toBe(false);
  });

  it("uses empty optional identity fields when claims are absent", () => {
    setup();
    keycloak.authenticated = true;
    keycloak.token = "access-token";

    keycloak.onAuthSuccess?.();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe("");
    expect(service.roles()).toEqual([]);
  });

  it("returns to the requested internal route after login", async () => {
    setup();

    await service.login("/orders?status=CONFIRMED");

    expect(keycloak.login).toHaveBeenCalledWith({
      redirectUri: "http://localhost:9080/home/orders?status=CONFIRMED",
    });
  });

  it("uses dashboard when login is called without a returnUrl", async () => {
    setup();

    await service.login();

    expect(keycloak.login).toHaveBeenCalledWith({
      redirectUri: "http://localhost:9080/home/dashboard",
    });
  });

  for (const unsafeReturnUrl of [
    "https://attacker.example/steal",
    "//attacker.example/steal",
    "/\\attacker.example",
  ]) {
    it(`rejects unsafe returnUrl: ${unsafeReturnUrl}`, async () => {
      setup();

      await service.login(unsafeReturnUrl);

      expect(keycloak.login).toHaveBeenCalledWith({
        redirectUri: "http://localhost:9080/home/dashboard",
      });
    });
  }

  it("rejects a route that escapes the application base path", async () => {
    setup();

    await service.login("/../outside");

    expect(keycloak.login).toHaveBeenCalledWith({
      redirectUri: "http://localhost:9080/home/dashboard",
    });
  });

  it("handles a document base URI without a trailing slash", async () => {
    setup("http://localhost:9080/home");

    await service.login("/orders");

    expect(keycloak.login).toHaveBeenCalledWith({
      redirectUri: "http://localhost:9080/dashboard",
    });
  });

  it("returns null without refresh when Keycloak is unauthenticated", async () => {
    setup();

    const token = await service.getValidAccessToken();

    expect(token).toBeNull();
    expect(keycloak.updateToken).not.toHaveBeenCalled();
  });

  it("returns null without refresh when an authenticated session has no token", async () => {
    setup();
    keycloak.authenticated = true;

    const token = await service.getValidAccessToken();

    expect(token).toBeNull();
    expect(keycloak.updateToken).not.toHaveBeenCalled();
  });

  it("refreshes before returning a token for a protected API request", async () => {
    setup();
    authenticate("order-admin", ["ORDER_READ", "ORDER_WRITE"]);

    const token = await service.getValidAccessToken();

    expect(keycloak.updateToken).toHaveBeenCalledWith(30);
    expect(token).toBe("access-token");
    expect(service.roles()).toContain("ORDER_WRITE");
  });

  it("returns null if the adapter no longer has a token after refresh", async () => {
    setup();
    authenticate("order-admin", ["ORDER_READ"]);
    keycloak.updateToken.mockImplementation(() => {
      keycloak.token = undefined;
      return Promise.resolve(true);
    });

    const token = await service.getValidAccessToken();

    expect(token).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it("clears state and returns null when refresh fails", async () => {
    setup();
    authenticate("order-admin", ["ORDER_READ"]);
    keycloak.updateToken.mockImplementation(() => {
      throw new Error("refresh failed");
    });

    const token = await service.getValidAccessToken();

    expect(token).toBeNull();
    expect(keycloak.clearToken).toHaveBeenCalled();
    expect(service.isAuthenticated()).toBe(false);
  });

  it("uses OIDC logout and returns to the application login page", async () => {
    setup();
    authenticate("order-admin", ["ORDER_READ"]);

    await service.logout();

    expect(keycloak.logout).toHaveBeenCalledWith({
      redirectUri: "http://localhost:9080/home/login",
    });
    expect(service.isAuthenticated()).toBe(false);
  });

  it("refreshes an expired token through the Keycloak callback", async () => {
    setup();
    authenticate("order-admin", ["ORDER_READ"]);

    keycloak.onTokenExpired?.();
    await vi.advanceTimersByTimeAsync(0);

    expect(keycloak.updateToken).toHaveBeenCalledWith(0);
    expect(service.isAuthenticated()).toBe(true);
  });

  it("clears the session if automatic expired-token refresh fails", async () => {
    setup();
    authenticate("order-admin", ["ORDER_READ"]);
    keycloak.updateToken.mockImplementation(() => {
      throw new Error("expired refresh failed");
    });

    keycloak.onTokenExpired?.();
    await vi.advanceTimersByTimeAsync(0);

    expect(keycloak.clearToken).toHaveBeenCalled();
    expect(service.isAuthenticated()).toBe(false);
  });

  function authenticate(username: string, roles: string[]): void {
    keycloak.authenticated = true;
    keycloak.token = "access-token";
    keycloak.tokenParsed = { preferred_username: username };
    keycloak.realmAccess = { roles };
  }
});
