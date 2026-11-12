import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of, map } from 'rxjs';
import { LoginRequest, RegisterRequest, User } from '../models/app.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);

  currentUser = signal<User | null>(null);
  isLoading = signal<boolean>(true);

  constructor() {
    this.checkSession();
  }

  checkSession(): void {
    this.http.get<User>('/api/auth/me').subscribe({
      next: (user) => {
        this.currentUser.set(user);
        this.isLoading.set(false);
      },
      error: () => {
        this.currentUser.set(null);
        this.isLoading.set(false);
      },
    });
  }

  ensureAuthenticated(): Observable<boolean> {
    if (this.currentUser()) {
      return of(true);
    }
    return this.http.get<User>('/api/auth/me').pipe(
      tap((user) => {
        this.currentUser.set(user);
        this.isLoading.set(false);
      }),
      map(() => true),
      catchError(() => {
        this.currentUser.set(null);
        this.isLoading.set(false);
        return of(false);
      })
    );
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<User>('/api/auth/login', request).pipe(
      tap((user) => this.currentUser.set(user))
    );
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>('/api/auth/register', request).pipe(
      tap((user) => this.currentUser.set(user))
    );
  }

  logout(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/auth/logout', {}).pipe(
      tap(() => this.currentUser.set(null))
    );
  }

  initCsrf(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>('/api/auth/csrf');
  }
}
