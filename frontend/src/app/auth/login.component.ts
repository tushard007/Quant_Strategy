import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <section class="login-panel" aria-labelledby="login-title">
      <p class="eyebrow">ACCOUNT ACCESS</p>
      <h1 id="login-title">Sign in</h1>
      <p>Sign in to use Analysis. Administrators also have access to Master screens.</p>
      <form (ngSubmit)="submit()" #form="ngForm">
        <label for="username">Email address</label>
        <input id="username" name="username" type="email" autocomplete="username" required email
          maxlength="254" [(ngModel)]="username" />
        <label for="password">Password</label>
        <input id="password" name="password" type="password" autocomplete="current-password"
          required maxlength="72" [(ngModel)]="password" />
        @if (error()) { <p class="login-error" role="alert">{{ error() }}</p> }
        <button type="submit" [disabled]="form.invalid || loading()">
          {{ loading() ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
      <button class="dashboard-link" type="button" (click)="cancelled.emit()">Back to Momentum dashboard</button>
    </section>
  `,
  styles: `
    :host { display: block; padding: 2rem 1rem; }
    .login-panel { max-width: 440px; margin: 3rem auto; padding: 2rem; background: white;
      border: 1px solid #dce4df; border-radius: 16px; color: #183b30; }
    .eyebrow { font-size: .75rem; letter-spacing: .1em; }
    h1 { font-size: 2rem; margin: .5rem 0; }
    p { line-height: 1.5; }
    form { display: grid; gap: .75rem; margin-top: 1.5rem; }
    input { padding: .8rem; border: 1px solid #9cafa5; border-radius: 6px; font: inherit; min-width: 0; }
    button { padding: .85rem; border: 0; border-radius: 6px; background: #176348; color: white;
      font: inherit; cursor: pointer; }
    button:disabled { opacity: .6; cursor: wait; }
    .dashboard-link { margin-top: 1rem; background: transparent; color: #176348; padding-left: 0; }
    .login-error { color: #a42121; margin: 0; }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  readonly signedIn = output<void>();
  readonly cancelled = output<void>();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  username = '';
  password = '';

  submit(): void {
    if (this.loading() || !this.username.trim() || !this.password) return;
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).pipe(
      finalize(() => { this.loading.set(false); this.password = ''; }),
    ).subscribe({
      next: () => this.signedIn.emit(),
      error: error => this.error.set(error.status === 401
        ? 'Invalid email or password.' : 'Sign-in is unavailable. Please try again.'),
    });
  }
}
