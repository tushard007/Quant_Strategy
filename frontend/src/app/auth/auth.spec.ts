import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { vi } from 'vitest';

describe('JWT authentication', () => {
  let auth: AuthService;
  let http: HttpClient;
  let requests: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    ] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { auth.logout(); requests.verify(); vi.useRealTimers(); });

  function login() {
    auth.login('user@example.com', 'test-password').subscribe();
    const request = requests.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ accessToken: 'jwt-token', tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { username: 'user@example.com', roles: ['USER'] } });
  }

  it('attaches JWT only to same-origin API calls', () => {
    login();
    http.get('/api/momentum-backtest/executions').subscribe();
    const api = requests.expectOne('/api/momentum-backtest/executions');
    expect(api.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    api.flush([]);
    for (const url of ['https://external.example/api/data', '/assets/icon.svg']) {
      http.get(url).subscribe();
      const external = requests.expectOne(url);
      expect(external.request.headers.has('Authorization')).toBe(false);
      external.flush({});
    }
  });

  it('attaches JWT to same-origin actuator calls', () => {
    login();
    http.get('/actuator/metrics').subscribe();
    const actuator = requests.expectOne('/actuator/metrics');
    expect(actuator.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    actuator.flush({ names: [] });
  });

  it('clears the session on 401 but preserves it on 403', () => {
    login();
    http.get('/api/stock-master').subscribe({ error: () => {} });
    requests.expectOne('/api/stock-master').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.message()).toContain('does not have access');
    http.get('/api/auth/me').subscribe({ error: () => {} });
    requests.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });

  it('silently renews the session before expiry and logout clears credentials', () => {
    vi.useFakeTimers();
    login();
    expect(auth.isAuthenticated()).toBe(true);
    vi.advanceTimersByTime(840_000);
    const refresh = requests.expectOne('/api/auth/refresh');
    expect(refresh.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    refresh.flush({ accessToken: 'renewed-jwt-token', tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { username: 'user@example.com', roles: ['USER'] } });
    expect(auth.accessToken()).toBe('renewed-jwt-token');
    auth.logout();
    expect(auth.accessToken()).toBeNull();
  });

  it('logs out once when token renewal fails', () => {
    vi.useFakeTimers();
    login();
    vi.advanceTimersByTime(840_000);
    requests.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.message()).toContain('expired');
  });
});
