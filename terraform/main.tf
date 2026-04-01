terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    # Values injected at runtime via -backend-config in GitHub Actions
    # terraform init -backend-config="bucket=..." -backend-config="key=..."
  }
}

provider "aws" {
  region = var.aws_region
}

# ================================================================
# S3 — CSV upload bucket
# ================================================================
resource "aws_s3_bucket" "postcode_csv" {
  bucket = "${var.project_name}-csv-${var.environment}"
}

resource "aws_s3_bucket_public_access_block" "postcode_csv" {
  bucket                  = aws_s3_bucket.postcode_csv.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# ================================================================
# DynamoDB
# ================================================================
resource "aws_dynamodb_table" "postcodes" {
  name         = "${var.project_name}-postcodes"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"

  attribute {
    name = "PK"
    type = "S"
  }

  attribute {
    name = "STATE"
    type = "S"
  }

  attribute {
    name = "POST_CODE"
    type = "S"
  }

  # Query all postcodes in a state → used by within-radius when state supplied
  global_secondary_index {
    name            = "STATE-index"
    hash_key        = "STATE"
    projection_type = "ALL"
  }

  # Query by postcode without knowing state → used by /location endpoint
  global_secondary_index {
    name            = "POSTCODE-index"
    hash_key        = "POST_CODE"
    projection_type = "ALL"
  }
}

# ================================================================
# Lambda — processor (S3 trigger, handles CSV ingestion)
# ================================================================
resource "aws_lambda_function" "postcode_processor" {
  function_name    = "${var.project_name}-processor"
  role             = aws_iam_role.lambda_role.arn
  runtime          = "provided.al2"    # required for GraalVM native binary
  handler          = "bootstrap"       # native runtime ignores this but must be set
  filename         = "../target/postcode-distance.zip"
  source_code_hash = filebase64sha256("../target/postcode-distance.zip")
  timeout          = 900               # 15 min — large CSVs need this
  memory_size      = 512

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "postcodeHandler"
      DYNAMODB_TABLE                   = aws_dynamodb_table.postcodes.name
    }
  }
}

resource "aws_s3_bucket_notification" "csv_trigger" {
  bucket = aws_s3_bucket.postcode_csv.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.postcode_processor.arn
    events              = ["s3:ObjectCreated:*"]
    filter_suffix       = ".csv"
  }

  depends_on = [aws_lambda_permission.allow_s3]
}

resource "aws_lambda_permission" "allow_s3" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.postcode_processor.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.postcode_csv.arn
}

# ================================================================
# Lambda — query API (API Gateway trigger, handles read endpoints)
# ================================================================
resource "aws_lambda_function" "postcode_query" {
  function_name    = "${var.project_name}-query"
  role             = aws_iam_role.lambda_role.arn
  runtime          = "provided.al2"
  handler          = "bootstrap"
  filename         = "../target/postcode-distance.zip"
  source_code_hash = filebase64sha256("../target/postcode-distance.zip")
  timeout          = 30
  memory_size      = 256

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "queryHandler"
      DYNAMODB_TABLE                   = aws_dynamodb_table.postcodes.name
    }
  }
}

resource "aws_lambda_permission" "allow_apigw" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.postcode_query.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.postcode_api.execution_arn}/*/*"
}

# ================================================================
# API Gateway — HTTP API
# ================================================================
resource "aws_apigatewayv2_api" "postcode_api" {
  name          = "${var.project_name}-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "query_lambda" {
  api_id                 = aws_apigatewayv2_api.postcode_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.postcode_query.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "location" {
  api_id    = aws_apigatewayv2_api.postcode_api.id
  route_key = "GET /postcodes/{postcode}/location"
  target    = "integrations/${aws_apigatewayv2_integration.query_lambda.id}"
}

resource "aws_apigatewayv2_route" "distance" {
  api_id    = aws_apigatewayv2_api.postcode_api.id
  route_key = "GET /postcodes/{postcode}/distance"
  target    = "integrations/${aws_apigatewayv2_integration.query_lambda.id}"
}

resource "aws_apigatewayv2_route" "within_radius" {
  api_id    = aws_apigatewayv2_api.postcode_api.id
  route_key = "GET /postcodes/within-radius"
  target    = "integrations/${aws_apigatewayv2_integration.query_lambda.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.postcode_api.id
  name        = "$default"
  auto_deploy = true
}

# ================================================================
# Outputs
# ================================================================
output "api_url" {
  description = "Base URL for all query endpoints"
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "csv_bucket_name" {
  description = "Upload your CSV here to trigger processing"
  value       = aws_s3_bucket.postcode_csv.bucket
}

output "github_actions_role_arn" {
  description = "Paste this into GitHub secret AWS_DEPLOY_ROLE_ARN"
  value       = aws_iam_role.github_actions.arn
}