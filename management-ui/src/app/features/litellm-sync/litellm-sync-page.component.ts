import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { LiteLlmHealth, LiteLlmReconciliation, LiteLlmResource } from '../../core/models/management.models';
import { ManagementApiService } from '../../core/services/management-api.service';

@Component({
  selector: 'gf-litellm-sync-page',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './litellm-sync-page.component.html',
  styleUrl: './litellm-sync-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LiteLlmSyncPageComponent {
  private readonly api = inject(ManagementApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly health = signal<LiteLlmHealth | null>(null);
  protected readonly resources = signal<LiteLlmResource[]>([]);
  protected readonly reconciliation = signal<LiteLlmReconciliation | null>(null);

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.api
      .getLiteLlmHealth()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((health) => this.health.set(health));

    this.api
      .listLiteLlmResources()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resources) => this.resources.set(resources));
  }

  protected reconcile(): void {
    this.api
      .reconcileLiteLlm({ actorId: 'management-user', changeReason: 'manual-litellm-reconcile' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.reconciliation.set(result);
        this.refresh();
      });
  }
}
