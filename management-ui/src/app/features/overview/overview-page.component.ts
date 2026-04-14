import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  Customer,
  LlmModel,
  UsageAnalyticsBreakdown,
  UsageAnalyticsResponse
} from '../../core/models/management.models';
import { ManagementApiService } from '../../core/services/management-api.service';

@Component({
  selector: 'gf-overview-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule
  ],
  templateUrl: './overview-page.component.html',
  styleUrl: './overview-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OverviewPageComponent {
  private readonly api = inject(ManagementApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly customers = signal<Customer[]>([]);
  protected readonly models = signal<LlmModel[]>([]);
  protected readonly apiKeysCount = signal(0);
  protected readonly analytics = signal<UsageAnalyticsResponse | null>(null);
  protected readonly metrics = signal([
    { label: 'Clients', value: '0', icon: 'domain' },
    { label: 'API keys', value: '0', icon: 'vpn_key' },
    { label: 'Modeles', value: '0', icon: 'deployed_code' },
    { label: 'Cout total', value: '0 €', icon: 'euro' }
  ]);

  protected readonly filtersForm = this.fb.nonNullable.group({
    customerId: [''],
    modelId: [''],
    startDate: [''],
    endDate: ['']
  });

  constructor() {
    this.loadReferenceData();
    this.refreshAnalytics();
  }

  protected refreshAnalytics(): void {
    const raw = this.filtersForm.getRawValue();
    this.api
      .getUsageAnalytics({
        customerId: raw.customerId || undefined,
        modelId: raw.modelId || undefined,
        startDate: this.toIso(raw.startDate),
        endDate: this.toIso(raw.endDate),
        limit: 12
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((analytics) => {
        this.analytics.set(analytics);
        this.metrics.set([
          { label: 'Clients', value: String(this.customers().length), icon: 'domain' },
          { label: 'API keys', value: String(this.apiKeysCount()), icon: 'vpn_key' },
          { label: 'Modeles', value: String(this.models().length), icon: 'deployed_code' },
          { label: 'Cout total', value: this.formatCurrency(analytics.summary.totalCost), icon: 'euro' }
        ]);
      });
  }

  protected percentage(values: UsageAnalyticsBreakdown[], current: number, key: 'totalTokens' | 'durationMs' | 'totalCost'): number {
    const total = values.reduce((sum, item) => sum + Number(item[key] ?? 0), 0);
    if (total <= 0) {
      return 0;
    }
    return Math.max(6, Math.round((current / total) * 100));
  }

  protected formatCurrency(value: number | null | undefined): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      maximumFractionDigits: 2
    }).format(value ?? 0);
  }

  protected formatDuration(durationMs: number | null | undefined): string {
    const totalSeconds = Math.floor((durationMs ?? 0) / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}m ${seconds.toString().padStart(2, '0')}s`;
  }

  protected formatNumber(value: number | null | undefined): string {
    return new Intl.NumberFormat('fr-FR').format(value ?? 0);
  }

  protected comboLabel(row: UsageAnalyticsBreakdown): string {
    return `${row.customerId ?? 'n/a'} x ${row.modelId ?? 'n/a'}`;
  }

  private loadReferenceData(): void {
    forkJoin({
      customers: this.api.listCustomers(),
      apiKeys: this.api.listApiKeys(),
      models: this.api.listModels()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ customers, apiKeys, models }) => {
        this.customers.set(customers);
        this.apiKeysCount.set(apiKeys.length);
        this.models.set(models);
        const analytics = this.analytics();
        this.metrics.set([
          { label: 'Clients', value: String(customers.length), icon: 'domain' },
          { label: 'API keys', value: String(apiKeys.length), icon: 'vpn_key' },
          { label: 'Modeles', value: String(models.length), icon: 'deployed_code' },
          { label: 'Cout total', value: analytics ? this.formatCurrency(analytics.summary.totalCost) : '0 €', icon: 'euro' }
        ]);
      });
  }

  private toIso(value: string): string | undefined {
    if (!value) {
      return undefined;
    }
    return new Date(value).toISOString();
  }
}
