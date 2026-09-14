import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth/auth.service';
import { provideRouter, Router } from '@angular/router';
import { routes } from './app.routes';

describe('App access', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });
  afterEach(() => TestBed.inject(AuthService).logout());

  function signIn(role: 'SUPERADMIN' | 'ADMIN' | 'USER') {
    TestBed.inject(AuthService).login(role.toLowerCase() + '@example.com', 'test-password').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({
      accessToken: 'test-token', tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { username: role.toLowerCase() + '@example.com', roles: [role] },
    });
  }

  it('keeps the dashboard public and hides protected menus', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toContain('Momentum dashboard');
    expect(element.querySelector('.account-control')?.textContent).toContain('Sign in');
    expect(element.querySelector('nav')?.textContent).not.toContain('Analyze');
    expect(element.querySelector('nav')?.textContent).not.toContain('Data');
  });

  it('shows analysis menus for users and prevents selecting a master screen', () => {
    signIn('USER');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const navigation = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(navigation.textContent).toContain('Analyze');
    expect(navigation.textContent).toContain('Backtest');
    expect(navigation.textContent).not.toContain('Data');
    expect(navigation.textContent).not.toContain('Administration');
    fixture.componentInstance.navigate('users');
    expect(fixture.componentInstance.activePage()).toBe('market-breadth');
    fixture.componentInstance.navigate('stocks');
    expect(fixture.componentInstance.activePage()).toBe('market-breadth');
    fixture.componentInstance.navigate('momentum');
    expect(fixture.componentInstance.activePage()).toBe('momentum');
  });

  it('shows every menu and permits master screens for admins', () => {
    signIn('ADMIN');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const navigation = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(navigation.textContent).toContain('Data');
    expect(navigation.textContent).not.toContain('Administration');
    fixture.componentInstance.navigate('users');
    expect(fixture.componentInstance.activePage()).toBe('market-breadth');
    expect(navigation.textContent).toContain('Analyze');
    fixture.componentInstance.navigate('stocks');
    expect(fixture.componentInstance.activePage()).toBe('stocks');
  });

  it('gives Superadmin all menus and identifies the elevated role', () => {
    signIn('SUPERADMIN');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const navigation = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(navigation.textContent).toContain('Data');
    expect(navigation.textContent).toContain('Analyze');
    expect(navigation.textContent).toContain('Administration');
    expect((fixture.nativeElement as HTMLElement).querySelector('.account-control')?.textContent).toContain('Superadmin');
    fixture.componentInstance.navigate('users');
    expect(fixture.componentInstance.activePage()).toBe('users');
  });

  it('requires login for analysis and removes access after session expiry', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.navigate('momentum');
    expect(app.activePage()).toBe('login');
    signIn('USER');
    expect(app.activePage()).toBe('momentum');
    TestBed.inject(AuthService).logout('Session expired');
    expect(app.activePage()).toBe('login');
    app.logout();
    expect(app.activePage()).toBe('dashboard');
  });

  it('synchronizes direct URLs, active pages and expanded sections', async () => {
    signIn('USER');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/backtest/breadth');
    fixture.detectChanges();

    expect(fixture.componentInstance.activePage()).toBe('breadth-backtest');
    expect(fixture.componentInstance.expandedSections().backtest).toBe(true);
    expect(router.url).toBe('/backtest/breadth');
  });

  it('redirects a user away from an admin-only direct URL', async () => {
    signIn('USER');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/data/stocks');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.activePage()).toBe('market-breadth');
    expect(router.url).toBe('/overview/market-breadth');
  });
});
