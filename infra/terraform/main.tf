# S3 bucket for per-order JSON exports.
# Maps to: aws.s3.bucket-name in application-aws-localstack.yml
resource "aws_s3_bucket" "order_exports" {
  bucket = var.s3_bucket_name
}

# SQS queue for lightweight order-audit events.
# Maps to: aws.sqs.queue-url in application-aws-localstack.yml
resource "aws_sqs_queue" "order_audit" {
  name = var.sqs_queue_name
}

# Secrets Manager secret that holds the datasource credentials.
# Maps to: aws.secretsmanager.secret-name in application-aws-localstack.yml
resource "aws_secretsmanager_secret" "db_credentials" {
  name = var.db_secret_name
}

# Actual credential payload: {"username": "...", "password": "..."}
# Must match the Postgres service credentials in docker-compose.yml
resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id     = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({ username = var.db_username, password = var.db_password })
}
