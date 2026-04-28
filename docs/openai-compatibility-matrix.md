# Matrice de compatibilité OpenAI (GrayFile)

Ce document formalise les endpoints OpenAI-compatible **ciblés** par GrayFile, leur statut d’implémentation, leurs contraintes d’usage (metering/billing), la prise en charge du streaming, et les écarts attendus selon le backend (vLLM, NIM, autres APIs compatibles).

## 1) Statut par endpoint

### Légende des statuts
- `supported` : endpoint activé en production avec contrat d’usage stabilisé et tests de conformité automatiques.
- `beta` : endpoint implémenté côté proxy + extraction d’usage, mais couverture de conformité encore partielle.
- `pass-through` : endpoint routé sans contrat de facturation actif (ou endpoint enregistré pour futur routage model-agnostic).
- `unsupported` : endpoint non implémenté dans GrayFile.

| Endpoint | Méthode(s) | Statut | Notes |
|---|---|---|---|
| `/llm/v1/chat/completions` | `POST` | `supported` | Endpoint de référence V1, couverture de tests la plus complète. |
| `/llm/v1/responses` | `POST` | `beta` | Support proxy + usage tokenisé, couverture à compléter. |
| `/llm/v1/completions` | `POST` | `beta` | Support proxy + usage tokenisé, couverture à compléter. |
| `/llm/v1/embeddings` | `POST` | `beta` | Extraction usage spécifique embeddings (`input_tokens`). |
| `/llm/v1/audio/transcriptions` | `POST` | `beta` | Contrat non-token (unités audio). |
| `/llm/v1/audio/translations` | `POST` | `beta` | Contrat non-token (unités audio). |
| `/llm/v1/audio/speech` | `POST` | `beta` | Contrat non-token (unités audio). |
| `/llm/v1/images/generations` | `POST` | `beta` | Contrat non-token (images générées). |
| `/llm/v1/moderations` | `POST` | `beta` | Contrat non-token (requêtes/unités). |
| `/llm/v1/models` | `GET` | `pass-through` | Endpoint enregistré mais actuellement désactivé (retour `501` tant que routage model-agnostic non configuré). |
| `/llm/v1/files` | `GET`, `POST` | `pass-through` | Endpoint enregistré mais actuellement désactivé (retour `501` tant que routage model-agnostic non configuré). |
| Autres endpoints OpenAI (assistants, batches, fine-tuning, vector stores, etc.) | N/A | `unsupported` | Non déclarés dans `OpenAiEndpoint` et non exposés dans `LlmProxyResource`. |

## 2) Contrat par endpoint

## Exigences transverses (tous endpoints déclarés)
- Headers requis : `x-customer-id`, `x-api-key-id`.
- Pour les endpoints `requiresModel=true`, champ JSON `model` obligatoire et non vide.
- Si endpoint déclaré mais non activable sans modèle (`requiresModel=false`), GrayFile retourne `501`.

## Détail endpoint par endpoint

| Endpoint | Champs requis | Stratégie d’usage / unité de facturation | Streaming |
|---|---|---|---|
| `/v1/chat/completions` | `model` | Extraction token (`input/prompt_tokens`, `output/completion_tokens`, `total_tokens`), billable. | Oui (`stream=true`), via headers edge finaux `x-edge-usage-*`. |
| `/v1/responses` | `model` | Extraction token, billable. | Oui (même stratégie que chat/completions). |
| `/v1/completions` | `model` | Extraction token, billable. | Oui (si backend expose les headers edge finaux). |
| `/v1/embeddings` | `model` | Extraction `input_tokens`/`prompt_tokens`, `total_tokens` (fallback total=input), billable. | Non ciblé en V1 (pas de parcours de conformité streaming embeddings). |
| `/v1/audio/transcriptions` | `model` | Non-token : `usage.billable_units` > fallback `usage.audio_seconds` > fallback `duration_seconds` > fallback `audio_duration_seconds`, billable. | Non ciblé en V1. |
| `/v1/audio/translations` | `model` | Même stratégie que transcriptions (unités audio), billable. | Non ciblé en V1. |
| `/v1/audio/speech` | `model` | Même stratégie que transcriptions (unités audio), billable. | Non ciblé en V1. |
| `/v1/images/generations` | `model` | Non-token : `usage.billable_units` > `usage.image_count` > `images_generated` > `data[].size`, billable. | Non ciblé en V1. |
| `/v1/moderations` | `model` | Non-token : `usage.billable_units` (sinon ambigu), billable. | Non ciblé en V1. |
| `/v1/models` | Aucun `model` requis | Non-billable, actuellement endpoint désactivé (`501`). | Non |
| `/v1/files` | Aucun `model` requis | Non-billable, actuellement endpoint désactivé (`501`). | Non |

### Règles de facturation / capture
- Endpoint billable = candidat à `UsageCaptureService` (sinon `x-grayfile-usage-capture=not_billable_endpoint`).
- Contrat canonique persistant : `usage_extraction.v2`.
- Type d’unité facturable dérivé :
  - `tokens` si usage token.
  - `audio_seconds` pour audio.
  - `images` pour générations d’images.
  - `requests` pour modérations (et fallback défaut).

## 3) Différences backend (vLLM, NIM, autres)

La gateway est volontairement backend-agnostic mais les payloads de `usage` varient selon fournisseur.

| Sujet | vLLM (typique) | NVIDIA NIM (typique) | Autres OpenAI-compatible |
|---|---|---|---|
| Noms de champs tokens | Souvent `prompt_tokens`, `completion_tokens`, `total_tokens` | Peut exposer `input_tokens`, `output_tokens`, `total_tokens` | Variable ; GrayFile accepte les 2 conventions pour input/output. |
| Streaming token usage | Souvent via extraction edge finale (`x-edge-usage-*`) | Idem via ext_proc/edge | Si aucun header final, capture marquée `stream_final_missing_usage`. |
| Audio/Images/Moderation units | Pas toujours normalisé | Pas toujours normalisé | GrayFile applique des fallbacks par endpoint ; sinon audit ambigu (`missing_billable_units`). |
| Compatibilité `/models` et `/files` | Dépend backend | Dépend backend | Côté GrayFile ces endpoints sont enregistrés mais non activés (mode model-agnostic non en place). |

**Principe commun** : GrayFile forward la réponse backend au client, et n’ajoute que ses headers de traçabilité/usage.

## 4) Politique de versioning (API + contrat usage)

Versionnement sémantique recommandé pour la matrice et les tests de conformité :

- **MAJOR** (x.0.0) : rupture de contrat d’usage ou de sémantique de facturation.
  - Exemples : changement du contrat canonique (`usage_extraction.v2` -> `v3`), changement des unités facturables d’un endpoint, suppression d’un champ requis.
- **MINOR** (0.x.0) : ajout backward-compatible d’un endpoint, passage `beta` -> `supported`, ajout d’un fallback d’extraction non cassant.
- **PATCH** (0.0.x) : correction documentaire, clarification de statut, fix de test sans changement de contrat.

### Règle explicite demandée
- **Ajout d’endpoint = MINOR**.
- **Changement de contrat usage = MAJOR**.

## 5) Lien avec les tests de conformité automatisés

## Sources de tests actuelles
- Intégration gateway : `gateway/src/test/java/io/grayfile/api/LlmProxyResourceTest.java`.
- Extraction edge/ext_proc : `tests/ext_proc/test_ext_proc_usage.py`.

## Matrice de couverture (à maintenir à chaque release)

| Endpoint | Niveau attendu | Tests existants | Gap à fermer |
|---|---|---|---|
| `/v1/chat/completions` | `supported` | Tests proxy, persistance usage, divergence edge/backend, streaming succès/échec. | Maintenir au vert. |
| `/v1/responses` | `beta` | Couvert indirectement par extracteur token, pas de suite dédiée endpoint. | Ajouter suite dédiée `responses` (sync + stream + divergence). |
| `/v1/completions` | `beta` | Couvert indirectement par extracteur token, pas de suite dédiée endpoint. | Ajouter suite dédiée `completions`. |
| `/v1/embeddings` | `beta` | Couvert via extracteur embeddings (niveau composant), pas de test endpoint dédié. | Ajouter test d’intégration endpoint + facturation. |
| `/v1/audio/*` | `beta` | Couverture extracteur non-token uniquement. | Ajouter tests d’intégration par endpoint audio. |
| `/v1/images/generations` | `beta` | Couverture extracteur non-token uniquement. | Ajouter tests d’intégration endpoint. |
| `/v1/moderations` | `beta` | Couverture extracteur non-token uniquement. | Ajouter tests d’intégration endpoint. |
| `/v1/models`, `/v1/files` | `pass-through` | Pas de test de conformité fonctionnelle (désactivés). | Ajouter tests de garde (retour `501`) tant que désactivés. |

## Gate de conformité CI recommandé
1. Générer/maintenir cette matrice sous versioning.
2. Vérifier qu’un endpoint marqué `supported` possède au minimum :
   - test happy path,
   - test erreur de contrat usage,
   - test streaming (si applicable),
   - test facturation/contract headers.
3. Refuser en CI le passage `beta` -> `supported` sans couverture complète.

