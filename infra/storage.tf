locals {
  suffix = "${var.project_name}-${data.aws_caller_identity.current.account_id}"
}
resource "aws_s3_bucket" "source" { bucket = "${local.suffix}-source" }
resource "aws_s3_bucket" "primary" { bucket = "${local.suffix}-primary" }
resource "aws_s3_bucket" "recovery" { bucket = "${local.suffix}-recovery" }
resource "aws_s3_bucket" "secondary" {
  provider = aws.secondary
  bucket   = "${local.suffix}-secondary"
  lifecycle {
    precondition {
      condition     = var.primary_region != var.secondary_region
      error_message = "Backup copies must use two different regions."
    }
  }
}
locals {
  primary_buckets = { source = aws_s3_bucket.source.id, primary = aws_s3_bucket.primary.id, recovery = aws_s3_bucket.recovery.id }
}
resource "aws_s3_bucket_public_access_block" "primary" {
  for_each                = local.primary_buckets
  bucket                  = each.value
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_public_access_block" "secondary" {
  provider                = aws.secondary
  bucket                  = aws_s3_bucket.secondary.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_server_side_encryption_configuration" "primary" {
  for_each = local.primary_buckets
  bucket   = each.value
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "secondary" {
  provider = aws.secondary
  bucket   = aws_s3_bucket.secondary.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}
resource "aws_s3_bucket_versioning" "primary" {
  for_each = { source = aws_s3_bucket.source.id, primary = aws_s3_bucket.primary.id }
  bucket   = each.value
  versioning_configuration { status = "Enabled" }
}
resource "aws_s3_bucket_versioning" "secondary" {
  provider = aws.secondary
  bucket   = aws_s3_bucket.secondary.id
  versioning_configuration { status = "Enabled" }
}
# Recovery bucket intentionally has no versioning: cleanup removes actual data.
resource "aws_s3_bucket_lifecycle_configuration" "recovery" {
  bucket = aws_s3_bucket.recovery.id
  rule {
    id     = "test-timeout-safety-net"
    status = "Enabled"
    filter {
      tag {
        key   = "cloudshield-temporary"
        value = "true"
      }
    }
    expiration { days = 1 }
  }
}
resource "aws_s3_bucket_policy" "primary_tls" {
  for_each = local.primary_buckets
  bucket   = each.value
  policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Sid = "DenyInsecureTransport", Effect = "Deny", Principal = "*", Action = "s3:*", Resource = ["arn:${data.aws_partition.current.partition}:s3:::${each.value}", "arn:${data.aws_partition.current.partition}:s3:::${each.value}/*"], Condition = { Bool = { "aws:SecureTransport" = "false" } } }] })
}
resource "aws_s3_bucket_policy" "secondary_tls" {
  provider = aws.secondary
  bucket   = aws_s3_bucket.secondary.id
  policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Sid = "DenyInsecureTransport", Effect = "Deny", Principal = "*", Action = "s3:*", Resource = [aws_s3_bucket.secondary.arn, "${aws_s3_bucket.secondary.arn}/*"], Condition = { Bool = { "aws:SecureTransport" = "false" } } }] })
}
resource "aws_dynamodb_table" "state" {
  name         = "${var.project_name}-state"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"
  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }
  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }
}
