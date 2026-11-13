resource "aws_iam_policy" "dashboard" {
  name = "${var.project_name}-dashboard"
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query"], Resource = aws_dynamodb_table.state.arn, Condition = { "ForAllValues:StringLike" = { "dynamodb:LeadingKeys" = ["CLOUDSHIELD", "TENANT#*"] } } },
    { Effect = "Allow", Action = "lambda:InvokeFunction", Resource = aws_lambda_function.worker.arn },
    { Effect = "Allow", Action = ["s3:GetObject"], Resource = ["${aws_s3_bucket.source.arn}/application/*", "${aws_s3_bucket.recovery.arn}/restores/*", "${aws_s3_bucket.source.arn}/tenants/*/application/*", "${aws_s3_bucket.recovery.arn}/tenants/*/restores/*"] }
  ] })
}
output "dashboard_policy_arn" { value = aws_iam_policy.dashboard.arn }
output "dashboard_environment" {
  value = {
    CLOUDSHIELD_MODE = "aws"
    AWS_REGION       = var.primary_region
    SECONDARY_REGION = var.secondary_region
    SOURCE_BUCKET    = aws_s3_bucket.source.id
    PRIMARY_BUCKET   = aws_s3_bucket.primary.id
    SECONDARY_BUCKET = aws_s3_bucket.secondary.id
    RECOVERY_BUCKET  = aws_s3_bucket.recovery.id
    STATE_TABLE      = aws_dynamodb_table.state.name
    SNS_TOPIC_ARN    = aws_sns_topic.alerts.arn
    WORKER_FUNCTION  = aws_lambda_function.worker.function_name
  }
}
output "source_bucket" { value = aws_s3_bucket.source.id }
output "secondary_bucket" { value = aws_s3_bucket.secondary.id }
output "recovery_bucket" { value = aws_s3_bucket.recovery.id }
output "worker_function" { value = aws_lambda_function.worker.function_name }
output "failed_events_queue_url" { value = aws_sqs_queue.failures.url }
