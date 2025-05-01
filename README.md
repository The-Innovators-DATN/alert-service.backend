# AquaTech Alert System

A real-time water quality monitoring tool that processes sensor data and sends notifications when values exceed
predefined thresholds.

## Table of Contents

- [Overview](#overview)
- [System Components](#system-components)
- [Workflow](#workflow)
- [Supported Condition Types](#supported-condition-types)
- [System Setup](#system-setup)
    - [Requirements](#requirements)
    - [Database Setup](#database-setup)
    - [Configuration](#configuration)
- [Deployment on Ubuntu (Production)](#deployment-on-ubuntu-production)
- [API Documentation](#api-documentation)
    - [Response Format](#response-format)
    - [HTTP Status Codes](#http-status-codes)
    - [Endpoints](#endpoints)
- [Data Models](#data-models)
- [Redis Structure](#redis-structure)
- [Source Code Structure](#source-code-structure)
- [Development Tips](#development-tips)
- [Future Improvements](#future-improvements)

## Overview

The AquaTech Alert System is designed for water quality stations to monitor sensor data such as temperature and pH. It
can notify users when values exceed predefined thresholds (e.g., temperature exceeds 30°C or pH drops below 6). The
system leverages REST API, Kafka, Redis, and PostgreSQL for efficient operation.

## System Components

| Component      | Description                             |
|----------------|-----------------------------------------|
| REST API       | Manages alerts via HTTP endpoints       |
| Kafka Consumer | Receives real-time sensor data          |
| Redis Cache    | Stores alert conditions for fast lookup |
| Kafka Producer | Sends alert notifications               |
| PostgreSQL     | Stores alert configurations             |

## Workflow

1. **Alert Creation**:
    - Users create alerts via the API (e.g., temperature > 30°C)
    - Alerts are stored in PostgreSQL and cached in Redis

2. **Sensor Data Processing**:
    - Sensor data arrives via the Kafka topic `sensor_data`
    - The system checks Redis for matching alert conditions
    - If conditions are met, a notification is sent via Kafka to the `alert_notification` topic

3. **Alert Status**:
    - Alerts trigger with status `alert` when conditions are met
    - When values normalize, a `resolved` notification is sent

## Supported Condition Types

| Operator      | Description           |
|---------------|-----------------------|
| EQ            | Equal                 |
| NEQ           | Not equal             |
| GT            | Greater than          |
| GTE           | Greater than or equal |
| LT            | Less than             |
| LTE           | Less than or equal    |
| RANGE         | Within a range        |
| OUTSIDE_RANGE | Outside a range       |

## System Setup

### Requirements

| Tool       | Version  | Purpose                 |
|------------|----------|-------------------------|
| Java       | 21       | Runs the application    |
| Maven      | 3.4+     | Builds the application  |
| PostgreSQL | Latest   | Stores alert data       |
| Redis      | Latest   | Caches alert conditions |
| Kafka      | Existing | Streams sensor data     |

### Database Setup

Run this SQL script to set up the `alert_dev` database and `alert` table:

```sql
-- Create the alert_dev database if it doesn't exist
CREATE
DATABASE alert_dev;

-- Connect to the alert_dev database
\c
alert_dev

-- Create the alert table
CREATE TABLE alert
(
    uid        UUID PRIMARY KEY,
    user_id    INTEGER      NOT NULL,
    name       VARCHAR(255) NOT NULL,
    station_id INTEGER      NOT NULL,
    message    TEXT,
    conditions TEXT,
    silenced   INTEGER DEFAULT 0,
    status     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

-- Add indexes
CREATE INDEX idx_alert_user_id ON alert (user_id);
CREATE INDEX idx_alert_station_id ON alert (station_id);
CREATE INDEX idx_alert_status ON alert (status);
```

Execute the script:

```bash
psql -U your_username -f init-db.sql
```

Replace `your_username` with your PostgreSQL username.

### Configuration

1. Create a `.env` file in the project root with these settings:

```
DB_HOST=your_db_host
DB_PORT=5432
DB_NAME=alert_dev
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
REDIS_HOST=your_redis_host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
KAFKA_BOOTSTRAP_SERVERS=your_kafka_server:9092
```

2. Update `application.yml` to use these variables:

```yaml
spring:
  application:
    name: alert
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
    hibernate:
      ddl-auto: update
    open-in-view: false
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: clickhuse-consumer
      auto-offset-reset: latest
server:
  port: 3001
  servlet:
    context-path: /api/v0
kafka:
  alert-topic: sensor_data
  message-topic: alert_notification
```

3. Add `.env` to `.gitignore`:

```
.env
```

## Deployment on Ubuntu (Production)

### 1. Install Tools

Update the system and install dependencies:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jdk maven postgresql redis-server
```

Verify installations:

```bash
java -version
mvn -version
psql --version
redis-cli --version
```

### 2. Configure PostgreSQL

Set up user and database:

```bash
sudo -u postgres psql -c "CREATE USER your_username WITH PASSWORD 'your_db_password';"
sudo -u postgres psql -f init-db.sql
```

Adjust `your_username` and `your_db_password` to match your `.env`.

### 3. Configure Redis

Start Redis:

```bash
sudo systemctl enable redis
sudo systemctl start redis
```

Verify Redis:

```bash
redis-cli ping
```

Should return `PONG`.

### 4. Clone the Repository

Clone and navigate to the project:

```bash
git clone <your-repository-url>
cd alert-system
```

### 5. Configure Environment

Create and secure the `.env` file:

```bash
chmod 600 .env
```

### 6. Build and Run

Build the application:

```bash
mvn clean package
```

Run it in the background:

```bash
export $(cat .env | xargs) && nohup java -jar target/alert-0.0.1-SNAPSHOT.jar &
```

Check logs:

```bash
tail -f nohup.out
```

API will be available at `http://<your-server-ip>:3001/api/v0`.

### 7. Set Up as a Systemd Service (Optional)

Create a service file:

```bash
sudo nano /etc/systemd/system/alert-system.service
```

Add this content:

```
[Unit]
Description=AquaTech Alert System
After=network.target

[Service]
Type=simple
ExecStart=/bin/bash -c 'export $(cat /path/to/alert-system/.env | xargs) && java -jar /path/to/alert-system/target/alert-0.0.1-SNAPSHOT.jar'
WorkingDirectory=/path/to/alert-system
Restart=always
User=your_username

[Install]
WantedBy=multi-user.target
```

Replace `/path/to/alert-system` and `your_username` as needed.

Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable alert-system
sudo systemctl start alert-system
```

Check status:

```bash
sudo systemctl status alert-system
```

## API Documentation

The API is hosted at `/api/v0/alert` and returns JSON. Authentication is not required but recommended for production (
e.g., JWT/OAuth).

### Response Format

**Success**:

```json
{
  "success": true,
  "message": "string",
  "data": {
    /* response data */
  }
}
```

**Error**:

```json
{
  "success": false,
  "message": "string",
  "errorCode": "int",
  "errors": {
    /* error details */
  }
}
```

### HTTP Status Codes

| Code | Meaning              |
|------|----------------------|
| 200  | Success              |
| 201  | Created successfully |
| 400  | Invalid input        |
| 404  | Resource not found   |
| 500  | Server error         |

### Endpoints

| Endpoint                                  | Method | Description         |
|-------------------------------------------|--------|---------------------|
| `/api/v0/alert/create/{userId}`           | POST   | Create a new alert  |
| `/api/v0/alert/get/{userId}`              | GET    | Get user's alerts   |
| `/api/v0/alert/update/{alertId}`          | PUT    | Update an alert     |
| `/api/v0/alert/delete/{alertId}`          | DELETE | Delete an alert     |
| `/api/v0/alert/{alertId}/status/{status}` | PUT    | Update alert status |

#### 1. Create Alert

- **Method**: POST
- **Path**: `/api/v0/alert/create/{userId}`
- **Description**: Creates a new alert for a user
- **Path Parameter**: `userId` (integer)
- **Request Body**: Alert object (see Data Models)
- **Response Codes**: 201, 400, 500

**Example Request**:

```json
{
  "name": "High Temperature Alert",
  "stationId": 1,
  "message": "Temperature exceeds threshold!",
  "conditions": [
    {
      "metricId": 101,
      "metricName": "temperature",
      "threshold": 30.0,
      "operator": "GT",
      "severity": 2
    }
  ],
  "silenced": 0,
  "status": "active"
}
```

**Example Response (201)**:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "uid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "High Temperature Alert",
    "stationId": 1,
    "message": "Temperature exceeds threshold!",
    "conditions": [
      {
        "uid": "123e4567-e89b-12d3-a456-426614174001",
        "metricId": 101,
        "metricName": "temperature",
        "threshold": 30.0,
        "operator": "GT",
        "severity": 2
      }
    ],
    "silenced": 0,
    "status": "active",
    "createdAt": "2025-04-19T10:00:00",
    "updatedAt": "2025-04-19T10:00:00"
  }
}
```

#### 2. Get Alerts

- **Method**: GET
- **Path**: `/api/v0/alert/get/user/{userId}`
- **Description**: Retrieves all alerts for a user
- **Path Parameter**: `userId` (integer)
- **Response Codes**: 200, 400, 500

**Example Response (200)**:

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "uid": "123e4567-e89b-12d3-a456-426614174000",
      "name": "High Temperature Alert",
      "stationId": 1,
      "message": "Temperature exceeds threshold!",
      "conditions": [
        {
          "uid": "123e4567-e89b-12d3-a456-426614174001",
          "metricId": 101,
          "metricName": "temperature",
          "threshold": 30.0,
          "operator": "GT",
          "severity": 2
        }
      ],
      "silenced": 0,
      "status": "active",
      "createdAt": "2025-04-19T10:00:00",
      "updatedAt": "2025-04-19T10:00:00"
    }
  ]
}
```

#### 3. Update Alert

- **Method**: PUT
- **Path**: `/api/v0/alert/update/{alertId}`
- **Description**: Updates an existing alert
- **Path Parameter**: `alertId` (UUID string)
- **Request Body**: Updated alert object
- **Response Codes**: 200, 400, 404, 500

**Example Request**:

```json
{
  "name": "Updated Temperature Alert",
  "stationId": 1,
  "message": "Temperature exceeds new threshold!",
  "conditions": [
    {
      "metricId": 101,
      "metricName": "temperature",
      "threshold": 35.0,
      "operator": "GT",
      "severity": 3
    }
  ],
  "silenced": 0,
  "status": "active"
}
```

**Example Response (200)**:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "uid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "Updated Temperature Alert",
    "stationId": 1,
    "message": "Temperature exceeds new threshold!",
    "conditions": [
      {
        "uid": "123e4567-e89b-12d3-a456-426614174002",
        "metricId": 101,
        "metricName": "temperature",
        "threshold": 35.0,
        "operator": "GT",
        "severity": 3
      }
    ],
    "silenced": 0,
    "status": "active",
    "createdAt": "2025-04-19T10:00:00",
    "updatedAt": "2025-04-19T11:00:00"
  }
}
```

#### 4. Delete Alert

- **Method**: DELETE
- **Path**: `/api/v0/alert/delete/{alertId}`
- **Description**: Deletes an alert
- **Path Parameter**: `alertId` (UUID string)
- **Response Codes**: 200, 400, 404, 500

**Example Response (200)**:

```json
{
  "success": true,
  "message": "Alert deleted successfully"
}
```

#### 5. Update Alert Status

- **Method**: PUT
- **Path**: `/api/v0/alert/{alertId}/status/{status}`
- **Description**: Updates an alert's status (e.g., "active", "resolved")
- **Path Parameters**: `alertId` (UUID string), `status` (string)
- **Response Codes**: 200, 400, 404, 500

**Example Response (200)**:

```json
{
  "success": true,
  "message": "Status updated successfully",
  "data": {
    "uid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "High Temperature Alert",
    "stationId": 1,
    "message": "Temperature exceeds threshold!",
    "conditions": [
      {
        "uid": "123e4567-e89b-12d3-a456-426614174001",
        "metricId": 101,
        "metricName": "temperature",
        "threshold": 30.0,
        "operator": "GT",
        "severity": 2
      }
    ],
    "silenced": 0,
    "status": "resolved",
    "createdAt": "2025-04-19T10:00:00",
    "updatedAt": "2025-04-19T12:00:00"
  }
}
```

## Data Models

### Alert

```json
{
  "uid": "string",
  "userId": "integer",
  "name": "string",
  "stationId": "integer",
  "message": "string",
  "conditions": [
    {
      "metricId": "integer",
      "metricName": "string",
      "threshold": "number",
      "operator": "string",
      "severity": "integer"
    }
  ],
  "silenced": "integer",
  "status": "string",
  "createdAt": "string",
  "updatedAt": "string"
}
```

For create/update, omit `uid`, `createdAt`, `updatedAt`.

**conditions**: Array of condition objects:

- `metricId`: Metric ID
- `metricName`: Metric name (e.g., "temperature")
- `threshold`: Threshold value
- `operator`: Comparison operator (e.g., "GT")
- `severity`: Severity level (integer)

### Sensor Data

```json
{
  "value": 32.5,
  "sensorId": 101,
  "metric": "temperature",
  "stationId": 1,
  "datetime": "2025-04-19 10:00:00",
  "unit": "C"
}
```

## Redis Structure

Redis stores alert conditions with this key format:
`station:{stationId}:alert:{alertId}:metric:{metricId}:condition:{conditionId}`

| Example Key                                          | Description                           |
|------------------------------------------------------|---------------------------------------|
| `station:1:alert:a1b2c3:metric:101:condition:d4e5f6` | Condition for metric 101 at station 1 |

Each key holds JSON data about the alert condition.

## Source Code Structure

| Package      | Purpose                       |
|--------------|-------------------------------|
| `controller` | Handles REST API endpoints    |
| `service`    | Business logic                |
| `model`      | Data objects                  |
| `repository` | PostgreSQL data access        |
| `config`     | Configurations (Redis, Kafka) |
| `constant`   | System constants              |
| `utils`      | Helper utilities              |

## Development Tips

- Check logs in `logs/alert.log` for debugging
- Use Redis CLI to inspect cached data
- Test APIs with Postman or curl

## Future Improvements

- Add JWT/OAuth for API security
- Implement rate limiting
- Add pagination for the get alerts endpoint
- Provide OpenAPI/Swagger documentation