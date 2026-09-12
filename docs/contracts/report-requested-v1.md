# REPORT_REQUESTED v1 Contract

Contract version: `REPORT_REQUESTED v1`  
Owner Jira for Producer/Topic ops: `INF-007 / S15P11A701-350`  
Owner Jira for Consumer: `AI-007`

## Purpose

Backend publishes a report-request event after a field-visit session ends.
AI-007 FastAPI/Python Worker will later consume the same topic.

AI-005 and AI-006 implement report generation and evidence linking. Those
business rules are intentionally outside this Kafka contract.

## Topic

| Item | Value |
|---|---|
| Topic | `field-visit.report.request.v1` |
| Partitions | `1` |
| Replication factor | `1` |
| Retention | `604800000` ms (7 days) |
| Cleanup policy | `delete` |

## Kafka Key

- Key type: string
- Value: `studyId` converted to decimal string
- Purpose: keep same-study events ordered in the single partition

## Payload

JSON object with exactly these fields from Java `ReportRequestedEvent`:

| Field | Type | Notes |
|---|---|---|
| `studyId` | number (long) | required, >= 1 |
| `sessionId` | number (long) | required, >= 1 |
| `apartmentId` | number (long) | required, >= 1 |
| `occurredAt` | string | ISO-8601 UTC Instant, e.g. `2026-08-01T09:15:30.123Z` |

Do not add:

- `eventId`
- `reportId`
- `idempotencyKey`
- `schemaVersion`
- user profile fields
- AI source text
- report result body

## Example JSON

```json
{
  "studyId": 42,
  "sessionId": 7,
  "apartmentId": 100,
  "occurredAt": "2026-08-01T09:15:30.123Z"
}
```

## Serialization

- Spring Kafka producer uses `StringSerializer` for key and value
  (`spring.kafka.producer.*` in `backend/src/main/resources/application.yaml`)
- Adapter serializes the event to a JSON string with the application
  `ObjectMapper`, then sends that string as the Kafka value
- The JSON top-level value is an object, not a quoted JSON string
- A Python consumer can parse the Kafka value once with `json.loads` to get
  an object

## Producer behavior

- `KafkaReportRequestAdapter` performs an asynchronous `KafkaTemplate.send`
- The request method does **not** wait for broker acknowledgement
- `publishTimeout` is applied asynchronously to the send Future
- Success, failure, and timeout are each logged once in the completion callback
- Logs include only `topic`, `key`, `studyId`, and `sessionId`
- Full payload and secrets must never be logged
- After a timeout log, whether the message was actually delivered can be unclear

## Producers and Consumers

| Role | Component |
|---|---|
| Producer | Spring Boot `KafkaReportRequestAdapter` implementing `ReportRequestPort` |
| Publish timing | AFTER_COMMIT listener after field-visit close/finish |
| Consumer | AI-007 FastAPI/Python Worker |
| Consumer Group | decided in AI-007, not in INF-007 |

## Delivery semantics

- Kafka delivery may be duplicated
- Producer timeout logging does not prove non-delivery
- AI-007 Consumer must be safe under duplicate processing
- The final idempotency rule is **not** fixed in INF-007
- AI-007 must confirm the latest DB constraints, report state model, and
  session uniqueness before choosing an idempotency strategy
- Current payload has no `eventId` or `idempotencyKey`
- Producer enablement defaults to `REPORT_KAFKA_ENABLED=false`
- Do not enable the production producer until AI-007 is deployed and
  Producer→Consumer E2E is approved

## Activation

| Environment | Default |
|---|---|
| local | `false` |
| test | `false` |
| prod example | `false` |

When disabled:

- Backend still starts
- BE-018 finish/close flow still commits
- Listener logs that the adapter is absent and does not fail the transaction
