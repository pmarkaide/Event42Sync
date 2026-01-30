# AWS Resource Cleanup for Event42Sync

**IMPORTANT**: Only perform this cleanup AFTER verifying the Windows WSL setup is working correctly!

---

## Step 1: Identify Your Resources

Run these commands to find your specific resource names:

```bash
# Lambda functions
aws lambda list-functions --query 'Functions[?contains(FunctionName, `event42`) || contains(FunctionName, `Event42`)].FunctionName' --output table

# EventBridge rules
aws events list-rules --query 'Rules[*].Name' --output table

# S3 buckets (look for event42-related)
aws s3 ls | grep -i event

# CloudWatch log groups
aws logs describe-log-groups --query 'logGroups[?contains(logGroupName, `event42`) || contains(logGroupName, `Event42`)].logGroupName' --output table

# IAM roles
aws iam list-roles --query 'Roles[?contains(RoleName, `event42`) || contains(RoleName, `Event42`)].RoleName' --output table

# SSM parameters
aws ssm describe-parameters --query 'Parameters[?contains(Name, `event42`) || contains(Name, `Event42`)].Name' --output table
```

---

## Step 2: Download Database from S3 (if needed)

Before deleting S3, ensure you have the database:

```bash
# Replace YOUR_BUCKET_NAME with actual bucket name
aws s3 cp s3://YOUR_BUCKET_NAME/events.db ./events.db
```

---

## Step 3: Delete Resources (in order)

### 3.1 Delete EventBridge Rule

First, remove targets from the rule, then delete the rule:

```bash
# Replace RULE_NAME with your actual rule name (e.g., "Event42Sync-DailySchedule")

# List targets
aws events list-targets-by-rule --rule RULE_NAME

# Remove targets (usually ID "1")
aws events remove-targets --rule RULE_NAME --ids "1"

# Delete the rule
aws events delete-rule --rule RULE_NAME
```

### 3.2 Delete Lambda Functions

```bash
# Delete DailySyncHandler
aws lambda delete-function --function-name Event42Sync-DailySync

# Delete InitializationHandler (if exists)
aws lambda delete-function --function-name Event42Sync-Init

# Or whatever your function names are - check with:
aws lambda list-functions --query 'Functions[?contains(FunctionName, `Event42`)].FunctionName'
```

### 3.3 Delete CloudWatch Log Groups

```bash
# Delete log group for each Lambda function
aws logs delete-log-group --log-group-name /aws/lambda/Event42Sync-DailySync
aws logs delete-log-group --log-group-name /aws/lambda/Event42Sync-Init
```

### 3.4 Delete S3 Bucket

**WARNING**: Make sure you've downloaded the database first!

```bash
# Replace YOUR_BUCKET_NAME with actual bucket name

# Empty the bucket first
aws s3 rm s3://YOUR_BUCKET_NAME --recursive

# Delete the bucket
aws s3 rb s3://YOUR_BUCKET_NAME
```

### 3.5 Delete SSM Parameters

```bash
# List parameters first
aws ssm describe-parameters --query 'Parameters[?contains(Name, `event42`)].Name'

# Delete each parameter
aws ssm delete-parameter --name /event42sync/google-credentials
# (or whatever parameter names you have)
```

### 3.6 Delete IAM Role

```bash
# Replace ROLE_NAME with your actual role name

# First, list and detach managed policies
aws iam list-attached-role-policies --role-name ROLE_NAME
aws iam detach-role-policy --role-name ROLE_NAME --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
# Repeat for other attached policies

# List and delete inline policies
aws iam list-role-policies --role-name ROLE_NAME
aws iam delete-role-policy --role-name ROLE_NAME --policy-name POLICY_NAME
# Repeat for each inline policy

# Finally, delete the role
aws iam delete-role --role-name ROLE_NAME
```

---

## Step 4: Verify Cleanup

### Check All Resources Are Gone

```bash
# Should all return empty results
aws lambda list-functions --query 'Functions[?contains(FunctionName, `Event42`)].FunctionName'
aws events list-rules --query 'Rules[?contains(Name, `Event42`)].Name'
aws s3 ls | grep -i event42
aws logs describe-log-groups --query 'logGroups[?contains(logGroupName, `Event42`)].logGroupName'
aws iam list-roles --query 'Roles[?contains(RoleName, `Event42`)].RoleName'
aws ssm describe-parameters --query 'Parameters[?contains(Name, `event42`)].Name'
```

### Check AWS Billing

1. Go to AWS Console > Billing & Cost Management
2. Check "Bills" for current month
3. Verify no Event42Sync-related charges

---

## Cleanup Checklist

- [ ] Database downloaded from S3
- [ ] Windows WSL setup verified working
- [ ] EventBridge rule deleted
- [ ] Lambda function(s) deleted
- [ ] CloudWatch log groups deleted
- [ ] S3 bucket emptied and deleted
- [ ] SSM parameters deleted
- [ ] IAM role policies detached
- [ ] IAM role deleted
- [ ] Billing verified (no remaining charges)

---

## Rollback Plan

If you need to restore AWS infrastructure:

1. **Lambda handlers** are in `src/main/kotlin/com/Event42Sync/lambda.kt`:
   - `DailySyncHandler` - Daily sync
   - `InitializationHandler` - Full reinitialization

2. **EventBridge cron rule** for midnight:
   ```
   cron(0 0 * * ? *)
   ```

3. **S3 bucket** for database persistence

4. **SSM parameters** for Google credentials

The original infrastructure can be recreated using the AWS Console or IaC tools.
