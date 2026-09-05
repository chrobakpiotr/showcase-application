import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  get usernameControl() {
    return this.loginForm.controls.username;
  }

  get passwordControl() {
    return this.loginForm.controls.password;
  }

  onSubmit(): void {
    if (this.loginForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    const { username, password } = this.loginForm.getRawValue();
    this.authService.login(username, password).subscribe({
      next: () => {
        this.submitting.set(false);
        const returnUrl =
          this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err: Error) => {
        this.submitting.set(false);
        this.errorMessage.set(err.message);
      },
    });
  }
}
