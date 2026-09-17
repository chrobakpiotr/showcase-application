output "s3_bucket_name" {
  description = "Name of the S3 bucket for order exports."
  value       = aws_s3_bucket.order_exports.bucket
}

output "sqs_queue_url" {
  description = "URL of the SQS queue for order audit events."
  value       = aws_sqs_queue.order_audit.url
}

output "db_secret_arn" {
  description = "ARN of the Secrets Manager secret holding datasource credentials."
  value       = aws_secretsmanager_secret.db_credentials.arn
}
