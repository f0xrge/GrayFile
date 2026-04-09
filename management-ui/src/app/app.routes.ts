import { Routes } from '@angular/router';
import { ApiKeysPageComponent } from './features/api-keys/api-keys-page.component';
import { CustomersPageComponent } from './features/customers/customers-page.component';
import { ModelsPageComponent } from './features/models/models-page.component';
import { OverviewPageComponent } from './features/overview/overview-page.component';

export const appRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'overview' },
  { path: 'overview', component: OverviewPageComponent },
  { path: 'customers', component: CustomersPageComponent },
  { path: 'api-keys', component: ApiKeysPageComponent },
  { path: 'models', component: ModelsPageComponent },
  { path: '**', redirectTo: 'overview' }
];
