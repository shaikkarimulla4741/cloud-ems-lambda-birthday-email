# 🎉 Cloud-EMS Lambda: Birthday Email Service

This repository includes a scheduled AWS Lambda function that sends **birthday wishes** to employees as part of the **Cloud-EMS** project. It runs daily, checking for birthdays and sending emails via **AWS SES**.

---

## ⏱️ Lambda Trigger

This function only needs to be **triggered once per day** (e.g., via a CloudWatch cron job). Once triggered, it will:

- Connect to the RDS database
- Fetch all employees whose birthday is **today**
- Send them a cheerful birthday email via AWS SES
- Optionally mark them as "wished" (if you're maintaining a flag to avoid duplicates)

> ✅ No input or payload is required — just schedule it to run, and it handles everything automatically.

---

## ✨ Features

- Cron-scheduled using CloudWatch Events
- Sends cheerful birthday greetings 🎂
- Pulls employee birthday info from RDS (MySQL)
- Avoids duplicate sends using tracking flags

---

## 📦 Tech Stack

| Component      | Tech                        |
|----------------|-----------------------------|
| Language       | Java 17                     |
| Runtime        | AWS Lambda Java Runtime     |
| Email Service  | AWS SES                     |
| Trigger        | CloudWatch (Daily Schedule) |
| Database       | AWS RDS (MySQL)             |
| Packaging Tool | Maven                       |

---

## 🚀 Deployment

You can deploy this Lambda using the AWS Console, AWS CLI, or an Infrastructure-as-Code tool like **Terraform**, **CDK**, or **CloudFormation**.


### Steps

#### Step 1: Build the JAR

```bash
  mvn clean package
```

Find the generated JAR under `target/` (e.g. `cloud-ems-birthday-email-service-1.0.0.jar`).

#### Step 2: Create & Configure Lambda

1. **Create Function** in AWS Lambda Console

    * Runtime: `Java 17 (Corretto)`
    * Handler: `ems.lambda.EmployeeBirthdayLambda::handleRequest`

2. **Upload JAR**
   Under **Code** → “Upload from → .zip or JAR file”.

3. **Set Environment Variables**
   Under **Configuration → Environment variables**.

4. **Add CloudWatch Rule**
   Set a daily cron schedule (e.g. `cron(0 6 * * ? *)` for 6 AM UTC).

---

### ✅ IAM Permissions Required

* `ses:SendEmail`
* `rds-db:connect` (or allow access to MySQL)
* `logs:CreateLogGroup`, `logs:PutLogEvents`

---

### 🔐 Environment Variables

| Key                | Description               |
| ------------------ | ------------------------- |
| DB\_HOST           | RDS endpoint              |
| DB\_USER           | Database username         |
| DB\_PASSWORD       | Database password         |
| DB\_NAME           | Database name             |
| SES\_SENDER\_EMAIL | Verified SES sender email |

---

### 📬 Sample Email

* **Subject**: Happy Birthday, {{name}} 🎂
* **Body**: Wishing you a joyful birthday from the {COMPANY NAME} family!