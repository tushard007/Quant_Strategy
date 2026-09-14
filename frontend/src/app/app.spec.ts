import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth/auth.service';

describe('App access', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
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
    expect(element.querySelector('nav')?.textContent).toContain('Sign in');
    expect(element.querySelector('nav')?.textContent).not.toContain('MASTER');
    expect(element.querySelector('nav')?.textContent).not.toContain('ANALYSIS');
  });

  it('shows analysis menus for users and prevents selecting a master screen', () => {
    signIn('USER');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const navigation = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(navigation.textContent).toContain('ANALYSIS');
    expect(navigation.textContent).not.toContain('MASTER');
    expect(navigation.textContent).not.toContain('ADMINISTRATION');
    fixture.componentInstance.navigate('users');
    expect(fixture.componentInstance.activePage()).toBe('dashboard');
    fixture.componentInstance.navigate('stocks');
    expect(fixture.componentInstance.activePage()).toBe('dashboard');
    fixture.componentInstance.navigate('momentum');
    expect(fixture.componentInstance.activePage()).toBe('momentum');
  });

  it('shows every menu and permits master screens for admins', () => {
    signIn('ADMIN');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const navigation = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(navigation.textContent).toContain('MASTER');
    expect(navigation.textContent).toContain('ADMINISTRATION');
    fixture.componentInstance.navigate('users');
    expect(fixture.componentInstance.activePage()).toBe('users');
    expect(navigation.textContent).toContain('ANALYSIS');
    fixture.componentInstance.navigate('stocks');
    expect(fixture.componentInstance.activePage()).toBe('stocks');
  });

  it('gives Superadmin all menus and identifies the elevated role', () => {
    signIn('SUPERADMIN');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const navigation = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(navigation.textContent).toContain('MASTER');
    expect(navigation.textContent).toContain('ANALYSIS');
    expect(navigation.textContent).toContain('ADMINISTRATION');
    expect(navigation.textContent).toContain('Superadmin');
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
});
