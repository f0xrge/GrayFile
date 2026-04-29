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
  defaultTimeCriterionSeconds: number;
  defaultTimePrice: number;
  defaultTokenCriterion: number;
  defaultTokenPrice: number;
}

export interface ModelRoute {
  modelId: string;
  backendId: string;
  baseUrl: string;
  deploymentId: string | null;
  provider: string | null;
  liteLlmModel: string | null;
  apiBase: string | null;
  apiVersion: string | null;
  secretRef: string | null;
  lastSyncStatus: string | null;
  lastSyncError: string | null;
  lastSyncedAt: string | null;
  weight: number;
  active: boolean;
  version: number;
  updatedAt: string;
}

export interface CustomerModelPricing {
  customerId: string;
  modelId: string;
  timeCriterionSeconds: number;
  timePrice: number;
  tokenCriterion: number;
  tokenPrice: number;
  updatedAt: string;
}

export interface UsageAnalyticsSummary {
  requestCount: number;
  durationMs: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  timeCost: number;
  tokenCost: number;
  totalCost: number;
}

export interface UsageAnalyticsBreakdown {
  customerId: string | null;
  modelId: string | null;
  requestCount: number;
  durationMs: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  timeCost: number;
  tokenCost: number;
  totalCost: number;
}

export interface UsageAnalyticsResponse {
  summary: UsageAnalyticsSummary;
  byCustomer: UsageAnalyticsBreakdown[];
  byModel: UsageAnalyticsBreakdown[];
  byCustomerModel: UsageAnalyticsBreakdown[];
}

export interface AuditHeadersInput {
  actorId?: string;
  changeReason?: string;
  secondApproverId?: string;
}

export interface LiteLlmResource {
  id: string;
  entityType: string;
  entityId: string;
  liteLlmResourceType: string;
  liteLlmResourceId: string | null;
  lastSyncStatus: string;
  lastSyncError: string | null;
  lastSyncedAt: string | null;
  updatedAt: string;
}

export interface LiteLlmHealth {
  healthy: boolean;
  mode: string | null;
  error: string | null;
}

export interface LiteLlmReconciliation {
  attempted: number;
  failed: number;
  completedAt: string;
  syncEnabled: boolean;
  dryRun: boolean;
}
