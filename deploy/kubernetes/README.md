# GrayFile Kubernetes deployment

This directory contains Kustomize manifests for a light production Kubernetes deployment.

## Layout

- `base/`: GrayFile gateway, management UI, Envoy, ext-proc, Prometheus, Grafana, services, ingress, and generated ConfigMaps.
- `managed-postgres/`: base stack for an external or managed PostgreSQL database.
- `postgres-statefulset/`: base stack plus an in-cluster PostgreSQL StatefulSet and PVC.
- `demo-mock-backend/`: in-cluster PostgreSQL plus WireMock backend for smoke tests that mirror Docker Compose.

## 1. Set deployment variables

```bash
export NS=grayfile
export REGISTRY=registry.example.com/grayfile
export TAG=0.1.0
export UI_HOST=grayfile.example.com
export LLM_HOST=llm.grayfile.example.com
```

## 2. Build and push images

```bash
docker build -t $REGISTRY/gateway:$TAG -f gateway/src/main/docker/Dockerfile.jvm .
docker build -t $REGISTRY/ext-proc:$TAG -f ext-proc/Dockerfile .
docker build -t $REGISTRY/management-ui:$TAG -f management-ui/Dockerfile .

docker push $REGISTRY/gateway:$TAG
docker push $REGISTRY/ext-proc:$TAG
docker push $REGISTRY/management-ui:$TAG
```

## 3. Create namespace and secrets

```bash
kubectl create namespace $NS

kubectl -n $NS create secret generic grayfile-db-secret \
  --from-literal=GRAYFILE_DB_URL='jdbc:postgresql://postgres.example.internal:5432/grayfile' \
  --from-literal=GRAYFILE_DB_USER='grayfile' \
  --from-literal=GRAYFILE_DB_PASSWORD='change-me'

kubectl -n $NS create secret generic grayfile-app-secret \
  --from-literal=GRAYFILE_AUDIT_SIGNING_KEY='replace-with-a-long-random-secret' \
  --from-literal=GRAYFILE_USAGE_SIGNING_KEY='replace-with-a-long-random-secret'

kubectl -n $NS create secret generic grayfile-grafana-secret \
  --from-literal=GF_SECURITY_ADMIN_USER='admin' \
  --from-literal=GF_SECURITY_ADMIN_PASSWORD='change-me'
```

For the in-cluster PostgreSQL overlay, set `GRAYFILE_DB_URL` to:

```text
jdbc:postgresql://postgres:5432/grayfile
```

If your registry is private, create an image pull secret and attach it to the namespace service account:

```bash
kubectl -n $NS create secret docker-registry regcred \
  --docker-server=registry.example.com \
  --docker-username="$REGISTRY_USER" \
  --docker-password="$REGISTRY_PASSWORD"

kubectl -n $NS patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"regcred"}]}'
```

## 4. Configure registry, tag, and hostnames

Edit `base/kustomization.yaml` image names and tags, or run these commands from the overlay directory if you have the standalone `kustomize` CLI installed:

```bash
cd deploy/kubernetes/managed-postgres
kustomize edit set image registry.example.com/grayfile/gateway=$REGISTRY/gateway:$TAG
kustomize edit set image registry.example.com/grayfile/ext-proc=$REGISTRY/ext-proc:$TAG
kustomize edit set image registry.example.com/grayfile/management-ui=$REGISTRY/management-ui:$TAG
```

Patch `base/ingress.yaml` with your real `UI_HOST`, `LLM_HOST`, ingress class, TLS secret names, and certificate issuer before applying.

From the repository root, verify the rendered manifests with:

```bash
kubectl kustomize deploy/kubernetes/managed-postgres
kubectl kustomize deploy/kubernetes/postgres-statefulset
kubectl kustomize deploy/kubernetes/demo-mock-backend
```

## 5. Choose and apply an overlay

Managed PostgreSQL:

```bash
kubectl apply -k deploy/kubernetes/managed-postgres
```

In-cluster PostgreSQL:

```bash
kubectl apply -k deploy/kubernetes/postgres-statefulset
```

Demo smoke-test stack with WireMock:

```bash
kubectl apply -k deploy/kubernetes/demo-mock-backend
```

For production with a real LLM backend, update `deploy/envoy/envoy.yaml` so the `llm_backend` cluster points to the real backend service or DNS name instead of `mock-backend`.

## 6. Verify rollout

```bash
kubectl -n $NS get pods
kubectl -n $NS get svc
kubectl -n $NS get ingress
kubectl -n $NS rollout status deploy/gateway
kubectl -n $NS rollout status deploy/envoy
kubectl -n $NS rollout status deploy/management-ui
kubectl -n $NS logs deploy/gateway
kubectl -n $NS logs deploy/envoy
```

## 7. Test endpoints

```bash
curl -k https://$UI_HOST/

curl -k https://$LLM_HOST/llm/v1/chat/completions \
  -H 'content-type: application/json' \
  -H 'x-customer-id: customer-1' \
  -H 'x-api-key-id: key-1' \
  -H 'x-llm-model: facebook/opt-125m' \
  -d '{"model":"facebook/opt-125m","messages":[{"role":"user","content":"Hello"}]}'
```

For local validation without public DNS:

```bash
kubectl -n $NS port-forward svc/management-ui 4200:80
kubectl -n $NS port-forward svc/envoy 11000:11000
kubectl -n $NS port-forward svc/prometheus 9090:9090
kubectl -n $NS port-forward svc/grafana 3000:3000
```

## Operational notes

- The management UI proxies `/management/v1/` to the `gateway` service. Restrict access with a private ingress, VPN, allowlist, or SSO layer.
- The base manifests mount Envoy, Prometheus, and Grafana config from `deploy/kubernetes/base/config/`, copied from the existing Compose-oriented `deploy/` files.
- Prometheus storage is `emptyDir` by default. Replace it with a PVC if you need metric retention across pod restarts.
- PostgreSQL StatefulSet storage defaults to `20Gi` and uses the cluster default `StorageClass`.
