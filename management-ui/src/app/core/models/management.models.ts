export interface Customer {
  id: string;
  name: string;
  active: boolean;
}

export interface ApiKey {
  id: string;
  customerId: string;
  name: string;
  active: boolean;
}

export interface LlmModel {
  id: string;
  displayName: string;
  provider: string;
  active: boolean;
}

export interface ModelRoute {
  modelId: string;
  backendId: string;
  baseUrl: string;
  weight: number;
  active: boolean;
  version: number;
  updatedAt: string;
}

export interface AuditHeadersInput {
  actorId?: string;
  changeReason?: string;
  secondApproverId?: string;
}
