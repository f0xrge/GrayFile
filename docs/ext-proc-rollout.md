# ext_proc minimal pour extraction `usage.*`

## 1) Implementation minimale

- Le filtre Envoy `envoy.filters.http.ext_proc` remplace le filtre Lua sur le listener egress `llm_backend_egress_listener`.
- Le mode de traitement est volontairement minimal:
  - `response_body_mode: BUFFERED`
  - tous les autres modes en `SKIP`.
- Le service gRPC Java `ext-proc`:
  - lit le body JSON de reponse `chat/completions`,
  - extrait `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`,
  - injecte des headers `x-edge-usage-*` identiques au contrat deja consomme par GrayFile.

## 2) Mesure overhead latence p95/p99

Script Java single-file: `scripts/MeasureExtProcOverhead.java`

Exemple:

```bash
java scripts/MeasureExtProcOverhead.java \
  --baseline-url http://localhost:8080/llm/v1/chat/completions \
  --ext-proc-url http://localhost:11000/llm/v1/chat/completions \
  --iterations 500
```

Sortie attendue: JSON avec p50/p95/p99 pour baseline + ext_proc, puis delta p95/p99 (overhead).

## 3) Cas limites couverts

Tests unitaires (`ext-proc/src/test/java/io/grayfile/extproc/UsageExtractorTest.java`):
- payload chunked complet (reconstruction puis parse OK),
- payload incomplet (`end_of_stream=false`),
- JSON invalide,
- absence de `usage`,
- valeurs `usage` invalides.

## 4) Fallback fail-open

Politique configuree cote Envoy:

- `failure_mode_allow: true` sur `envoy.filters.http.ext_proc`.

Consequence:
- si ext_proc est indisponible / timeout / erreur gRPC, Envoy **laisse passer** la reponse backend,
- GrayFile continue son parsing applicatif cote gateway (`UsageCaptureService`) et reste la source de verite de facturation.

## 5) Criteres go / no-go selon SLO

### Go
- Overhead mesure <= **+5 ms p95** et <= **+10 ms p99** vs baseline.
- Taux de reponses `x-edge-usage-extraction=ok` >= **99.5%** sur trafic nominal.
- Aucun impact observable sur taux 5xx/504 cote egress.
- Aucun ecart de facturation (divergence edge/backend) au-dela de **0.1%**.

### No-go
- Depassement d'un seuil p95/p99 ci-dessus sur 2 runs consecutifs.
- Erreurs ext_proc degradant la disponibilite percue (malgre fail-open).
- Divergences de tokens edge/backend > 0.1%.
- Hausse correlee des retries/timeout egress apres activation ext_proc.

## Plan de deploiement recommande

1. Activer ext_proc en environnement de charge de pre-prod.
2. Executer la mesure p95/p99 (minimum 2 runs).
3. Verifier divergence + disponibilite.
4. Promotion progressive (canary 5% > 25% > 100%).
