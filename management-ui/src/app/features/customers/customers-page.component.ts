import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Customer } from '../../core/models/management.models';
import { ManagementApiService } from '../../core/services/management-api.service';

@Component({
  selector: 'gf-customers-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule
  ],
  templateUrl: './customers-page.component.html',
  styleUrl: './customers-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CustomersPageComponent {
  private readonly api = inject(ManagementApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly customers = signal<Customer[]>([]);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly selectedCustomer = computed(
    () => this.customers().find((customer) => customer.id === this.selectedId()) ?? null
  );

  protected readonly auditForm = this.fb.nonNullable.group({
    actorId: ['management-user'],
    changeReason: ['ui-update'],
    secondApproverId: ['']
  });

  protected readonly customerForm = this.fb.nonNullable.group({
    id: ['', Validators.required],
    name: ['', Validators.required],
    active: [true]
  });

  constructor() {
    this.loadCustomers();
  }

  protected selectCustomer(customer: Customer): void {
    this.selectedId.set(customer.id);
    this.customerForm.setValue({
      id: customer.id,
      name: customer.name,
      active: customer.active
    });
    this.customerForm.controls.id.disable();
  }

  protected resetForm(): void {
    this.selectedId.set(null);
    this.customerForm.reset({ id: '', name: '', active: true });
    this.customerForm.controls.id.enable();
  }

  protected saveCustomer(): void {
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      return;
    }

    const audit = this.auditForm.getRawValue();
    const raw = this.customerForm.getRawValue();
    const selected = this.selectedCustomer();

    const request$ = selected
      ? this.api.updateCustomer(
          selected.id,
          {
            name: raw.name,
            active: raw.active,
            changeType: 'update'
          },
          audit
        )
      : this.api.createCustomer(
          {
            id: raw.id,
            name: raw.name,
            active: raw.active,
            changeType: 'create'
          },
          audit
        );

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadCustomers();
      this.resetForm();
    });
  }

  protected deactivateCustomer(): void {
    const selected = this.selectedCustomer();
    if (!selected) {
      return;
    }

    this.api
      .deactivateCustomer(selected.id, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.loadCustomers();
        this.resetForm();
      });
  }

  private loadCustomers(): void {
    this.api
      .listCustomers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((customers) => {
        this.customers.set(customers);
      });
  }
}
