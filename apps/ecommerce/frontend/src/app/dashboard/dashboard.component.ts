import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { CAPABILITIES, Capability } from '@app/auth/capabilities';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
})
export class DashboardComponent {
  readonly authService = inject(AuthService);

  readonly cards = CAPABILITIES;

  isVisible(card: Capability): boolean {
    const role = card.readRole ?? card.writeRole;
    return !role || this.authService.roles().includes(role);
  }
}
