import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';

export interface AuthUser {
  id: number;
  nombre: string;
  email: string;
  rol: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = '/api';
  private currentUser: AuthUser | null = null;

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<AuthUser> {
    return this.http
      .post<AuthUser & { mensaje: string }>(`${this.api}/auth/login`, { email, password })
      .pipe(tap(user => (this.currentUser = user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.api}/auth/logout`, {}).pipe(
      tap(() => (this.currentUser = null))
    );
  }

  me(): Observable<AuthUser | null> {
    return this.http.get<AuthUser>(`${this.api}/auth/me`).pipe(
      tap(user => (this.currentUser = user)),
      catchError(() => {
        this.currentUser = null;
        return of(null);
      })
    );
  }

  getUser(): AuthUser | null {
    return this.currentUser;
  }

  isLogged(): boolean {
    return this.currentUser !== null;
  }
}
