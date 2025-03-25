# Alert Service Backend
Alert Evaluation Service is a backend application that processes sensor data from Kafka, evaluates alert conditions, and sends notification messages to Kafka for further processing. The service uses Redis for caching alerts to improve performance.

## Prerequisites
Java 21, Maven 3.4

## Run Redis by Docker
- `docker run -d -p 6379:6379 --name redis redis`
- `docker exec -it redis redis-cli`
- `AUTH password`
- `keys *`