import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <h2>Create an Account</h2>
        <p class="subtitle">Register to reserve and schedule meeting rooms</p>

        @if (errorMessage()) {
          <div class="alert error">{{ errorMessage() }}</div>
        }

        <form (ngSubmit)="onRegister()">
          <div class="form-group">
            <label for="username">Username (3–15 chars, lowercase)</label>
            <input
              id="username"
              type="text"
              name="username"
              [(ngModel)]="username"
              required
              placeholder="e.g. charlie-99"
              autocomplete="username"
            />
          </div>

          <div class="form-group">
            <label for="profileName">Display Name</label>
            <input
              id="profileName"
              type="text"
              name="profileName"
              [(ngModel)]="profileName"
              required
              placeholder="e.g. Charlie Brown"
            />
          </div>

          <div class="form-group">
            <label for="password">Password (min 8 characters)</label>
            <input
              id="password"
              type="password"
              name="password"
              [(ngModel)]="password"
              required
              placeholder="At least 8 characters"
              autocomplete="new-password"
            />
          </div>

          <button type="submit" [disabled]="loading()" class="btn-primary">
            {{ loading() ? 'Creating Account...' : 'Register' }}
          </button>
        </form>

        <p class="switch-link">
          Already have an account? <a routerLink="/login">Log in here</a>
        </p>
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
      max-width: 420px;
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
    input:focus { outline: none; border-color: #2563eb; }
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
    }
    .btn-primary:hover:not(:disabled) { background: #1d4ed8; }
    .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
    .alert { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; font-size: 0.875rem; }
    .alert.error { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .switch-link { text-align: center; margin-top: 1.25rem; font-size: 0.875rem; color: #64748b; }
    .switch-link a { color: #2563eb; text-decoration: none; font-weight: 500; }
  `],
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  username = '';
  profileName = '';
  password = '';
  loading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  onRegister(): void {
    if (!this.username.trim() || !this.profileName.trim() || !this.password) {
      this.errorMessage.set('Please fill in all fields');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService
      .register({
        username: this.username,
        profileName: this.profileName,
        password: this.password,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loading.set(false);
          this.errorMessage.set(err?.error?.message || 'Registration failed');
        },
      });
  }
}
