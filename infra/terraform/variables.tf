variable "aws_region" {
  description = "AWS region used for all resources."
  type        = string
  default     = "us-east-1"
}

variable "localstack_endpoint" {
  description = <<-EOT
    LocalStack endpoint URL.

    Default (http://localhost:4566) is correct when running Terraform directly on the host
    against a LocalStack container whose port 4566 is published to localhost.

    The docker-compose 'terraform' one-shot service overrides this to http://localstack:4566
    via the TF_VAR_localstack_endpoint environment variable, because that container reaches
    LocalStack over the internal Docker network by service name - the same dual-endpoint
    pattern used for Keycloak in application-docker.yml.
  EOT
  type        = string
  default     = "http://localhost:4566"
}

variable "s3_bucket_name" {
  description = "Name of the S3 bucket for order JSON exports."
  type        = string
  default     = "ecommerce-order-exports"
}

variable "sqs_queue_name" {
  description = "Name of the SQS queue for order audit events."
  type        = string
  default     = "ecommerce-order-audit"
}

variable "db_secret_name" {
  description = "Name / path of the Secrets Manager secret holding datasource credentials."
  type        = string
  default     = "ecommerce/db-credentials"
}

variable "db_username" {
  description = "Postgres username stored in the Secrets Manager secret (must match the postgres service in docker-compose.yml)."
  type        = string
  # Matches POSTGRES_USER in docker-compose.yml
  default = "sa"
}

variable "db_password" {
  description = "Postgres password stored in the Secrets Manager secret (must match the postgres service in docker-compose.yml)."
  type        = string
  sensitive   = true
  # Matches POSTGRES_PASSWORD in docker-compose.yml
  default = "sa"
}
