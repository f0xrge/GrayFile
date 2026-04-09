# GrayFile Management UI

Prototype d'interface Angular pour le plan de management GrayFile.

## Stack

- Angular `^21.0.0`
- Angular Material `^21.0.0`
- Standalone components
- Reactive Forms
- Intégration directe avec l'API ` /management/v1 `

## Pages incluses

- Overview
- Clients
- API keys
- Modèles et endpoints

## Démarrage

Prévoir une version de Node.js compatible avec Angular 21, puis :

```powershell
cd management-ui
npm install
npm start
```

L'application s'attend à être servie derrière le même hôte que le backend GrayFile pour appeler directement les routes `/management/v1`.
