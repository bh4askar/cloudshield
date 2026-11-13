variable "project_name" {
  type    = string
  default = "cloudshield-g360"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,24}$", var.project_name))
    error_message = "Use 3-25 lowercase letters, digits or hyphens."
  }
}
variable "primary_region" {
  type    = string
  default = "ap-south-1"
}
variable "secondary_region" {
  type    = string
  default = "ap-southeast-1"
}
variable "alert_email" {
  type        = string
  description = "Email address to subscribe to backup, test and infrastructure alerts. Confirm the SNS subscription email."
}
variable "enable_schedules" {
  type    = bool
  default = true
}
variable "worker_jar" {
  type    = string
  default = "../worker/target/cloudshield-worker-1.0.0.jar"
}
