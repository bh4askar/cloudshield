terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "5.100.0" }
  }
}
provider "aws" {
  region = var.primary_region
  default_tags { tags = { Project = "CloudShield", Group = "G360", ManagedBy = "Terraform" } }
}
provider "aws" {
  alias  = "secondary"
  region = var.secondary_region
  default_tags { tags = { Project = "CloudShield", Group = "G360", ManagedBy = "Terraform" } }
}
data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
