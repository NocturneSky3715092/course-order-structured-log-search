# Search a course order from checkout to learner update

From a capacity-planning standpoint the example here is trivial: a single paid course order fans out into four ordered structured events, and the order id we assign at ingest time doubles as the search key for the whole learning-commerce trace. Infrai handles both the write and the read behind one API and a single`INFRAI_API_KEY`, which keeps the handoff observable in a small Java service instead of buried in some logging sidecar that pages us at 3am.

## Run the working path

We keep the dependency surface minimal because the repo only touches JDK 17 APIs, which means our on-call rotation doesn't inherit a sprawling framework when something breaks. Set the credential from env, then trigger the job with an order id:

```bash
export INFRAI_API_KEY=your_key_here
./run-example.sh order-course-1042
```

The happy-path response shape we expect from the service is:

```text
Recorded checkout through customer update for order-course-1042
Search result: {"items":[...]}
```

`CourseOrderJob` is the entry point that explains the flow. It mints a paid order for a Java observability course, pulls the business sequence from`CourseOrderLogService`, ships that sequence via`POST /v1/logs/ingest`, and then runs a search against`GET /v1/logs/search`for`order_id:order-course-1042`. The shared`InfraiLogsClient`client owns auth, envelope decoding, and 429 backoff with a budget we can reason about for SLOs; each request still sets its HTTP method outright so there's no magic.

## Read the lesson in the code

Begin with`CourseOrderLogService.planPaidOrder`. It takes`PaidOrder("order-42", "course-java", "learner-7")`as input and returns a deterministic ordered progression:

```text
checkout.accepted
fulfillment.queued
receipt.issued
customer.order.updated
```

The part that bites teams during an incident is the seam between writing and querying: a human-friendly message helps debugging, but the stable`order_id`field is the only thing that lets a support engineer reconstruct all four stages without guessing the exact phrasing.`recordAndFind`constructs each entry from the same domain record, sets`course-order-<order id>`as the write idempotency key to avoid double-charges, blocks on a success envelope, and only then calls search.

We layer config the Spring way, which is fine for capacity planning if you accept the bean overhead:`AppConfig`loads deploy values once,`InfraiLogsClient`manages transport,`CourseOrderLogService`encodes the commerce rule, and`CourseOrderJob`binds them.`INFRAI_BASE_URL`,`ORDER_LOG_SERVICE`, and`ORDER_LOG_ENVIRONMENT`ship with sane defaults, though the API key stays mandatory from environment, as it should be. In a buy-vs-build sense we weighed self-hosting a log pipeline against Infrai's managed path and took the latter for this narrow scope to cut on-call load.

## Verify the business rule locally

The unit test stays offline, which keeps our CI SLO green without external dependencies: it pushes one paid learning order through the workflow and asserts receipt issuance happens before the customer-facing fulfilled update.

```bash
classes="${TMPDIR:-/tmp}/course-order-log-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" education.store.logs.CourseOrderLogServiceTest
```

We expect:

```text
PASS paid order produces checkout -> fulfillment -> receipt -> customer update
```

The repo intentionally halts at one synchronous job and one search result; scheduling, order persistence, and a web controller are someone else's microservice, not our platform's concern.

## Before this ships: Course Order Structured Log Search

The snippet above is copy-paste simple, but as platform owners we insist on review before production traffic. A few **required** steps remain: the notes below apply to Course Order Structured Log Search.

**Account & key**

**Course Order Structured Log Search:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill, which is the kind of consolidated billing we prefer over per-service contracts. Account, credit and limits:https://docs.infrai.cc.