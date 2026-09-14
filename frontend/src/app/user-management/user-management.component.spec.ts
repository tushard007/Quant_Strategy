import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserManagementComponent, UserAccount } from './user-management.component';
import { AuthService } from '../auth/auth.service';

const account: UserAccount = {
  id: 12, email: 'member@example.com', role: 'USER', enabled: true,
  createdAt: '2026-09-14T00:00:00Z', version: 2,
};

describe('User management', () => {
  let requests: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [UserManagementComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(),
    ] });
    requests = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });
  afterEach(() => { requests.verify(); auth.logout(); });

  function signIn(role: 'SUPERADMIN' | 'ADMIN' | 'USER' = 'SUPERADMIN') {
    auth.login('admin@example.com', 'test-password').subscribe();
    requests.expectOne('/api/auth/login').flush({ accessToken: 'token', tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { username: 'admin@example.com', roles: [role] } });
  }

  it('creates normalized email accounts and defaults to User access', () => {
    signIn();
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    requests.expectOne('/api/admin/users').flush([]);
    const component = fixture.componentInstance;
    component.email = ' New@Example.COM ';
    component.password = 'new-user-password';
    component.save();
    const create = requests.expectOne('/api/admin/users');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ email: 'new@example.com', password: 'new-user-password', role: 'USER' });
    create.flush({ ...account, email: 'new@example.com' });
    requests.expectOne('/api/admin/users').flush([account]);
    expect(component.password).toBe('');
  });

  it('updates access with the current row version and leaves an empty password unchanged', () => {
    signIn();
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.edit(account);
    component.role = 'ADMIN';
    component.enabled = false;
    component.save();
    const update = requests.expectOne('/api/admin/users/12');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ role: 'ADMIN', enabled: false, password: null, version: 2 });
    update.flush({ ...account, role: 'ADMIN', enabled: false, version: 3 });
    requests.expectOne('/api/admin/users').flush([]);
  });

  it('signs out after changing the current admin account', () => {
    signIn();
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.edit({ ...account, email: 'admin@example.com', role: 'ADMIN' });
    component.password = 'changed-password';
    component.save();
    requests.expectOne('/api/admin/users/12').flush({ ...account, email: 'admin@example.com', role: 'ADMIN', version: 3 });
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('prevents admins from managing administrators or assigning elevated roles', () => {
    signIn('ADMIN');
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.edit({ ...account, role: 'SUPERADMIN' });
    expect(component.editing()).toBeNull();
    component.email = 'new@example.com';
    component.password = 'new-user-password';
    component.role = 'SUPERADMIN';
    component.save();
    requests.expectNone('/api/admin/users');
    expect(component.canManage({ ...account, role: 'ADMIN' })).toBe(false);
    expect(component.canManage(account)).toBe(true);
  });

  it('shows conflicts and prevents users from issuing account-management requests', () => {
    signIn();
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.edit(account);
    component.save();
    requests.expectOne('/api/admin/users/12').flush({ message: 'Refresh the account list.' }, { status: 409, statusText: 'Conflict' });
    expect(component.error()).toBe('Refresh the account list.');
    auth.logout();
    signIn('USER');
    component.refresh();
    component.save();
    requests.expectNone('/api/admin/users');
    requests.expectNone('/api/admin/users/12');
  });
});
