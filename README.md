# k8s-lab: two microservices

```
client ──► loan-service (8080) ──HTTP GET──► customer-service (8081 local / 8080 in container)
           /loans/offer                       /customers/{id}
```

`loan-service` calls `customer-service` **synchronously** (Spring `RestClient`) to build a loan offer.

## Endpoints

| Service | Endpoint | Notes |
|---|---|---|
| customer-service | `GET /customers/{id}` | ids 1, 2, 3 (in-memory) |
| loan-service | `GET /loans/offer?customerId=1&amount=50000&termMonths=12` | calls customer-service |
| both | `/actuator/health/liveness`, `/actuator/health/readiness` | for probes |

`loanPod` and `customerPod` in the response are the names of the pods that served the request (`HOSTNAME`), so you can watch load balancing.

## Environment variables

| Service | Variable | Default |
|---|---|---|
| both | `SERVER_PORT` | loan 8080, customer 8081 (8080 in the Dockerfile) |
| loan-service | `CUSTOMER_SERVICE_URL` | `http://localhost:8081` |
| loan-service | `CUSTOMER_SERVICE_TIMEOUT_MS` | `2000` |

## Run locally (without Kubernetes)

Start both `main` classes from your IDE, then:

```
curl "localhost:8080/loans/offer?customerId=1&amount=50000&termMonths=12"
```

## Docker images

The Dockerfiles are multi-stage: the jar is built inside the image, so you don't need Maven locally.

```
docker build -t customer-service:0.1 ./customer-service
docker build -t loan-service:0.1 ./loan-service
```

## Things to try

- Scale customer-service to 0 replicas: loan-service returns `503` (synchronous dependency).
- Scale customer-service to 3 replicas: `customerPod` changes between calls.
- customerId=3: rejected for credit score. customerId=2 with a large amount: rejected by the income rule.
