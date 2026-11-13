resource "aws_sns_topic" "alerts" { name = "${var.project_name}-alerts" }
resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}
resource "aws_sqs_queue" "failures" {
  name                      = "${var.project_name}-failed-events"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}
resource "aws_iam_role" "worker" {
  name               = "${var.project_name}-worker"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "lambda.amazonaws.com" } }] })
}
resource "aws_cloudwatch_log_group" "worker" {
  name              = "/aws/lambda/${var.project_name}-worker"
  retention_in_days = 14
}
resource "aws_iam_role_policy" "worker" {
  role = aws_iam_role.worker.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.worker.arn}:*" },
    { Effect = "Allow", Action = ["s3:ListBucket"], Resource = [aws_s3_bucket.source.arn], Condition = { StringLike = { "s3:prefix" = ["application/*", "tenants/*/application/*"] } } },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject"], Resource = ["${aws_s3_bucket.source.arn}/application/*", "${aws_s3_bucket.source.arn}/tenants/*/application/*"] },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject"], Resource = ["${aws_s3_bucket.primary.arn}/backups/*", "${aws_s3_bucket.secondary.arn}/backups/*", "${aws_s3_bucket.primary.arn}/tenants/*/backups/*", "${aws_s3_bucket.secondary.arn}/tenants/*/backups/*"] },
    { Effect = "Allow", Action = ["s3:ListBucket"], Resource = [aws_s3_bucket.recovery.arn], Condition = { StringLike = { "s3:prefix" = ["tests/*", "restores/*", "tenants/*/tests/*", "tenants/*/restores/*"] } } },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:PutObjectTagging", "s3:DeleteObject"], Resource = ["${aws_s3_bucket.recovery.arn}/tests/*", "${aws_s3_bucket.recovery.arn}/restores/*", "${aws_s3_bucket.recovery.arn}/tenants/*/tests/*", "${aws_s3_bucket.recovery.arn}/tenants/*/restores/*"] },
    { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem", "dynamodb:Query"], Resource = aws_dynamodb_table.state.arn, Condition = { "ForAllValues:StringLike" = { "dynamodb:LeadingKeys" = ["CLOUDSHIELD", "TENANT#*"] } } },
    { Effect = "Allow", Action = ["lambda:InvokeFunction"], Resource = "arn:${data.aws_partition.current.partition}:lambda:${var.primary_region}:${data.aws_caller_identity.current.account_id}:function:${var.project_name}-worker" },
    { Effect = "Allow", Action = ["sns:Publish"], Resource = aws_sns_topic.alerts.arn },
    { Effect = "Allow", Action = ["sqs:SendMessage"], Resource = aws_sqs_queue.failures.arn }
  ] })
}
resource "aws_lambda_function" "worker" {
  function_name                  = "${var.project_name}-worker"
  role                           = aws_iam_role.worker.arn
  runtime                        = "java21"
  handler                        = "edu.gla.cloudshield.worker.Handler::handleRequest"
  filename                       = var.worker_jar
  source_code_hash               = filebase64sha256(var.worker_jar)
  timeout                        = 900
  memory_size                    = 1024
  reserved_concurrent_executions = 1
  dead_letter_config { target_arn = aws_sqs_queue.failures.arn }
  environment {
    variables = {
      CLOUDSHIELD_MODE = "aws"
      SECONDARY_REGION = var.secondary_region
      SOURCE_BUCKET    = aws_s3_bucket.source.id
      PRIMARY_BUCKET   = aws_s3_bucket.primary.id
      SECONDARY_BUCKET = aws_s3_bucket.secondary.id
      RECOVERY_BUCKET  = aws_s3_bucket.recovery.id
      STATE_TABLE      = aws_dynamodb_table.state.name
      SNS_TOPIC_ARN    = aws_sns_topic.alerts.arn
      MAX_OBJECTS      = "500"
      MAX_BYTES        = "20971520"
    }
  }
  depends_on = [aws_iam_role_policy.worker, aws_s3_bucket_versioning.primary, aws_s3_bucket_versioning.secondary]
}
resource "aws_lambda_function_event_invoke_config" "worker" {
  function_name                = aws_lambda_function.worker.function_name
  maximum_event_age_in_seconds = 3600
  maximum_retry_attempts       = 2
}
resource "aws_iam_role" "scheduler" {
  name               = "${var.project_name}-scheduler"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "scheduler.amazonaws.com" }, Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id } } }] })
}
resource "aws_iam_role_policy" "scheduler" {
  role = aws_iam_role.scheduler.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = "lambda:InvokeFunction", Resource = aws_lambda_function.worker.arn },
    { Effect = "Allow", Action = "sqs:SendMessage", Resource = aws_sqs_queue.failures.arn }
  ] })
}
resource "aws_scheduler_schedule" "jobs" {
  for_each                     = { backup = { expression = "cron(0 2 * * ? *)", type = "BACKUP" }, test = { expression = "cron(30 2 ? * SUN *)", type = "TEST" } }
  name                         = "${var.project_name}-${each.key}"
  schedule_expression          = each.value.expression
  schedule_expression_timezone = "Asia/Kolkata"
  state                        = var.enable_schedules ? "ENABLED" : "DISABLED"
  flexible_time_window { mode = "OFF" }
  target {
    arn      = aws_lambda_function.worker.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ type = each.value.type, scheduleId = "<aws.scheduler.scheduled-time>" })
    dead_letter_config { arn = aws_sqs_queue.failures.arn }
    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 2
    }
  }
  depends_on = [aws_iam_role_policy.scheduler]
}
resource "aws_cloudwatch_metric_alarm" "worker_errors" {
  alarm_name          = "${var.project_name}-worker-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  dimensions          = { FunctionName = aws_lambda_function.worker.function_name }
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
resource "aws_cloudwatch_metric_alarm" "failed_events" {
  alarm_name          = "${var.project_name}-undelivered-events"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  dimensions          = { QueueName = aws_sqs_queue.failures.name }
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
