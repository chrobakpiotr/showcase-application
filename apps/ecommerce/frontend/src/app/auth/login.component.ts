import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  onLogin(): void {
    if (this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const returnUrl =
      this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';

    void this.authService.login(returnUrl).catch(() => {
      this.submitting.set(false);
      this.errorMessage.set(
        'Could not start the Keycloak sign-in flow. Please try again.'
      );
    });
  }
}
