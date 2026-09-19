import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink],
  template: `
    <main class="error-page" tabindex="-1">
      <h1>Forbidden</h1>
      <p>Your operator role does not allow this capability.</p>
      <a routerLink="/dashboard">Back to dashboard</a>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenComponent {}
