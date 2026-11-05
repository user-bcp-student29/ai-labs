// auth-login.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AuthLoginRequest {
  username: string;
  password: string;
}

export interface AuthLoginResponse {
  user: {
    username: string;
  };
  token: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthLoginService {
  constructor(private readonly http: HttpClient) {}

  login(credentials: AuthLoginRequest): Observable<AuthLoginResponse> {
    return this.http.post<AuthLoginResponse>('/auth/login', credentials);
  }
}