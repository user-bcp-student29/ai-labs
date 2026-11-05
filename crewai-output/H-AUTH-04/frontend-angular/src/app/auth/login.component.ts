// login.component.ts
import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  ViewChild,
  inject,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  @ViewChild('usernameInput')
  private usernameInput?: ElementRef<HTMLInputElement>;

  @ViewChild('passwordInput')
  private passwordInput?: ElementRef<HTMLInputElement>;

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.pattern(/\S/)]],
    password: ['', Validators.required],
  });

  submitting = false;
  loginSucceeded = false;
  signedInAs = '';
  errorMessage: string | null = null;

  get usernameControl() {
    return this.form.controls.username;
  }

  get passwordControl() {
    return this.form.controls.password;
  }

  get usernameInvalid(): boolean {
    return (
      this.usernameControl.invalid &&
      (this.usernameControl.touched || this.usernameControl.dirty)
    );
  }

  get passwordInvalid(): boolean {
    return (
      this.passwordControl.invalid &&
      (this.passwordControl.touched || this.passwordControl.dirty)
    );
  }

  submit(): void {
    if (this.submitting || this.loginSucceeded) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusFirstInvalidField();
      return;
    }

    this.errorMessage = null;
    this.submitting = true;

    this.authService
      .login(this.form.getRawValue())
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: (response) => {
          this.signedInAs = response.user.username;
          this.loginSucceeded = true;
          this.form.reset();
        },
        error: (error: unknown) => {
          this.errorMessage = this.messageFor(error);
        },
      });
  }

  private focusFirstInvalidField(): void {
    if (this.usernameControl.invalid) {
      this.usernameInput?.nativeElement.focus();
      return;
    }

    if (this.passwordControl.invalid) {
      this.passwordInput?.nativeElement.focus();
    }
  }

  private messageFor(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'No se pudo completar el inicio de sesión. Inténtalo de nuevo.';
    }

    switch (error.status) {
      case 0:
        return 'No se pudo conectar con el servicio. Comprueba tu conexión e inténtalo de nuevo.';
      case 400:
        return 'La solicitud no pudo validarse. Revisa los datos e inténtalo de nuevo.';
      case 401:
        return 'Usuario o contraseña incorrectos.';
      case 503:
        return 'El servicio de autenticación no está disponible temporalmente. Inténtalo más tarde.';
      default:
        return 'No se pudo completar el inicio de sesión. Inténtalo de nuevo.';
    }
  }
}