# AllWage Clock-In Service - Starter Project

This is the starter project for the AllWage Senior Back-End Engineer technical assessment.

## Prerequisites

- Java 21+
- Maven 3.8+

## Getting Started

1. Build the project:
   ```bash
   mvn clean compile
   ```

2. Run the application:
   ```bash
   mvn spring-boot:run
   ```

3. Run tests:
   ```bash
   mvn test
   ```

The service will start on `http://localhost:8080`.

## Project Structure

```
src/main/java/com/allwage/clockin/
├── ClockInApplication.java    # Application entry point
├── controller/
│   ├── ClockController.java   # REST endpoints
│   └── ClockRequest.java      # Request DTO
├── model/
│   ├── ClockEvent.java        # Clock event record
│   └── Employee.java          # Employee record
├── service/
│   ├── ClockService.java      # Clock processing logic
│   ├── WhatsAppClient.java    # WhatsApp interface
│   └── WhatsAppClientStub.java # WhatsApp stub implementation
└── store/
    └── DocumentStore.java     # In-memory document store
```

## Provided Components

- **DocumentStore**: An in-memory NoSQL-style document store. Use this for all data persistence.
- **WhatsAppClient**: Interface for sending WhatsApp messages. The stub implementation logs messages.
- **ClockService**: Basic service for processing clock events. Extend this with your implementation.
- **ClockController**: Basic REST controller. Add endpoints as needed.

## Sample API Usage

Clock in:
```bash
curl -X POST http://localhost:8080/api/clocks \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "emp-123",
    "timestamp": "2024-01-15T09:00:00+02:00",
    "latitude": -26.2041,
    "longitude": 28.0473,
    "accuracyMeters": 10.0,
    "type": "IN"
  }'
```

Get all clock events:
```bash
curl http://localhost:8080/api/clocks
```

## Your Task

See the assessment document for full requirements. Write your spec in `SPEC.md` before implementing.
