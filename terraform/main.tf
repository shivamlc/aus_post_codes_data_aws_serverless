provider "aws" {
  region = var.aws_region
}

# S3 bucket for CSV uploads
resource "aws_s3_bucket" "postcode_csv" {
  bucket = "${var.project_name}-csv-${var.environment}"
}

# DynamoDB table
resource "aws_dynamodb_table" "postcodes" {
  name         = "${var.project_name}-postcodes"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"

  attribute {
    name = "PK"
    type = "S"
  }

  # GSI to query all postcodes in a state
  global_secondary_index {
    name            = "STATE-index"
    hash_key        = "STATE"
    projection_type = "ALL"
  }

  attribute {
    name = "STATE"
    type = "S"
  }
}

# Lambda function
resource "aws_lambda_function" "postcode_processor" {
  function_name = "${var.project_name}-processor"
  role          = aws_iam_role.lambda_role.arn
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
  runtime       = "provided.al2"   # GraalVM native
  filename      = "../target/postcode-distance.zip"
  timeout       = 900  # 15 min for large CSVs
  memory_size   = 512

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "postcodeHandler"
      DYNAMODB_TABLE                   = aws_dynamodb_table.postcodes.name
    }
  }
}

# S3 → Lambda trigger
resource "aws_s3_bucket_notification" "csv_trigger" {
  bucket = aws_s3_bucket.postcode_csv.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.postcode_processor.arn
    events              = ["s3:ObjectCreated:*"]
    filter_suffix       = ".csv"
  }
}

resource "aws_lambda_permission" "allow_s3" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.postcode_processor.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.postcode_csv.arn
}