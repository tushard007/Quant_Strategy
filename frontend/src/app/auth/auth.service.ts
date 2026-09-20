import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, tap } from 'rxjs';

export interface CurrentUser {
  username: string;
  roles: Array<'SUPERADMIN' | 'ADMIN' | 'USER'>;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: CurrentUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly RENEWAL_LEAD_TIME_MS = 60_000;
  private readonly http = inject(HttpClient);
  private readonly session = signal<LoginResponse | null>(null);
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private refreshSubscription?: Subscription;
  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.user() !== null);
  readonly isSuperadmin = computed(() => this.user()?.roles.includes('SUPERADMIN') ?? false);
  readonly isAdmin = computed(() => this.isSuperadmin() || (this.user()?.roles.includes('ADMIN') ?? false));
  readonly roleLabel = computed(() => this.isSuperadmin() ? 'Superadmin' : this.isAdmin() ? 'Admin' : 'User');
  readonly message = signal<string | null>(null);

  // Keep bearer credentials in memory rather than persistent browser storage.
  login(username: string, password: string) {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(
      tap(session => {
        this.logout();
        this.startSession(session);
      }),
    );
  }

  accessToken(): string | null {
    const session = this.session();
    if (session && Date.parse(session.expiresAt) <= Date.now()) {
      this.logout('Your session expired. Sign in again to continue.');
      return null;
    }
    return session?.accessToken ?? null;
  }

  logout(message: string | null = null): void {
    clearTimeout(this.expiryTimer);
    this.refreshSubscription?.unsubscribe();
    this.refreshSubscription = undefined;
    this.session.set(null);
    this.message.set(message);
  }

  private startSession(session: LoginResponse): void {
    this.session.set(session);
    this.scheduleRefresh(session);
  }

  private scheduleRefresh(session: LoginResponse): void {
    clearTimeout(this.expiryTimer);
    const delay = Math.max(0, Date.parse(session.expiresAt) - Date.now() - AuthService.RENEWAL_LEAD_TIME_MS);
    this.expiryTimer = setTimeout(() => this.refresh(session.accessToken), delay);
  }

  private refresh(token: string): void {
    this.refreshSubscription = this.http.post<LoginResponse>('/api/auth/refresh', {}).subscribe({
      next: session => {
        if (this.session()?.accessToken === token) this.startSession(session);
      },
      error: () => {
        if (this.session()?.accessToken === token) {
          this.logout('Your session expired. Sign in again to continue.');
        }
      },
    });
  }
}
