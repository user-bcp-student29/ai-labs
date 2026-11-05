// auth-login.component.ts
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  AuthLoginRequest,
  AuthLoginResponse,
  AuthLoginService,
} from './auth-login.service';

@Component({
  selector: 'app-auth-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth-login.component.html',
  styleUrl: './auth-login.component.css',
})
export class AuthLoginComponent {
  @Output() readonly loginSucceeded =
    new EventEmitter<AuthLoginResponse>();

  username = '';
  password = '';
  submitting = false;
  errorMessage = '';
  successMessage = '';

  constructor(private readonly authLoginService: AuthLoginService) {}

  submit(form: NgForm): void {
    if (this.submitting) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    const credentials: AuthLoginRequest = {
      username: this.username,
      password: this.password,
    };

    this.submitting = true;

    this.authLoginService
      .login(credentials)
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: (response) => {
          this.password = '';
          this.successMessage =
            `Inicio de sesión correcto para ${response.user.username}.`;
          this.loginSucceeded.emit(response);
        },
        error: (error: unknown) => {
          this.errorMessage = this.getErrorMessage(error);
        },
      });
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'Usuario o contraseña incorrectos.';
      }

      if (error.status === 0) {
        return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
      }
    }

    return 'No se pudo iniciar sesión. Inténtalo de nuevo.';
  }
}