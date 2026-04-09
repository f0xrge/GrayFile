import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiKey, AuditHeadersInput, Customer, LlmModel, ModelRoute } from '../models/management.models';

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/management/v1';

  listCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.baseUrl}/customers`);
  }

  createCustomer(payload: CustomerPayload, audit?: AuditHeadersInput): Observable<Customer> {
    return this.http.post<Customer>(`${this.baseUrl}/customers`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  updateCustomer(customerId: string, payload: CustomerUpdatePayload, audit?: AuditHeadersInput): Observable<Customer> {
    return this.http.put<Customer>(`${this.baseUrl}/customers/${customerId}`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  deactivateCustomer(customerId: string, audit?: AuditHeadersInput): Observable<Customer> {
    return this.http.delete<Customer>(`${this.baseUrl}/customers/${customerId}`, {
      headers: this.auditHeaders(audit),
      params: new HttpParams().set('changeType', 'deactivation')
    });
  }

  listApiKeys(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(`${this.baseUrl}/api-keys`);
  }

  createApiKey(payload: ApiKeyPayload, audit?: AuditHeadersInput): Observable<ApiKey> {
    return this.http.post<ApiKey>(`${this.baseUrl}/api-keys`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  updateApiKey(apiKeyId: string, payload: ApiKeyUpdatePayload, audit?: AuditHeadersInput): Observable<ApiKey> {
    return this.http.put<ApiKey>(`${this.baseUrl}/api-keys/${apiKeyId}`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  deactivateApiKey(apiKeyId: string, audit?: AuditHeadersInput): Observable<ApiKey> {
    return this.http.delete<ApiKey>(`${this.baseUrl}/api-keys/${apiKeyId}`, {
      headers: this.auditHeaders(audit),
      params: new HttpParams().set('changeType', 'deactivation')
    });
  }

  listModels(): Observable<LlmModel[]> {
    return this.http.get<LlmModel[]>(`${this.baseUrl}/models`);
  }

  createModel(payload: ModelPayload, audit?: AuditHeadersInput): Observable<LlmModel> {
    return this.http.post<LlmModel>(`${this.baseUrl}/models`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  updateModel(modelId: string, payload: ModelUpdatePayload, audit?: AuditHeadersInput): Observable<LlmModel> {
    return this.http.put<LlmModel>(`${this.baseUrl}/models/${modelId}`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  deactivateModel(modelId: string, audit?: AuditHeadersInput): Observable<LlmModel> {
    return this.http.delete<LlmModel>(`${this.baseUrl}/models/${modelId}`, {
      headers: this.auditHeaders(audit),
      params: new HttpParams().set('changeType', 'deactivation')
    });
  }

  listRoutes(modelId: string): Observable<ModelRoute[]> {
    return this.http.get<ModelRoute[]>(`${this.baseUrl}/models/${modelId}/routes`);
  }

  createRoute(modelId: string, payload: ModelRoutePayload, audit?: AuditHeadersInput): Observable<ModelRoute> {
    return this.http.post<ModelRoute>(`${this.baseUrl}/models/${modelId}/routes`, payload, {
      headers: this.auditHeaders(audit)
    });
  }

  setRouteActive(modelId: string, backendId: string, active: boolean, audit?: AuditHeadersInput): Observable<ModelRoute> {
    return this.http.put<ModelRoute>(`${this.baseUrl}/models/${modelId}/routes/${backendId}/active`, {
      active,
      changeType: 'activation'
    }, {
      headers: this.auditHeaders(audit)
    });
  }

  setRouteWeight(modelId: string, backendId: string, weight: number, audit?: AuditHeadersInput): Observable<ModelRoute> {
    return this.http.put<ModelRoute>(`${this.baseUrl}/models/${modelId}/routes/${backendId}/weight`, {
      weight,
      changeType: 'weight-adjustment'
    }, {
      headers: this.auditHeaders(audit)
    });
  }

  deleteRoute(modelId: string, backendId: string, audit?: AuditHeadersInput): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/models/${modelId}/routes/${backendId}`, {
      headers: this.auditHeaders(audit),
      params: new HttpParams().set('changeType', 'deletion')
    });
  }

  private auditHeaders(audit?: AuditHeadersInput): HttpHeaders {
    let headers = new HttpHeaders();

    if (audit?.actorId) {
      headers = headers.set('x-actor-id', audit.actorId);
    }
    if (audit?.changeReason) {
      headers = headers.set('x-change-reason', audit.changeReason);
    }
    if (audit?.secondApproverId) {
      headers = headers.set('x-second-approver-id', audit.secondApproverId);
    }

    return headers;
  }
}

export interface CustomerPayload {
  id: string;
  name: string;
  active: boolean;
  changeType: string;
}

export interface CustomerUpdatePayload {
  name: string;
  active: boolean;
  changeType: string;
}

export interface ApiKeyPayload {
  id: string;
  customerId: string;
  name: string;
  active: boolean;
  changeType: string;
}

export interface ApiKeyUpdatePayload {
  name: string;
  active: boolean;
  changeType: string;
}

export interface ModelPayload {
  id: string;
  displayName: string;
  provider: string;
  active: boolean;
  changeType: string;
}

export interface ModelUpdatePayload {
  displayName: string;
  provider: string;
  active: boolean;
  changeType: string;
}

export interface ModelRoutePayload {
  backendId: string;
  baseUrl: string;
  weight: number;
  active: boolean;
  changeType: string;
}
