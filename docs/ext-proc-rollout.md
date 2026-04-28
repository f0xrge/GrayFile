# ext_proc minimal pour extraction `usage.*`

## 1) Implémentation minimale

- Le filtre Envoy `envoy.filters.http.ext_proc` remplace le filtre Lua sur le listener egress `llm_backend_egress_listener`.
- Le mode de traitement est volontairement minimal:
  - `response_body_mode: BUFFERED`
  - tous les autres modes en `SKIP`.
- Le service gRPC `deploy/ext_proc/app.py`:
  - lit le body JSON de réponse `chat/completions`,
  - extrait `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`,
  - injecte des headers `x-edge-usage-*` identiques au contrat déjà consommé par GrayFile.

## 2) Mesure overhead latence p95/p99

Script: `scripts/measure_ext_proc_overhead.py`

Exemple:

```bash
python scripts/measure_ext_proc_overhead.py \
  --baseline-url http://localhost:8080/llm/v1/chat/completions \
  --ext-proc-url http://localhost:11000/llm/v1/chat/completions \
  --iterations 500
```

Sortie attendue: JSON avec p50/p95/p99 pour baseline + ext_proc, puis delta p95/p99 (overhead).

## 3) Cas limites couverts

Tests unitaires (`tests/ext_proc/test_ext_proc_usage.py`):
- payload chunked complet (reconstruction puis parse OK),
- payload incomplet (`end_of_stream=false`),
- JSON invalide,
- absence de `usage`,
- valeurs `usage` invalides.

## 4) Fallback fail-open

Politique configurée côté Envoy:

- `failure_mode_allow: true` sur `envoy.filters.http.ext_proc`.

Conséquence:
- si ext_proc est indisponible / timeout / erreur gRPC, Envoy **laisse passer** la réponse backend,
- GrayFile continue son parsing applicatif côté gateway (`UsageCaptureService`) et reste la source de vérité de facturation.

### Fallback spécifique streaming/chunked

- Pour les réponses SSE/chunked, ext_proc n’émet les headers `x-edge-usage-*` qu’à `end_of_stream=true`.
- Si le flux n’est pas terminé côté ext_proc, le statut est `incomplete_stream` (audit + skip de capture tant que la métrique finale n’est pas disponible).
- Si le flux se termine mais qu’aucun bloc final avec `usage.*` n’est trouvé, le statut est `stream_final_missing_usage`.
- Dans ce dernier cas, la gateway ne persiste pas d’événement de consommation et journalise un audit explicite (`USAGE_EXTRACTION_AUDIT`) pour investigation backend/contrat.

## 5) Critères go / no-go selon SLO

### Go
- Overhead mesuré <= **+5 ms p95** et <= **+10 ms p99** vs baseline.
- Taux de réponses `x-edge-usage-extraction=ok` >= **99.5%** sur trafic nominal.
- Aucun impact observable sur taux 5xx/504 côté egress.
- Aucun écart de facturation (divergence edge/backend) au-delà de **0.1%**.

### No-go
- Dépassement d'un seuil p95/p99 ci-dessus sur 2 runs consécutifs.
- Erreurs ext_proc dégradant la disponibilité perçue (malgré fail-open).
- Divergences de tokens edge/backend > 0.1%.
- Hausse corrélée des retries/timeout egress après activation ext_proc.

## Plan de déploiement recommandé

1. Activer ext_proc en environnement de charge de pré-prod.
2. Exécuter la mesure p95/p99 (minimum 2 runs).
3. Vérifier divergence + disponibilité.
4. Promotion progressive (canary 5% > 25% > 100%).
