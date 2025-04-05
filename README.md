# AquaTech Alert System

This is an alert system for monitoring data from water quality stations. The system receives real-time sensor data, analyzes metrics, and sends notifications when values exceed permitted thresholds.

## System Overview

The system consists of these main components:

- **REST API**: Allows alert management
- **Kafka Consumer**: Receives real-time sensor data
- **Redis Cache**: Stores alert conditions for fast processing
- **Kafka Producer**: Sends alert notifications
- **PostgreSQL**: Stores alert configurations

## System Workflow

### 1. Alert Creation and Management

1. Users create alerts through the REST API
2. Each alert can contain multiple conditions
3. Example: Alert when temperature > 30°C OR pH < 6
4. The system saves alerts to both PostgreSQL and Redis

### 2. Sensor Data Processing

1. Data from measuring stations is sent to the Kafka topic `sensor_data`
2. `KafkaService` receives and processes data through the `consumeSensorData` method
3. For each received data point, the system checks Redis for matching alert conditions
4. If the data meets an alert condition, the system creates a notification and sends it to Kafka

### 3. Alert Condition Evaluation

The system supports various condition types:
- **EQ**: Equal
- **NEQ**: Not equal
- **GT**: Greater than
- **GTE**: Greater than or equal
- **LT**: Less than
- **LTE**: Less than or equal
- **RANGE**: Within a range
- **OUTSIDE_RANGE**: Outside a range

### 4. Alert Status Management

- When an alert condition is detected, the system sends a notification with status `alert`
- The system tracks triggered alerts in Redis
- When values return to normal, the system sends a resolution notification with status `resolved`

## Source Code Structure

### Main Packages

- **controller**: Handles REST API
- **service**: Business logic
    - **AlertService**: Manages alerts
    - **CacheService**: Handles Redis cache
    - **KafkaService**: Processes Kafka data
    - **SyncService**: Synchronizes data
- **model**: Data objects
- **repository**: PostgreSQL data access
- **config**: Redis, Kafka configuration, etc.
- **constant**: System constants
- **utils**: Helper utilities

### Main Objects

#### Alert
```
- uid: Unique identifier
- name: Alert name
- userId: User ID
- stationId: Station ID
- message: Notification content
- silenced: Notification mute flag (0/1)
- status: Status (active, deleted)
- conditions: List of conditions
```

#### AlertCondition
```
- uid: Unique identifier
- metricId: Sensor ID
- metricName: Parameter name
- threshold: Single threshold
- thresholdMin: Lower threshold (for RANGE)
- thresholdMax: Upper threshold (for RANGE)
- operator: Comparison operator
- severity: Severity level
```

#### SensorData
```
- value: Measured value
- sensorId: Sensor ID
- metric: Parameter type
- stationId: Station ID
- datetime: Measurement time
- unit: Measurement unit
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/alert/create/{userId}` | POST | Create a new alert |
| `/api/v1/alert/get/{userId}` | GET | Get user's alerts |
| `/api/v1/alert/update/{alertId}` | PUT | Update alert |
| `/api/v1/alert/delete/{alertId}` | DELETE | Delete alert |
| `/api/v1/alert/{alertId}/status/{status}` | PUT | Update alert status |

## Detailed Data Flow

1. **System startup**:
    - `SyncService` synchronizes all active alerts to Redis
    - System is ready to receive data

2. **User creates an alert**:
    - API receives request and routes to `AlertController`
    - `AlertService` saves alert to database
    - `CacheService` updates Redis

3. **Sensor data**:
    - Data arrives at Kafka topic `sensor_data`
    - `KafkaService` receives and converts to `SensorData` object
    - `KafkaService.processSensorData()` queries Redis for matching alert conditions
    - `KafkaService.evaluateCondition()` evaluates conditions
    - If satisfied, calls `triggerAlert()` and sends notification via Kafka

4. **User views and manages alerts**:
    - Uses API to get, update, delete alerts
    - Each change updates both database and Redis

## Installation and Running the System

### Requirements
- Java 21
- Maven 3.4+
- PostgreSQL
- Redis
- Kafka

### Installation Steps

1. **Configure Redis**:
   ```bash
   docker run -d -p 6379:6379 --name redis redis
   ```

2. **Configure `application.yml`**:
    - Change PostgreSQL connection info
    - Change Redis connection info
    - Change Kafka connection info

3. **Build and run application**:
   ```bash
   ./mvnw clean package
   java -jar target/alert-0.0.1-SNAPSHOT.jar
   ```

## Usage Examples

### 1. Create a New Alert

```bash
curl -X POST "http://localhost:3001/api/v1/alert/create/123" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "High Temperature",
    "stationId": 456,
    "message": "Temperature exceeds allowed threshold",
    "silenced": 0,
    "status": "active",
    "conditions": [
      {
        "metricName": "temperature",
        "threshold": 30.0,
        "operator": "GT",
        "severity": 2
      }
    ]
  }'
```

### 2. Receive Sensor Data (Simulating Kafka delivery)

```json
{
  "value": 32.5,
  "sensor_id": 789,
  "metric": "temperature",
  "station_id": 456,
  "datetime": "2023-06-15 10:30:45",
  "unit": "°C"
}
```

### 3. Alert Processing

The system will:
- Detect value 32.5°C > 30.0°C
- Create alert and send to Kafka
- Save state in Redis

## Redis Structure Explanation

Redis stores alert conditions in the format:
```
station:{stationId}:alert:{alertId}:metric:{metricId}:condition:{conditionId}
```

Example:
```
station:456:alert:a1b2c3:metric:789:condition:d4e5f6
```

Each key stores JSON information about the alert condition and related information.

## Development Tips

1. Always check logs in `logs/alert.log` when experiencing issues
2. Use Redis CLI to view data in Redis
3. Use Kafka tools to check messages
4. APIs can be tested using Postman or curl