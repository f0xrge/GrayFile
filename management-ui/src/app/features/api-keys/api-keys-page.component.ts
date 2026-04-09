import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ApiKey, Customer } from '../../core/models/management.models';
import { ManagementApiService } from '../../core/services/management-api.service';

@Component({
  selector: 'gf-api-keys-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule
  ],
  templateUrl: './api-keys-page.component.html',
  styleUrl: './api-keys-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApiKeysPageComponent {
  private readonly api = inject(ManagementApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly apiKeys = signal<ApiKey[]>([]);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly selectedApiKey = computed(
    () => this.apiKeys().find((apiKey) => apiKey.id === this.selectedId()) ?? null
  );

  protected readonly auditForm = this.fb.nonNullable.group({
    actorId: ['management-user'],
    changeReason: ['ui-update'],
    secondApproverId: ['']
  });

  protected readonly apiKeyForm = this.fb.nonNullable.group({
    id: ['', Validators.required],
    customerId: ['', Validators.required],
    name: ['', Validators.required],
    active: [true]
  });

  constructor() {
    this.loadData();
  }

  protected selectApiKey(apiKey: ApiKey): void {
    this.selectedId.set(apiKey.id);
    this.apiKeyForm.setValue({
      id: apiKey.id,
      customerId: apiKey.customerId,
      name: apiKey.name,
      active: apiKey.active
    });
    this.apiKeyForm.controls.id.disable();
    this.apiKeyForm.controls.customerId.disable();
  }

  protected resetForm(): void {
    this.selectedId.set(null);
    this.apiKeyForm.reset({ id: '', customerId: '', name: '', active: true });
    this.apiKeyForm.controls.id.enable();
    this.apiKeyForm.controls.customerId.enable();
  }

  protected saveApiKey(): void {
    if (this.apiKeyForm.invalid) {
      this.apiKeyForm.markAllAsTouched();
      return;
    }

    const audit = this.auditForm.getRawValue();
    const raw = this.apiKeyForm.getRawValue();
    const selected = this.selectedApiKey();

    const request$ = selected
      ? this.api.updateApiKey(
          selected.id,
          {
            name: raw.name,
            active: raw.active,
            changeType: 'update'
          },
          audit
        )
      : this.api.createApiKey(
          {
            id: raw.id,
            customerId: raw.customerId,
            name: raw.name,
            active: raw.active,
            changeType: 'create'
          },
          audit
        );

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadApiKeys();
      this.resetForm();
    });
  }

  protected deactivateApiKey(): void {
    const selected = this.selectedApiKey();
    if (!selected) {
      return;
    }

    this.api
      .deactivateApiKey(selected.id, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.loadApiKeys();
        this.resetForm();
      });
  }

  protected customerName(customerId: string): string {
    return this.customers().find((customer) => customer.id === customerId)?.name ?? customerId;
  }

  private loadData(): void {
    this.api
      .listCustomers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((customers) => {
        this.customers.set(customers);
      });

    this.loadApiKeys();
  }

  private loadApiKeys(): void {
    this.api
      .listApiKeys()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((apiKeys) => {
        this.apiKeys.set(apiKeys);
      });
  }
}
