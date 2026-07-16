import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private accessToken: string | null = null;
  private api = '/api';

  constructor(private http: HttpClient) {}

  login(email: string, password: string) {
    return this.http.post<{accessToken: string; refreshToken: string; expiresIn: number}>(`${this.api}/auth/login`, { email, password })
      .pipe(tap(res => this.accessToken = res.accessToken));
  }

  getToken(): string | null { return this.accessToken; }
  isLogged(): boolean { return this.accessToken !== null; }
  logout(): void { this.accessToken = null; }
}
