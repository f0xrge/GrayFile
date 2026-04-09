import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ManagementApiService } from '../../core/services/management-api.service';

@Component({
  selector: 'gf-overview-page',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  templateUrl: './overview-page.component.html',
  styleUrl: './overview-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OverviewPageComponent {
  private readonly api = inject(ManagementApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly metrics = signal([
    { label: 'Clients', value: '0', icon: 'domain' },
    { label: 'API keys', value: '0', icon: 'vpn_key' },
    { label: 'Modeles', value: '0', icon: 'deployed_code' }
  ]);

  constructor() {
    forkJoin({
      customers: this.api.listCustomers(),
      apiKeys: this.api.listApiKeys(),
      models: this.api.listModels()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ customers, apiKeys, models }) => {
        this.metrics.set([
          { label: 'Clients', value: String(customers.length), icon: 'domain' },
          { label: 'API keys', value: String(apiKeys.length), icon: 'vpn_key' },
          { label: 'Modeles', value: String(models.length), icon: 'deployed_code' }
        ]);
      });
  }
}
