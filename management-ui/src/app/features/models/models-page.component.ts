import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { LlmModel, ModelRoute } from '../../core/models/management.models';
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
  protected readonly routes = signal<ModelRoute[]>([]);
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
    active: [true]
  });

  protected readonly routeForm = this.fb.nonNullable.group({
    backendId: ['', Validators.required],
    baseUrl: ['', Validators.required],
    weight: [100, [Validators.required, Validators.min(0)]],
    active: [true]
  });

  constructor() {
    this.loadModels();

    effect(() => {
      const modelId = this.selectedModelId();
      if (modelId) {
        this.refreshRoutes(modelId);
      } else {
        this.routes.set([]);
      }
    });
  }

  protected selectModel(model: LlmModel): void {
    this.selectedModelId.set(model.id);
    this.modelForm.setValue({
      id: model.id,
      displayName: model.displayName,
      provider: model.provider,
      active: model.active
    });
    this.modelForm.controls.id.disable();
    this.resetRouteForm();
  }

  protected resetModelForm(): void {
    this.selectedModelId.set(null);
    this.modelForm.reset({ id: '', displayName: '', provider: '', active: true });
    this.modelForm.controls.id.enable();
    this.resetRouteForm();
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
    this.routeForm.reset({ backendId: '', baseUrl: '', weight: 100, active: true });
    this.routeForm.controls.backendId.enable();
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
          changeType: 'create'
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

  private refreshRoutes(modelId: string): void {
    this.api
      .listRoutes(modelId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((routes) => {
        this.routes.set(routes);
      });
  }
}
