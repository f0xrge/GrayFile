import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Customer, CustomerModelPricing, LlmModel, ModelRoute } from '../../core/models/management.models';
import { ManagementApiService } from '../../core/services/management-api.service';

@Component({
  selector: 'gf-models-page',
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
  templateUrl: './models-page.component.html',
  styleUrl: './models-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ModelsPageComponent {
  private readonly api = inject(ManagementApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly models = signal<LlmModel[]>([]);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly routes = signal<ModelRoute[]>([]);
  protected readonly customerPricing = signal<CustomerModelPricing[]>([]);
  protected readonly selectedModelId = signal<string | null>(null);
  protected readonly selectedRouteId = signal<string | null>(null);
  protected readonly selectedModel = computed(
    () => this.models().find((model) => model.id === this.selectedModelId()) ?? null
  );

  protected readonly auditForm = this.fb.nonNullable.group({
    actorId: ['management-user'],
    changeReason: ['ui-update'],
    secondApproverId: ['']
  });

  protected readonly modelForm = this.fb.nonNullable.group({
    id: ['', Validators.required],
    displayName: ['', Validators.required],
    provider: ['', Validators.required],
    defaultTimeCriterionSeconds: [600, [Validators.required, Validators.min(1)]],
    defaultTimePrice: [0, [Validators.required, Validators.min(0)]],
    defaultTokenCriterion: [1000, [Validators.required, Validators.min(1)]],
    defaultTokenPrice: [0, [Validators.required, Validators.min(0)]],
    active: [true]
  });

  protected readonly routeForm = this.fb.nonNullable.group({
    backendId: ['', Validators.required],
    baseUrl: ['', Validators.required],
    weight: [100, [Validators.required, Validators.min(1)]],
    active: [false]
  });

  protected readonly pricingForm = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    timeCriterionSeconds: [600, [Validators.required, Validators.min(1)]],
    timePrice: [0, [Validators.required, Validators.min(0)]],
    tokenCriterion: [1000, [Validators.required, Validators.min(1)]],
    tokenPrice: [0, [Validators.required, Validators.min(0)]]
  });

  constructor() {
    this.loadModels();
    this.loadCustomers();

    effect(() => {
      const modelId = this.selectedModelId();
      if (modelId) {
        this.refreshRoutes(modelId);
        this.refreshPricing(modelId);
      } else {
        this.routes.set([]);
        this.customerPricing.set([]);
      }
    });
  }

  protected selectModel(model: LlmModel): void {
    this.selectedModelId.set(model.id);
    this.modelForm.setValue({
      id: model.id,
      displayName: model.displayName,
      provider: model.provider,
      defaultTimeCriterionSeconds: model.defaultTimeCriterionSeconds,
      defaultTimePrice: model.defaultTimePrice,
      defaultTokenCriterion: model.defaultTokenCriterion,
      defaultTokenPrice: model.defaultTokenPrice,
      active: model.active
    });
    this.modelForm.controls.id.disable();
    this.resetRouteForm();
  }

  protected resetModelForm(): void {
    this.selectedModelId.set(null);
    this.modelForm.reset({
      id: '',
      displayName: '',
      provider: '',
      defaultTimeCriterionSeconds: 600,
      defaultTimePrice: 0,
      defaultTokenCriterion: 1000,
      defaultTokenPrice: 0,
      active: true
    });
    this.modelForm.controls.id.enable();
    this.resetRouteForm();
    this.resetPricingForm();
  }

  protected saveModel(): void {
    if (this.modelForm.invalid) {
      this.modelForm.markAllAsTouched();
      return;
    }

    const audit = this.auditForm.getRawValue();
    const raw = this.modelForm.getRawValue();
    const selected = this.selectedModel();

    const request$ = selected
      ? this.api.updateModel(
          selected.id,
          {
            displayName: raw.displayName,
            provider: raw.provider,
            active: raw.active,
            defaultTimeCriterionSeconds: raw.defaultTimeCriterionSeconds,
            defaultTimePrice: raw.defaultTimePrice,
            defaultTokenCriterion: raw.defaultTokenCriterion,
            defaultTokenPrice: raw.defaultTokenPrice,
            changeType: 'update'
          },
          audit
        )
      : this.api.createModel(
          {
            id: raw.id,
            displayName: raw.displayName,
            provider: raw.provider,
            active: raw.active,
            defaultTimeCriterionSeconds: raw.defaultTimeCriterionSeconds,
            defaultTimePrice: raw.defaultTimePrice,
            defaultTokenCriterion: raw.defaultTokenCriterion,
            defaultTokenPrice: raw.defaultTokenPrice,
            changeType: 'create'
          },
          audit
        );

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadModels();
      this.resetModelForm();
    });
  }

  protected deactivateModel(): void {
    const model = this.selectedModel();
    if (!model) {
      return;
    }

    this.api
      .deactivateModel(model.id, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.loadModels();
        this.resetModelForm();
      });
  }

  protected selectRoute(route: ModelRoute): void {
    this.selectedRouteId.set(route.backendId);
    this.routeForm.setValue({
      backendId: route.backendId,
      baseUrl: route.baseUrl,
      weight: route.weight,
      active: route.active
    });
    this.routeForm.controls.backendId.disable();
  }

  protected resetRouteForm(): void {
    this.selectedRouteId.set(null);
    this.routeForm.reset({ backendId: '', baseUrl: '', weight: 100, active: false });
    this.routeForm.controls.backendId.enable();
  }

  protected savePricing(): void {
    const model = this.selectedModel();
    if (!model) {
      return;
    }
    if (this.pricingForm.invalid) {
      this.pricingForm.markAllAsTouched();
      return;
    }

    const raw = this.pricingForm.getRawValue();
    this.api
      .upsertCustomerPricing(
        model.id,
        raw.customerId,
        {
          timeCriterionSeconds: raw.timeCriterionSeconds,
          timePrice: raw.timePrice,
          tokenCriterion: raw.tokenCriterion,
          tokenPrice: raw.tokenPrice,
          changeType: 'pricing'
        },
        this.auditForm.getRawValue()
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshPricing(model.id);
        this.resetPricingForm();
      });
  }

  protected editPricing(pricing: CustomerModelPricing): void {
    this.pricingForm.setValue({
      customerId: pricing.customerId,
      timeCriterionSeconds: pricing.timeCriterionSeconds,
      timePrice: pricing.timePrice,
      tokenCriterion: pricing.tokenCriterion,
      tokenPrice: pricing.tokenPrice
    });
  }

  protected deletePricing(pricing: CustomerModelPricing): void {
    this.api
      .deleteCustomerPricing(pricing.modelId, pricing.customerId, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshPricing(pricing.modelId);
        this.resetPricingForm();
      });
  }

  protected resetPricingForm(): void {
    this.pricingForm.reset({ customerId: '', timeCriterionSeconds: 600, timePrice: 0, tokenCriterion: 1000, tokenPrice: 0 });
  }

  protected saveRoute(): void {
    const model = this.selectedModel();
    if (!model) {
      return;
    }
    if (this.routeForm.invalid) {
      this.routeForm.markAllAsTouched();
      return;
    }

    const raw = this.routeForm.getRawValue();
    this.api
      .createRoute(
        model.id,
        {
          backendId: raw.backendId,
          baseUrl: raw.baseUrl,
          weight: raw.weight,
          active: raw.active,
          changeType: 'routing'
        },
        this.auditForm.getRawValue()
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshRoutes(model.id);
        this.resetRouteForm();
      });
  }

  protected toggleRoute(route: ModelRoute): void {
    this.api
      .setRouteActive(route.modelId, route.backendId, !route.active, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshRoutes(route.modelId);
      });
  }

  protected updateRouteWeight(route: ModelRoute, weight: string): void {
    const numericWeight = Number(weight);
    if (Number.isNaN(numericWeight)) {
      return;
    }

    this.api
      .setRouteWeight(route.modelId, route.backendId, numericWeight, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshRoutes(route.modelId);
      });
  }

  protected deleteRoute(route: ModelRoute): void {
    this.api
      .deleteRoute(route.modelId, route.backendId, this.auditForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshRoutes(route.modelId);
        this.resetRouteForm();
      });
  }

  private loadModels(): void {
    this.api
      .listModels()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((models) => {
        this.models.set(models);
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

  private refreshRoutes(modelId: string): void {
    this.api
      .listRoutes(modelId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((routes) => {
        this.routes.set(routes);
      });
  }

  private refreshPricing(modelId: string): void {
    this.api
      .listCustomerPricing(modelId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((pricing) => {
        this.customerPricing.set(pricing);
      });
  }
}
