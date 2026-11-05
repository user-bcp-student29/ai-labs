// auth.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginUser {
  username: string;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  user: LoginUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private session: LoginResponse | null = null;

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/auth/login', credentials)
      .pipe(
        tap((response) => {
          // Mantiene en memoria solo los campos que consume esta interfaz.
          // No persiste el token en el almacenamiento del navegador.
          this.session = {
            token: response.token,
            expiresIn: response.expiresIn,
            user: { username: response.user.username },
          };
        }),
      );
  }

  get accessToken(): string | null {
    return this.session?.token ?? null;
  }

  get currentUser(): LoginUser | null {
    return this.session?.user ?? null;
  }
}