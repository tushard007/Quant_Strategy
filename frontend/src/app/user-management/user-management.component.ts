import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export type AccountRole = 'SUPERADMIN' | 'ADMIN' | 'USER';
export interface UserAccount {
  id: number;
  email: string;
  role: AccountRole;
  enabled: boolean;
  createdAt: string;
  version: number;
}

@Component({
  selector: 'app-user-management',
  imports: [FormsModule, DatePipe],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly accounts = signal<UserAccount[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly editing = signal<UserAccount | null>(null);
  email = '';
  password = '';
  role: AccountRole = 'USER';
  enabled = true;

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    if (!this.auth.isAdmin()) return;
    this.loading.set(true);
    this.http.get<UserAccount[]>('/api/admin/users').pipe(
      finalize(() => this.loading.set(false)),
    ).subscribe({ next: accounts => this.accounts.set(accounts), error: error => this.showError(error) });
  }

  edit(account: UserAccount): void {
    if (this.saving() || !this.canManage(account)) return;
    this.editing.set(account);
    this.email = account.email;
    this.role = account.role;
    this.enabled = account.enabled;
    this.password = '';
    this.error.set(null);
    this.message.set(null);
  }

  reset(): void {
    this.editing.set(null);
    this.email = '';
    this.password = '';
    this.role = 'USER';
    this.enabled = true;
  }

  save(): void {
    if (!this.auth.isAdmin() || this.saving() || !this.email.trim()) return;
    if (!this.auth.isSuperadmin() && (this.role !== 'USER' || (this.editing() && !this.canManage(this.editing()!)))) return;
    if ((!this.editing() || this.password) &&
        (this.password.trim().length === 0 || this.password.length < 12 || new TextEncoder().encode(this.password).length > 72)) {
      this.error.set('Passwords must be at least 12 characters and at most 72 UTF-8 bytes.');
      return;
    }
    const editing = this.editing();
    const operation = editing
      ? this.http.put<UserAccount>(`/api/admin/users/${editing.id}`, {
          role: this.role, enabled: this.enabled, password: this.password || null, version: editing.version,
        })
      : this.http.post<UserAccount>('/api/admin/users', {
          email: this.email.trim().toLowerCase(), password: this.password, role: this.role,
        });
    this.saving.set(true);
    this.error.set(null);
    this.message.set(null);
    operation.pipe(finalize(() => { this.saving.set(false); this.password = ''; })).subscribe({
      next: account => {
        this.reset();
        if (editing && account.email === this.auth.user()?.username && account.version !== editing.version) {
          this.auth.logout('Your account was updated. Sign in again to continue.');
          return;
        }
        this.message.set(editing ? 'Account updated. Previous sessions are invalidated when access or password changes.' : 'Account created.');
        this.refresh();
      },
      error: error => this.showError(error),
    });
  }

  canManage(account: UserAccount): boolean {
    return this.auth.isSuperadmin() || (this.auth.isAdmin() && account.role === 'USER');
  }

  roleLabel(role: AccountRole): string {
    return role === 'SUPERADMIN' ? 'Superadmin' : role === 'ADMIN' ? 'Admin' : 'User';
  }

  private showError(error: HttpErrorResponse): void {
    this.error.set(error.error?.details?.join('. ') || error.error?.message || 'Account operation failed. Please try again.');
  }
}
