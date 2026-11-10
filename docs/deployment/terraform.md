# Terraform - AWS LocalStack provisioning

The [Terraform configuration](../../infra/terraform/) provisions the S3 bucket, SQS queue
and Secrets Manager secret used by the `aws-localstack` Spring profile.
All resources are provisioned against a running [LocalStack](https://localstack.cloud/) instance.

## What is provisioned

| Resource | Name | Spring property |
|---|---|---|
| S3 bucket | `ecommerce-order-exports` | `aws.s3.bucket-name` |
| SQS queue | `ecommerce-order-audit` | `aws.sqs.queue-url` |
| Secrets Manager secret | `ecommerce/db-credentials` | `aws.secretsmanager.secret-name` |

The secret payload is `{"username":"sa","password":"sa"}` - matching the Postgres service
credentials in `docker-compose.yml`.

## Option A: via docker-compose (automatic)

This is the easiest path.  The `terraform` one-shot service in `docker-compose.yml` runs
`terraform init && terraform apply -auto-approve` automatically once LocalStack is healthy:

```bash
# Start LocalStack (published to localhost:4566)
docker compose --profile aws up -d localstack

# Run Terraform (waits for LocalStack to be healthy, then provisions)
docker compose --profile aws up terraform
```

> **Endpoint note**: when running inside Docker Compose the `terraform` container reaches
> LocalStack via the Docker-internal service name `http://localstack:4566` (set via
> `TF_VAR_localstack_endpoint`).  This is the same dual-endpoint pattern used for Keycloak
> in `application-docker.yml`: browser/host traffic uses `localhost:8081`, container-to-
> container traffic uses `keycloak:8080`.

## Option B: manually on the host

If you prefer to run Terraform directly (requires `terraform` CLI 1.5 or later):

```bash
# Start LocalStack first
docker compose --profile aws up -d localstack

# Init and apply against the host-published endpoint
cd infra/terraform
terraform init
terraform apply        # uses the default localstack_endpoint = http://localhost:4566
```

To verify:

```bash
# List the S3 bucket
docker exec ecommerce-localstack awslocal s3 ls

# List SQS queues
docker exec ecommerce-localstack awslocal sqs list-queues

# Show the secret
docker exec ecommerce-localstack awslocal secretsmanager list-secrets
```

## Running the Spring Boot app with the aws-localstack profile

```bash
SPRING_PROFILES_ACTIVE=local,aws-localstack ./gradlew :application:ecommerce:bootRun
```

Start the [standard local dependencies](../development.md#run-the-app-from-an-ide-with-infrastructure-in-docker)
before running this profile. After placing a demo order, inspect the S3 export and SQS audit event.

```bash
docker exec ecommerce-localstack awslocal s3 cp s3://ecommerce-order-exports/orders/<orderNumber>.json -
docker exec ecommerce-localstack awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/ecommerce-order-audit
```

## Git-ignored files

`.terraform/`, `*.tfstate*`, and `.terraform.lock.hcl` are git-ignored so that generated
Terraform state files are never committed.
