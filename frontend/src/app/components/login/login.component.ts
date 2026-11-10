import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <h2>Room Reservation Login</h2>
        <p class="subtitle">Log in to view available rooms and reserve slots</p>

        @if (errorMessage()) {
          <div class="alert error">{{ errorMessage() }}</div>
        }

        <form (ngSubmit)="onLogin()">
          <div class="form-group">
            <label for="username">Username</label>
            <input
              id="username"
              type="text"
              name="username"
              [(ngModel)]="username"
              required
              placeholder="e.g. alice"
              autocomplete="username"
            />
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              name="password"
              [(ngModel)]="password"
              required
              placeholder="Enter your password"
              autocomplete="current-password"
            />
          </div>

          <button type="submit" [disabled]="loading()" class="btn-primary">
            {{ loading() ? 'Logging in...' : 'Log In' }}
          </button>
        </form>

        <p class="switch-link">
          Don't have an account? <a routerLink="/register">Register here</a>
        </p>

        <div class="demo-hints">
          <small>Default test accounts: <code>alice</code> or <code>bob</code> / <code>Password123!</code></small>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    }
    .auth-card {
      background: #ffffff;
      padding: 2.5rem;
      border-radius: 12px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
      width: 100%;
      max-width: 400px;
    }
    h2 { margin: 0 0 0.5rem; color: #1e293b; font-size: 1.5rem; }
    .subtitle { color: #64748b; font-size: 0.875rem; margin-bottom: 1.5rem; }
    .form-group { margin-bottom: 1.25rem; }
    label { display: block; font-size: 0.875rem; font-weight: 500; color: #334155; margin-bottom: 0.35rem; }
    input {
      width: 100%;
      padding: 0.65rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.95rem;
      box-sizing: border-box;
    }
    input:focus { outline: none; border-color: #2563eb; ring: 2px rgba(37, 99, 235, 0.2); }
    .btn-primary {
      width: 100%;
      padding: 0.75rem;
      background: #2563eb;
      color: white;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.95rem;
      cursor: pointer;
      transition: background 0.2s;
    }
    .btn-primary:hover:not(:disabled) { background: #1d4ed8; }
    .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
    .alert { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; font-size: 0.875rem; }
    .alert.error { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .switch-link { text-align: center; margin-top: 1.25rem; font-size: 0.875rem; color: #64748b; }
    .switch-link a { color: #2563eb; text-decoration: none; font-weight: 500; }
    .demo-hints { margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid #e2e8f0; color: #64748b; text-align: center; font-size: 0.8rem; }
    code { background: #f1f5f9; padding: 2px 5px; border-radius: 4px; color: #0f172a; }
  `],
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  username = '';
  password = '';
  loading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  onLogin(): void {
    if (!this.username.trim() || !this.password) {
      this.errorMessage.set('Please provide both username and password');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.message || 'Invalid username or password');
      },
    });
  }
}
