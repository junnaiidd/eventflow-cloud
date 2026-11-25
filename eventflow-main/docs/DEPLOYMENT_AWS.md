# Deploying EventFlow on AWS (EC2 + RDS MySQL + S3)

> **Status:** these instructions and the `prod` profile were written but **not tested against a real AWS
> account** (no AWS access was available while developing). The S3 and MySQL code paths are covered by unit
> tests with mocks only. Follow the "Verify" section after your first deployment.

## Architecture

```
Browser --HTTP(S)--> nginx (EC2, port 80/443) --> Spring Boot :8080 --JDBC/TLS--> RDS MySQL (private)
                                                         |
                                                         +--AWS SDK (instance role)--> S3 bucket (private)
```

The browser never talks to RDS or S3 and never sees AWS credentials: posters and certificates are streamed
through the application after it has checked the user's permission.

## 1. Create the AWS resources

1. **S3 bucket** (e.g. `eventflow-files-<yourname>`): region of your choice, *Block all public access* = ON, default encryption ON.
2. **IAM role for EC2** (e.g. `eventflow-ec2-role`) with this inline policy (replace the bucket name):
   ```json
   { "Version": "2012-10-17", "Statement": [
     { "Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
       "Resource": "arn:aws:s3:::eventflow-files-<yourname>/*" } ] }
   ```
3. **RDS MySQL 8** instance (db.t3.micro is enough): *Public access = No*, initial database name `eventflow`,
   create a master user and password. Put it in the same VPC as the EC2 instance.
4. **Security groups**
   - EC2 group: inbound 22 (your IP only) and 80 (and 443 if you add HTTPS).
   - RDS group: inbound **3306 from the EC2 security group only**.
5. **EC2 instance** (Amazon Linux 2023, t3.micro/small) with the IAM role from step 2 attached.

## 2. Prepare the server

```bash
sudo dnf install -y java-21-amazon-corretto-headless nginx
sudo mkdir -p /opt/eventflow && sudo chown ec2-user /opt/eventflow
```

Create `/etc/eventflow.env` from `.env.example` (`sudo chmod 600 /etc/eventflow.env`) and fill in:
`DB_HOST` (the RDS endpoint), `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `S3_BUCKET`, `AWS_REGION`,
`ADMIN_EMAIL`, `ADMIN_PASSWORD`. AWS credentials are **not** needed because the instance role provides them.

## 3. Build and upload

On your machine:
```bash
mvn clean package            # runs the tests, produces target/eventflow-0.0.1-SNAPSHOT.jar
scp target/eventflow-0.0.1-SNAPSHOT.jar ec2-user@<EC2-IP>:/opt/eventflow/eventflow.jar
```

## 4. Run it as a service

```bash
sudo cp deploy/eventflow.service /etc/systemd/system/
sudo cp deploy/nginx-eventflow.conf /etc/nginx/conf.d/eventflow.conf
sudo systemctl daemon-reload
sudo systemctl enable --now eventflow nginx
journalctl -u eventflow -f          # watch the start-up log
```

On first start Hibernate creates the tables in RDS (`DDL_AUTO=update`, additive only) and the admin account
from `ADMIN_EMAIL` / `ADMIN_PASSWORD` is created. Open `http://<EC2-public-IP>/`, sign in as that admin, and
create organizer accounts on the **Users** page.

## How the application connects to the remote database

`application-prod.properties` builds the JDBC URL from environment variables:
`jdbc:mysql://${DB_HOST}:${DB_PORT:3306}/${DB_NAME}?sslMode=REQUIRED...` with `DB_USERNAME` / `DB_PASSWORD`.
Only the EC2 security group can reach port 3306. If a variable is missing the application refuses to start.

## Environment variables

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | yes | enables MySQL + S3 configuration |
| `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | yes | RDS connection (`DB_PORT` default 3306, `DB_SSL_MODE` default REQUIRED) |
| `S3_BUCKET`, `AWS_REGION` | yes | bucket for posters and certificate PDFs |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | only without an instance role | AWS SDK default credential chain |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | recommended | first admin account (created if missing) |
| `COOKIE_SECURE` | optional | `true` once served over HTTPS |
| `DDL_AUTO` | optional | `update` (default) or `validate` if you applied `docs/migration-mysql.sql` yourself |
| `PORT` | optional | default 8080 |

## Database schema changes and migration

New tables: `app_users`, `registrations`, `certificates`. New nullable columns on `events`: `capacity`,
`organizer_id`, `poster_key`. Nothing is dropped or renamed. With `DDL_AUTO=update` Hibernate applies this
automatically; `docs/migration-mysql.sql` contains the same changes as plain SQL. The original project used an
in-memory H2 database, so there is no earlier production data to migrate.

## HTTPS and the camera scanner

Browsers only allow camera access on **HTTPS or localhost**. Over plain `http://<ip>` the QR scanner shows a
message and the organizer uses the manual ticket-ID field instead. For a real demo, put a domain in front
(Route 53 + an Application Load Balancer with an ACM certificate, or certbot on nginx) and set `COOKIE_SECURE=true`.

## Verify after deployment (checklist)

1. `curl http://<ip>/actuator/health` returns `{"status":"UP"}`.
2. Sign in as the admin, create an organizer, create an event with a poster.
3. In the AWS console, the poster appears under `posters/` in the bucket (confirms S3 write).
4. In a MySQL client: `SELECT COUNT(*) FROM events;` (confirms RDS).
5. Register as a participant, check in as the organizer, download the certificate (`certificates/` in S3).

## Cost note

Stop or terminate the EC2 and RDS instances and delete the bucket after your demo to avoid charges.
