import { DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subject, catchError, of, startWith, switchMap, tap } from "rxjs";

import { AuthService } from "@app/auth/auth.service";
import {
  ParkedDispatch,
  ParkedDispatchesService,
} from "@app/parked-dispatches/parked-dispatches.service";

const REASON_LABELS: Record<ParkedDispatch["reasonCode"], string> = {
  ORDER_MISSING: "Order was not found",
  ATTEMPT_BUDGET_EXHAUSTED: "Delivery attempt limit reached",
  OTHER: "Other",
};

@Component({
  selector: "app-parked-dispatches",
  templateUrl: "./parked-dispatches.component.html",
  styleUrls: ["./parked-dispatches.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
})
export class ParkedDispatchesComponent implements OnInit {
  private readonly service = inject(ParkedDispatchesService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh = new Subject<void>();
  private readonly pageSize = 20;

  readonly dispatches = signal<ParkedDispatch[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly oldestAgeSeconds = signal<number | null>(null);

  get canRead(): boolean {
    return this.authService.roles().includes("ORDER_READ");
  }

  ngOnInit(): void {
    if (!this.canRead) return;
    this.refresh
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(() =>
          this.service.listParkedDispatches(this.page(), this.pageSize).pipe(
            catchError(() => {
              this.errorMessage.set("Failed to load parked dispatches.");
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result === null) return;
        this.dispatches.set(result.content ?? []);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.oldestAgeSeconds.set(result.oldestAgeSeconds);
      });
  }

  reasonLabel(code: ParkedDispatch["reasonCode"]): string {
    return REASON_LABELS[code] ?? REASON_LABELS.OTHER;
  }

  formatAge(seconds: number | null): string {
    if (seconds === null || !Number.isFinite(seconds) || seconds < 0) {
      return "Unknown";
    }
    if (seconds < 60)
      return `${seconds} ${seconds === 1 ? "second" : "seconds"}`;
    if (seconds < 3600) {
      const minutes = Math.floor(seconds / 60);
      return `${minutes} ${minutes === 1 ? "minute" : "minutes"}`;
    }
    if (seconds < 86400) {
      const hours = Math.floor(seconds / 3600);
      return `${hours} ${hours === 1 ? "hour" : "hours"}`;
    }
    const days = Math.floor(seconds / 86400);
    return `${days} ${days === 1 ? "day" : "days"}`;
  }

  ageOf(createdAt: string): string {
    const createdAtMillis = Date.parse(createdAt);
    if (!Number.isFinite(createdAtMillis)) return "Unknown";
    return this.formatAge(
      Math.max(0, Math.floor((Date.now() - createdAtMillis) / 1000)),
    );
  }

  retry(): void {
    this.refresh.next();
  }

  previousPage(): void {
    if (this.page() === 0 || this.loading()) return;
    this.page.update((value) => value - 1);
    this.retry();
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages() || this.loading()) return;
    this.page.update((value) => value + 1);
    this.retry();
  }
}
