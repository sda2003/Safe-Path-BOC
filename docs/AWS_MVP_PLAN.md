# SafePath on AWS: MVP master plan

Living plan for the AWS features. Update the **Progress** checkboxes as you go.
Feature 1 is fully specified; features 2–5 are outlined and only done if time permits.

---

## 0. Ground rules (read before every session)

- **Region is `ap-southeast-2` (Sydney). Every resource goes there.** The AWS project cannot create
  Regional resources anywhere else. (Only CloudFront/WAF/global logs may touch `us-east-1`, not needed here.)
- Project is on the **FREE plan** (USD 100 credits, expires 2027-04-10). Lambda, API Gateway, DynamoDB,
  CloudFormation, SNS, CloudWatch, S3, IoT Core are all fine. Run `aws freetier get-account-plan-state`
  if a service suddenly fails; sudden "Access Denied" on things that used to work = check spend
  status in AWS Settings > Billing.
- Do **not** use: Lambda@Edge, CloudFormation StackSets, cross-Region replication, Pinpoint, Kinesis,
  RDS, Cognito (see section 9).
- All infra lives in `infra/` (AWS CDK, TypeScript). Never click-create resources in the console;
  change the stack and redeploy so `cdk destroy` can clean everything up.
- Shell is **PowerShell on Windows**. Run CDK commands from `c:\Projects\SafePath\infra`.
- Never commit `.env`, `infra/outputs.json`, account IDs, or keys.

---

## 1. Decisions already made

| Topic | Decision | Why |
|---|---|---|
| Region | `ap-southeast-2` | Project's assigned Region |
| IaC | AWS CDK v2, TypeScript, in `infra/` | Repeatable, one-command cleanup, features 2–5 are small additions |
| API front door | API Gateway **HTTP API** (not REST API) | ~70% cheaper, lower latency, supports throttling. REST API only needed for WAF/API keys/usage plans |
| Compute | One Lambda (Node.js 22, arm64, 256 MB) for both routes | One risk engine reused by features 2 and 5 |
| Storage | DynamoDB on-demand, PK `district`, SK `sk = createdAt#id`, TTL 7 days | "Latest alerts for district" is one Query. Viva line: *DynamoDB is the MVP stand-in for PostGIS* |
| Auth | **Open endpoint** + throttling (10 rps, burst 20) + strict input validation | Deadline. Stretch: HTTP API JWT authorizer using Supabase JWKS |
| Supabase | **Dual write**: keep Supabase, also send to AWS | Least risk. Room stays the offline source of truth |

---

## 2. Progress (as of 2026-10-10, 08:10)

### Done
- [x] Codebase analysis (findings in section 3)
- [x] Preflight: AWS credentials valid via AWS MCP, FREE plan active, Region confirmed `ap-southeast-2`
- [x] CDK project files written:
  - `infra/package.json`, `infra/tsconfig.json`, `infra/cdk.json`, `infra/.gitignore`
  - `infra/bin/safepath.ts`: app entry, Region pinned to `ap-southeast-2`
  - `infra/lib/safepath-stack.ts`: DynamoDB table, Lambda, HTTP API, 2 routes, throttling, least-privilege grant, outputs
  - `infra/lambda/alerts.ts`: validation, risk score, idempotent POST, GET latest, structured JSON logs
- [x] `npm install` in `infra/` (it succeeded on the second try after a network reset)
- [x] **Task 1.1**: esbuild 0.25.12 rebuilt, `tsc --noEmit` clean
- [x] **Task 1.2**: `byTime` GSI added (`gsi1pk = ALL`). Empty district queries the index; a present but invalid district still returns 400. `cdk synth` succeeded. The Lambda policy already includes `.../index/*` for `dynamodb:Query`, so no extra IAM statement was needed.

### Feature 1 complete (resume at Features 2–5 only if asked)
- [x] **Task 1.3**: CDK bootstrap (`CDKToolkit` created in ap-southeast-2)
- [x] **Task 1.4**: deploy
- [x] **Task 1.5**: API smoke tests
- [x] **Task 1.6**: config plumbing (`SAFEPATH_API_URL` in `.env.example`, local `.env`, `BuildConfig`, `AwsConfig`)
- [x] **Task 1.7**: Retrofit API + `CloudAlertDto` + Hilt module (no second unqualified Retrofit/OkHttpClient)
- [x] **Task 1.8**: `AlertRepository` dual-write and island-wide merge; success toast includes AWS
- [x] **Task 1.9**: build check (`assembleDebug` and `testDebugUnitTest` succeeded after removing the unused google-services plugin)
- [x] **Task 1.10**: end-to-end on the API 34 emulator. Flood/HIGH from the app landed in DynamoDB (`source=citizen`) with no AWS sync error. A second Kandy alert posted to the API showed up after reopening the map (Room cache contains it). Airplane mode still opened the home screen on cached Room data.
- [ ] Features 2–5

---

## 3. Codebase facts the plan depends on

- `AlertRepository.reportAlert()` (`app/src/main/java/com/hades/safepath/data/repository/AlertRepository.kt`)
  saves to Room, then **returns early if Supabase is not configured**. The AWS call must go
  **before** that early return.
- `AlertRepository.getActiveAlerts()` merges Room + Supabase and takes **no district argument**.
  It is called by `MapViewModel.loadActiveAlerts()`.
- `SafePathApi` (`network/SafePathApi.kt`) has `GET v1/alerts/latest`, but **nothing uses it**:
  `RetrofitClient` points at a fake URL `https://api.safepath.lk/`, and `NetworkRepository` is never instantiated.
- `AlertUpdateDto` (`signal_code`, `issued_at`, `district`) is too thin to draw an alert. We add a new `CloudAlertDto`.
- **Hilt gotcha:** `di/GeminiModule.kt` already provides an *unqualified* `OkHttpClient` and `Retrofit`.
  Do **not** add another unqualified `@Provides Retrofit`/`OkHttpClient` or Hilt fails with a duplicate binding.
  Build the SafePath Retrofit *inside* the `SafePathApi` provider (Task 1.7).
- Secrets come from `.env` / `local.properties` via `secret("KEY")` in `app/build.gradle.kts` (see `SUPABASE_URL`).
- Report sheet sends type ids `Flood, Landslide, Fire, Tsunami, Earthquake, Other` and severities
  `LOW, MODERATE, HIGH, CRITICAL`. The Lambda lowercases `type`, so the app must re-capitalize when mapping back.
- `district` comes from a spinner and may be blank. Send `"Unknown"` when blank (the Lambda rejects empty strings).

---

## 4. Feature 1: Citizen report ingestion (API Gateway → Lambda → DynamoDB)

```
Android app ──POST /v1/alerts──────────────► API Gateway HTTP API ──► Lambda (validate + score) ──► DynamoDB
            ──GET /v1/alerts/latest[?district]─►  (throttle 10/20)                                SafePathAlerts
```

### API contract

`POST /v1/alerts`: request body (JSON, ≤ 4 KB):
```json
{ "id": "uuid (optional, app sends its Room id)", "type": "Flood", "severity": "HIGH",
  "description": "≤500 chars", "latitude": 6.9271, "longitude": 79.8612,
  "district": "Colombo", "user_id": "supabase-user-id-or-local", "created_at": "ISO-8601 (optional)" }
```
Responses: `201` with the stored alert, `200 {"duplicate":true,"id":...}` on retry with same id,
`400 {"error","details":[...]}` on validation failure, `413` if body too large.

`GET /v1/alerts/latest?district=Colombo&limit=20` (district optional after Task 1.2): `200` with a JSON array of:
```json
{ "id","user_id","type":"flood","severity":"HIGH","description","latitude","longitude",
  "district","is_active":true,"created_at","risk_score":60,"source":"citizen" }
```

Risk score = `severityBase (1..4) × hazardWeight × 16.7`, capped at 100
(tsunami 1.5, earthquake 1.4, landslide 1.3, flood 1.2, fire 1.1, other 1.0). Feature 2 reuses it.

### Task 1.1: esbuild + typecheck (5 min)
CDK bundles the Lambda with esbuild. npm blocked esbuild's postinstall script (it downloads the native
binary); without it CDK tries Docker and fails.
```powershell
cd c:\Projects\SafePath\infra
npm install-scripts approve esbuild
npm rebuild esbuild
npx esbuild --version          # must print a version
npx --no-install tsc --noEmit  # must print nothing
```
If `npm install` ever fails with `ECONNRESET`, just retry with
`npm install --fetch-retries=5 --fetch-retry-mintimeout=5000`.
Never run plain `npx tsc` before install: it downloads an unrelated package called `tsc`.

### Task 1.2: "all districts" index (15 min, do BEFORE first deploy)
Why: `getActiveAlerts()` has no district, and the home screen wants island-wide latest alerts.
Querying a constant-key GSI avoids a full table Scan.

`infra/lib/safepath-stack.ts`: add to the `TableV2` props:
```ts
globalSecondaryIndexes: [
  {
    indexName: 'byTime',
    partitionKey: { name: 'gsi1pk', type: dynamodb.AttributeType.STRING },
    sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
  },
],
```
`infra/lambda/alerts.ts`:
1. In `createAlert`, add `gsi1pk: 'ALL',` to `item`.
2. In `latestAlerts`, make district optional:
   - if `district` is empty, run `QueryCommand` with `IndexName: 'byTime'`,
     `KeyConditionExpression: 'gsi1pk = :all'`, `':all': 'ALL'`, same filter/limit/`ScanIndexForward: false`;
   - if present but fails `DISTRICT_RE`, return 400; otherwise keep the existing district query.

After `npx cdk synth`, confirm the Lambda's IAM policy includes `.../index/*`
(search `cdk.out/SafePathStack.template.json` for `index`). If it does not, add to the stack:
```ts
alertsFn.addToRolePolicy(new iam.PolicyStatement({
  actions: ['dynamodb:Query'],
  resources: [`${alertsTable.tableArn}/index/byTime`],
}));
```
(`import * as iam from 'aws-cdk-lib/aws-iam';`)

### Task 1.3: bootstrap CDK (5 min, once per account+Region)
Creates the `CDKToolkit` stack (an S3 bucket for assets + deploy roles).
```powershell
aws sts get-caller-identity          # if this fails: run `aws login` and retry
$env:AWS_REGION = "ap-southeast-2"
npx cdk bootstrap
```

### Task 1.4: deploy (5 min)
```powershell
npx cdk diff
npx cdk deploy --require-approval broadening --outputs-file outputs.json
```
It will show the IAM changes (Lambda role, API invoke permission). Approve them. Outputs: `ApiUrl`
(ends with `/`), `AlertsTableName`, `AlertsFunctionName`.

### Task 1.5: smoke test the API without the app (10 min)
```powershell
$api = (Get-Content outputs.json | ConvertFrom-Json).SafePathStack.ApiUrl
$id  = [guid]::NewGuid().ToString()
$body = @{ id=$id; type="Flood"; severity="HIGH"; description="Smoke test"; latitude=6.9271;
           longitude=79.8612; district="Colombo"; user_id="smoke" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "${api}v1/alerts" -Body $body -ContentType "application/json"   # 201
Invoke-RestMethod -Method Post -Uri "${api}v1/alerts" -Body $body -ContentType "application/json"   # duplicate:true
Invoke-RestMethod "${api}v1/alerts/latest?district=Colombo"                                        # array with it
Invoke-RestMethod "${api}v1/alerts/latest"                                                         # all districts
# Negative: expect 400
try { Invoke-RestMethod -Method Post -Uri "${api}v1/alerts" -Body '{"type":"Flood","severity":"HUGE"}' -ContentType "application/json" }
catch { $_.Exception.Response.StatusCode; $_.ErrorDetails.Message }
```
Also check the item in the DynamoDB console (ap-southeast-2 > Tables > Explore items) and the logs in
CloudWatch Logs (`AlertsFunctionLogs`, JSON lines `alert_created`).

### Task 1.6: config plumbing in the app (10 min)
- `.env.example`: add `SAFEPATH_API_URL=https://xxxxxxxx.execute-api.ap-southeast-2.amazonaws.com/`
- Your real `.env`: add the `ApiUrl` value from `outputs.json` (keep trailing `/`).
- `app/build.gradle.kts` `defaultConfig`, next to the Supabase fields:
  ```kotlin
  buildConfigField("String", "SAFEPATH_API_URL", "\"${secret("SAFEPATH_API_URL")}\"")
  ```
- New `app/src/main/java/com/hades/safepath/data/remote/AwsConfig.kt`:
  ```kotlin
  object AwsConfig {
      fun isConfigured(): Boolean = BuildConfig.SAFEPATH_API_URL.startsWith("https://")
  }
  ```

### Task 1.7: Retrofit API + DTO + Hilt (15 min)
`network/SafePathApi.kt`: add (keep existing methods):
```kotlin
@POST("v1/alerts")
suspend fun postAlert(@Body body: CloudAlertDto): Response<CloudAlertDto>

@GET("v1/alerts/latest")
suspend fun getLatestCloudAlerts(
    @Query("district") district: String? = null,
    @Query("limit") limit: Int = 20
): Response<List<CloudAlertDto>>
```
New `network/dto/CloudAlertDto.kt`: Gson `@SerializedName` fields `id, user_id, type, severity,
description, latitude, longitude, district, is_active, created_at, risk_score (Int?), source (String?)`, plus:
- `fun toAlert(): Alert` that re-capitalizes type: `type.replaceFirstChar { it.uppercase() }`
- `companion fun from(alert: Alert)`, which sends `district.ifBlank { "Unknown" }`

New `di/SafePathApiModule.kt`. It must **not** expose Retrofit/OkHttpClient (clashes with `GeminiModule`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SafePathApiModule {
    @Provides
    @Singleton
    fun provideSafePathApi(): SafePathApi {
        val base = BuildConfig.SAFEPATH_API_URL.ifBlank { "https://api.safepath.lk/" }
            .let { if (it.endsWith("/")) it else "$it/" }
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SafePathApi::class.java)
    }
}
```
Optionally delete `RetrofitClient.kt` once nothing references it (it is currently unused).

### Task 1.8: `AlertRepository` (15 min)
- Add `private val safePathApi: SafePathApi` to the `@Inject constructor`.
- In `reportAlert()`, **right after** `alertDao.insertAlert(local)` and **before** the Supabase early return:
  ```kotlin
  if (AwsConfig.isConfigured()) {
      runCatching {
          val res = safePathApi.postAlert(CloudAlertDto.from(local))
          if (!res.isSuccessful) Log.w(TAG, "AWS alert sync HTTP ${res.code()}")
      }.onFailure { e -> Log.e(TAG, "AWS alert sync failed; kept local copy", e) }
  }
  ```
  Never fail the user's report because AWS is unreachable (offline-first).
- In `getActiveAlerts()`, add an `aws` list fetched with `safePathApi.getLatestCloudAlerts()` (no district,
  i.e. island-wide), wrapped in `runCatching`, mapped with `toAlert()`, empty on failure or when not configured.
  Merge order stays **local first**, then Supabase, then AWS, de-duplicated by `id`.
- Optional: cache fetched AWS alerts into Room (`alertDao.insertAlert`) so they survive airplane mode.
  This is the "offline" step in the demo.
- Update the success toast in `ReportAlertBottomSheet` so it doesn't claim "saved on this device only"
  when AWS is configured: check `SupabaseConfig.isConfigured() || AwsConfig.isConfigured()`.

### Task 1.9: build check (5 min)
```powershell
cd c:\Projects\SafePath
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```
`INTERNET` permission is already needed by Supabase/Gemini, so there's no manifest change. HTTPS only, so no cleartext config.

### Task 1.10: end-to-end (10 min)
1. Install, report a Flood / HIGH in the app.
2. `adb logcat -s AlertRepository` shows no "AWS alert sync failed".
3. The item appears in DynamoDB with `source = citizen`.
4. Insert a second alert via the PowerShell POST (different district); after reopening the map, it shows up
   (proves GET + merge works).
5. Airplane mode: app still opens and shows Room data.

**Feature 1 done when:** 1.5 and 1.10 pass, and `git status` shows no secrets or `outputs.json`.

---

## 5. Feature 2: Risk alerting via SNS (45–60 min, if time permits)
- Stack: `const topic = new sns.Topic(this, 'AgencyAlerts')`; email subscription from context:
  `topic.addSubscription(new subs.EmailSubscription(this.node.tryGetContext('alertEmail')))`;
  deploy with `npx cdk deploy -c alertEmail=you@example.com`, then **click the confirmation email**.
- `topic.grantPublish(alertsFn)`; env `TOPIC_ARN`.
- Lambda: after `PutCommand`, if `severity` is HIGH/CRITICAL (or `riskScore >= 60`) publish
  (`@aws-sdk/client-sns` `PublishCommand`) a short subject + body (type, district, coords, Google Maps link).
  Catch and log publish errors; never fail the report because of SNS.
- SMS: new accounts are in the SMS sandbox, so verify your own number first (SNS console > Text messaging >
  Sandbox destination phone numbers). Email is the reliable demo path.
- Viva: Pinpoint is end-of-support (Oct 2026); we use **SNS / AWS End User Messaging**.

## 6. Feature 3: CloudWatch dashboard + alarm (30–45 min)
- `new cloudwatch.Dashboard(this, 'SafePathDashboard')` with widgets: `alertsFn.metricInvocations()`,
  `alertsFn.metricErrors()`, `alertsFn.metricDuration()`, and API metrics from `api.metricCount()`,
  `api.metricLatency()`, `api.metricServerError()`, `api.metricClientError()`.
- Alarm: `alertsFn.metricErrors({ period: Duration.minutes(5) })` ≥ 1 for 1 period, then
  `alarm.addAlarmAction(new actions.SnsAction(topic))` (reuses the feature 2 topic).
- Custom metric `AlertLatencyMs` with no extra API calls: log in **Embedded Metric Format** from the Lambda:
  `{"_aws":{"Timestamp":...,"CloudWatchMetrics":[{"Namespace":"SafePath","Dimensions":[[]],"Metrics":[{"Name":"AlertLatencyMs","Unit":"Milliseconds"}]}]},"AlertLatencyMs":<ingestLatencyMs>}`.
  `ingestLatencyMs` is already computed in `alerts.ts`. Add a widget for it.
- Targets the 15% "Availability, Monitoring & Reliability" criterion.

## 7. Feature 4: S3 offline advisory pack (45–60 min)
- Stack: private bucket (`blockPublicAccess: BLOCK_ALL`, `enforceSSL: true`, `removalPolicy: DESTROY`,
  `autoDeleteObjects: true`) + `BucketDeployment` uploading `infra/assets/guides.json`.
- Keep the bucket private: add route `GET /v1/guides` to the same Lambda (or a small second one) that reads the
  object (`bucket.grantRead(fn)`) and returns it with an `ETag`.
- App: on start when online (`NetworkMonitor`), fetch `/v1/guides`, store in Room (reuse `GuidanceRepository`
  storage), show from Room when offline.

## 8. Feature 5: Simulated IoT river sensor (≈1.5 h, optional)
- Shortcut: the laptop script publishes with **IAM credentials over HTTPS** (`boto3.client('iot-data').publish`
  to topic `safepath/sensors/river-01`). No device certificates needed.
- Stack: `iot.CfnTopicRule` with `SELECT *, topic(3) AS sensorId FROM 'safepath/sensors/+'`, Lambda action
  plus `lambda.CfnPermission` for `iot.amazonaws.com`.
- Lambda: if `waterLevelCm` exceeds a threshold, write an alert with `source = 'sensor'` (same table, same SNS path).
- Do **not** connect the real ESP32 (Wi-Fi + BLE coexistence and cert setup are too risky for the deadline).

## 9. Out of scope (say this in the viva)
Cognito (replacing Supabase Auth is too big), Kinesis (per-shard-hour cost; present as scale-up path),
RDS/PostGIS (setup time; DynamoDB is the stand-in), FCM push (Firebase removed; the google-services plugin
is still applied in `app/build.gradle.kts`), Pinpoint (end of support).

## 10. Demo script
1. Report a flood in the app. 2. API Gateway receives it. 3. Lambda validates, scores, saves to DynamoDB.
4. (F2) SNS emails/SMSes the "district officer". 5. Home/map pulls latest alerts from AWS.
6. (F3) CloudWatch dashboard shows the traffic. 7. Airplane mode: cached data still shows.

## 11. Security slide
- API Gateway throttling (10 rps / burst 20) + strict server-side validation + 4 KB body cap.
- Least-privilege Lambda role: only `dynamodb:PutItem` + `dynamodb:Query` on this one table (+ its index).
- Encryption at rest by default (DynamoDB, S3); TLS-only API endpoint.
- 7-day TTL limits data retention; 1-week log retention.
- Known gap: no caller auth yet. Next step: HTTP API JWT authorizer validating Supabase tokens.

## 12. Cleanup (after the demo)
```powershell
cd c:\Projects\SafePath\infra
npx cdk destroy        # removes table, Lambda, API, logs (all set to DESTROY)
```
The `CDKToolkit` bootstrap stack can stay (near-zero cost) or be deleted from the CloudFormation console.
