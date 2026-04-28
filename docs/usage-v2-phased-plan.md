# Plan d’exécution en 4 phases — Usage v2 multi-endpoints

Ce document structure le déploiement de la capture d’usage **v2** en quatre phases successives, avec des critères de sortie explicites.

## Objectifs globaux

- Uniformiser la capture d’usage pour plusieurs endpoints OpenAI-compatibles.
- Éliminer le double comptage via des clés de déduplication stables.
- Limiter l’impact latence au niveau du proxy.
- Garantir une observabilité exploitable (dashboards + alerting + conformité).

## Phase A — Fondations

### Périmètre

1. **Routeur multi-endpoints**
   - Généraliser le routage selon `OpenAiEndpoint` (ou équivalent) avec règles explicites par endpoint.
   - Introduire un dispatch central des extracteurs (`endpoint -> extractor strategy`).
2. **Contrat d’usage v2**
   - Définir un schéma canonique v2 (tokens, unités non-token, latence, métadonnées provider, endpoint, streaming flag).
   - Rendre le contrat versionné (`usage_contract_version = 2`) et backward compatible lecture.
3. **Migration DB minimale**
   - Ajouter uniquement les colonnes/champs indispensables au v2 (endpoint, usage_unit_type, version, provider_request_id si absent).
   - Préparer index de déduplication sans activer de contraintes risquées tant que les flux ne sont pas complets.

### Livrables

- Routeur endpoint-aware en production derrière feature flag.
- Schéma `UsageExtractionContract v2` validé et documenté.
- Migration Flyway minimale appliquée et testée en rollback logique.

### Critères de sortie (Go/No-Go)

- **Précision usage**: écart ≤ **2%** vs vérité terrain sur jeux de tests non-streaming de référence.
- **Double comptage**: **0 doublon** sur tests de rejouabilité idempotente (même requête rejouée N fois).
- **Latence**: overhead p95 proxy ≤ **+15 ms** sur endpoints activés.
- **Couverture tests**: tests unitaires + intégration DB pour contrat v2 et migration; succès CI sur suite concernée.

## Phase B — Extraction d’usage (non-streaming d’abord)

### Périmètre

Implémenter les extracteurs par endpoint prioritaire, **réponses non-streaming en premier**:

1. `responses`
   - Parse usage tokens `input/output/total` et champs provider associés.
2. `embeddings`
   - Extraction orientée input tokens (et batch metadata si présent).
3. `audio transcriptions`
   - Extraction non-token (durée audio, secondes facturables, caractères selon modèle de pricing).

### Stratégie

- Pipeline d’extraction commun (normalize -> validate -> persist).
- Politique de fallback explicite par endpoint (usage absent => `unknown` + métrique dédiée).
- Jeux de fixtures JSON réels/anonymisés par endpoint.

### Livrables

- 3 extracteurs endpoint-spécifiques non-streaming.
- Tests unitaires exhaustifs des parseurs + tests d’intégration persistence.
- Métriques d’extraction: succès, fallback, champs manquants, invalid payload.

### Critères de sortie (Go/No-Go)

- **Précision usage**: écart ≤ **1%** sur corpus goldens non-streaming (`responses`, `embeddings`, `audio transcriptions`).
- **Double comptage**: aucune collision de clé de dédup sur corpus + replays.
- **Latence**: overhead p95 extraction non-streaming ≤ **+20 ms**.
- **Couverture tests**: ≥ **90%** sur package extracteurs + scénarios d’intégration critiques couverts.

## Phase C — Streaming

### Périmètre

1. **Parsing SSE / chunked**
   - Agrégation incrémentale des chunks.
   - Gestion des événements de fin (`[DONE]`, close stream, timeout).
2. **Finalisation usage**
   - Émission d’un event d’usage final unique en fin de stream.
   - Support des cas partiels (stream interrompu) avec statut explicite.
3. **Tests de robustesse**
   - Réseaux instables, chunks hors ordre/partiels, fermeture prématurée, retries.

### Livrables

- Parseur streaming robuste avec état de session.
- Mécanisme de commit final idempotent (anti-double-comptage garanti).
- Suite de tests de résilience + charge légère.

### Critères de sortie (Go/No-Go)

- **Précision usage**: écart ≤ **2%** entre streaming et non-streaming équivalent sur scénarios comparables.
- **Double comptage**: **0 duplication** d’événement final en cas de retry/timeout/reconnect testés.
- **Latence**: pas de dégradation perceptible du temps au premier token; overhead p95 finalisation ≤ **+25 ms**.
- **Couverture tests**: tests robustesse streaming couvrant erreurs réseau majeures + idempotence finale validée.

## Phase D — Hardening

### Périmètre

1. **Dashboards finaux**
   - Vues par endpoint, modèle, client, type d’unité, taux fallback, précision estimée.
2. **Alerting**
   - Alertes sur dérive de précision, pics de fallback, anomalies de déduplication, hausse latence.
3. **Tests conformité provider**
   - Matrice de compatibilité par endpoint/provider/version API.
4. **Documentation / matrice publique**
   - Contrat v2 public, limites connues, champs optionnels, compatibilité streaming.

### Livrables

- Dashboards Grafana production-ready + SLO/SLA usage.
- Règles alerting Prometheus/Alertmanager opérationnelles.
- Rapport de conformité provider signé + doc publique versionnée.

### Critères de sortie (Go/No-Go)

- **Précision usage**: dérive hebdomadaire ≤ **1%** sur top endpoints.
- **Double comptage**: taux de duplication détecté ≤ **0.01%** (cible long terme: 0).
- **Latence**: SLO p95 global respecté sur 7 jours glissants.
- **Couverture tests**: conformité provider ≥ **95%** des combinaisons en matrice prioritaire.

## Gouvernance transversale (toutes phases)

- Feature flags par endpoint et par mode (streaming/non-streaming).
- Déploiement progressif (canary client puis généralisation).
- Revue hebdomadaire des métriques de qualité:
  - précision vs vérité terrain,
  - taux fallback,
  - duplications,
  - overhead latence.
- Critère de rollback: dépassement seuils critiques sur 2 fenêtres consécutives.

## Définition commune des KPIs

- **Précision usage** = `abs(usage_mesuré - usage_référence) / usage_référence`.
- **Absence de double comptage** = unicité de la clé (`tenant`, `provider_request_id`, `endpoint`, `finalization_marker`).
- **Latence acceptable** = overhead p95 ajouté par la chaîne extraction/persistance, mesuré à charge comparable.
- **Couverture tests** = couverture code extracteurs + couverture scénarios métier critiques.
