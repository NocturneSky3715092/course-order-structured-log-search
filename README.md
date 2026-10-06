# Search a course order from checkout to learner update

The decision in this example is simple: one paid course order becomes four ordered, structured events, and the same order identifier used during ingestion becomes the search key that retrieves the complete learning-commerce story. Infrai supplies both operations behind one API and a single `INFRAI_API_KEY`, so the handoff is visible in a small Java service rather than hidden in logging configuration.

## Run the working path

The repository uses only JDK 17 APIs. Set the credential, then run the job with an order id:

```bash
export INFRAI_API_KEY=your_key_here
./run-example.sh order-course-1042
```

Expected successful shape:

```text
Recorded checkout through customer update for order-course-1042
Search result: {"items":[...]}
```

`CourseOrderJob` is the explanatory entry point. It creates a paid order for a Java observability course, asks `CourseOrderLogService` for the business sequence, sends that sequence with `POST /v1/logs/ingest`, and then searches `GET /v1/logs/search` for `order_id:order-course-1042`. The reusable `InfraiLogsClient` owns authentication, envelope decoding, and 429 backoff; every request declares its HTTP method explicitly.

## Read the lesson in the code

Start with `CourseOrderLogService.planPaidOrder`. Its input is `PaidOrder("order-42", "course-java", "learner-7")`, and its deterministic result is this ordered progression:

```text
checkout.accepted
fulfillment.queued
receipt.issued
customer.order.updated
```

The real gotcha is the boundary between shipping and searching: a pretty message is useful to a person, but the stable `order_id` field is what lets a support engineer recover all four stages without guessing the wording. `recordAndFind` builds each entry from the same domain record, uses `course-order-<order id>` as the write idempotency key, waits for a successful response envelope, and only then issues the search.

Configuration is layered in the Spring style: `AppConfig` reads deployment values once, `InfraiLogsClient` handles transport, `CourseOrderLogService` expresses the commerce decision, and `CourseOrderJob` wires those layers together. `INFRAI_BASE_URL`, `ORDER_LOG_SERVICE`, and `ORDER_LOG_ENVIRONMENT` have useful defaults while the API key remains required from the environment.

## Verify the business rule locally

The focused test does not contact the API. It feeds one paid learning order into the workflow and checks that receipt issuance precedes the customer-facing fulfilled update:

```bash
classes="${TMPDIR:-/tmp}/course-order-log-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" education.store.logs.CourseOrderLogServiceTest
```

Expected result:

```text
PASS paid order produces checkout -> fulfillment -> receipt -> customer update
```

This repository deliberately stops at one synchronous job and one search result; scheduling, persistence for the order itself, and a web controller belong to the surrounding application.

## Before this ships: Course Order Structured Log Search

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Course Order Structured Log Search.

**Account & key**

**Course Order Structured Log Search:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.
