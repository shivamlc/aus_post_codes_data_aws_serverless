variable "aws_region"    { default = "ap-southeast-2" }
variable "project_name"  { default = "postcode-distance" }
variable "environment"   { default = "prod" }
variable "github_oidc_provider_arn" {
  description = "ARN of the existing GitHub OIDC provider"
  type        = string
  sensitive   = true
}

variable "org" {
  description = "GitHub username or organisation name"
  type        = string
}

variable "repo" {
  description = "GitHub repository name"
  type        = string
}

variable "tf_state_bucket" {
  description = "S3 bucket name used for Terraform state"
  type        = string
}