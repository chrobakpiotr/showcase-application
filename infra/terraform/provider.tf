terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# Provider configured for LocalStack. The dummy credentials and the skip_* flags are
# LocalStack conventions - they are NOT used against real AWS.
provider "aws" {
  region                      = var.aws_region
  access_key                  = "test"
  secret_key                  = "test"
  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    s3             = var.localstack_endpoint
    sqs            = var.localstack_endpoint
    secretsmanager = var.localstack_endpoint
  }
}
