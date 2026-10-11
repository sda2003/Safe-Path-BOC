import { randomUUID } from 'node:crypto';
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocumentClient, PutCommand, QueryCommand } from '@aws-sdk/lib-dynamodb';
import type { APIGatewayProxyEventV2, APIGatewayProxyStructuredResultV2 } from 'aws-lambda';

const TABLE_NAME = process.env.TABLE_NAME!;
const TTL_DAYS = 7;
const MAX_BODY_BYTES = 4096;
const MAX_DESCRIPTION = 500;
const DEFAULT_LIMIT = 20;
const MAX_LIMIT = 50;

const SEVERITY_BASE = { LOW: 1, MODERATE: 2, HIGH: 3, CRITICAL: 4 } as const;
const HAZARD_WEIGHT: Record<string, number> = {
  tsunami: 1.5,
  earthquake: 1.4,
  landslide: 1.3,
  flood: 1.2,
  fire: 1.1,
  other: 1.0,
};
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DISTRICT_RE = /^[\p{L} .'-]{1,40}$/u;

type Severity = keyof typeof SEVERITY_BASE;

const ddb = DynamoDBDocumentClient.from(new DynamoDBClient({}), {
  marshallOptions: { removeUndefinedValues: true },
});

export const handler = async (
  event: APIGatewayProxyEventV2,
): Promise<APIGatewayProxyStructuredResultV2> => {
  try {
    switch (event.routeKey) {
      case 'POST /v1/alerts':
        return await createAlert(event);
      case 'GET /v1/alerts/latest':
        return await latestAlerts(event);
      default:
        return json(404, { error: 'Not found' });
    }
  } catch (err) {
    log('ERROR', 'unhandled', { error: String(err) });
    return json(500, { error: 'Internal error' });
  }
};

async function createAlert(event: APIGatewayProxyEventV2) {
  const raw = event.isBase64Encoded && event.body
    ? Buffer.from(event.body, 'base64').toString('utf8')
    : event.body ?? '';
  if (Buffer.byteLength(raw, 'utf8') > MAX_BODY_BYTES) {
    return json(413, { error: 'Body too large' });
  }

  let body: Record<string, unknown>;
  try {
    body = JSON.parse(raw);
  } catch {
    return json(400, { error: 'Body must be JSON' });
  }

  const errors: string[] = [];
  const type = str(body.type).toLowerCase();
  if (!(type in HAZARD_WEIGHT)) errors.push(`type must be one of ${Object.keys(HAZARD_WEIGHT).join(', ')}`);

  const severity = str(body.severity).toUpperCase() as Severity;
  if (!(severity in SEVERITY_BASE)) errors.push('severity must be LOW, MODERATE, HIGH or CRITICAL');

  const latitude = Number(body.latitude);
  const longitude = Number(body.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) errors.push('latitude out of range');
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) errors.push('longitude out of range');

  const district = str(body.district).trim();
  if (!DISTRICT_RE.test(district)) errors.push('district is required (letters only, max 40 chars)');

  const description = str(body.description).trim();
  if (description.length > MAX_DESCRIPTION) errors.push(`description max ${MAX_DESCRIPTION} chars`);

  if (errors.length) return json(400, { error: 'Validation failed', details: errors });

  const id = UUID_RE.test(str(body.id)) ? str(body.id).toLowerCase() : randomUUID();
  const now = new Date();
  const createdAt = now.toISOString();
  const clientCreatedAt = parseClientTime(body.created_at, now);
  const riskScore = scoreRisk(type, severity);

  const item = {
    district,
    gsi1pk: 'ALL',
    sk: `${createdAt}#${id}`,
    id,
    type,
    severity,
    description,
    latitude,
    longitude,
    reportedBy: str(body.user_id).slice(0, 64) || 'anonymous',
    isActive: true,
    createdAt,
    clientCreatedAt,
    riskScore,
    source: 'citizen',
    ttl: Math.floor(now.getTime() / 1000) + TTL_DAYS * 86400,
  };

  // Same id among the district's recent reports is treated as a client retry, not a new report.
  const existing = await ddb.send(new QueryCommand({
    TableName: TABLE_NAME,
    KeyConditionExpression: 'district = :d',
    FilterExpression: 'id = :id',
    ExpressionAttributeValues: { ':d': district, ':id': id },
    ProjectionExpression: 'id',
    ScanIndexForward: false,
    Limit: 100,
  }));
  if (existing.Items?.length) {
    log('INFO', 'duplicate_report', { id, district });
    return json(200, { duplicate: true, id });
  }

  await ddb.send(new PutCommand({ TableName: TABLE_NAME, Item: item }));

  log('INFO', 'alert_created', {
    id,
    district,
    type,
    severity,
    riskScore,
    ingestLatencyMs: clientCreatedAt ? now.getTime() - Date.parse(clientCreatedAt) : undefined,
  });
  return json(201, toDto(item));
}

async function latestAlerts(event: APIGatewayProxyEventV2) {
  const district = (event.queryStringParameters?.district ?? '').trim();
  if (district && !DISTRICT_RE.test(district)) {
    return json(400, { error: 'district must be letters only, max 40 chars' });
  }
  const requested = Number(event.queryStringParameters?.limit ?? DEFAULT_LIMIT);
  const limit = Number.isInteger(requested) ? Math.min(Math.max(requested, 1), MAX_LIMIT) : DEFAULT_LIMIT;

  const res = await ddb.send(new QueryCommand({
    TableName: TABLE_NAME,
    IndexName: district ? undefined : 'byTime',
    KeyConditionExpression: district ? 'district = :d' : 'gsi1pk = :all',
    FilterExpression: 'isActive = :active',
    ExpressionAttributeValues: district
      ? { ':d': district, ':active': true }
      : { ':all': 'ALL', ':active': true },
    ScanIndexForward: false,
    Limit: limit,
  }));

  return json(200, (res.Items ?? []).map(toDto));
}

function scoreRisk(type: string, severity: Severity): number {
  return Math.min(100, Math.round(SEVERITY_BASE[severity] * (HAZARD_WEIGHT[type] ?? 1) * 16.7));
}

/** Response shape matches the app's `Alert` model (snake_case keys). */
function toDto(item: Record<string, any>) {
  return {
    id: item.id,
    user_id: item.reportedBy,
    type: item.type,
    severity: item.severity,
    description: item.description,
    latitude: item.latitude,
    longitude: item.longitude,
    district: item.district,
    is_active: item.isActive,
    created_at: item.createdAt,
    risk_score: item.riskScore,
    source: item.source,
  };
}

function parseClientTime(value: unknown, now: Date): string | undefined {
  const t = Date.parse(str(value));
  if (!Number.isFinite(t) || Math.abs(now.getTime() - t) > 86400_000) return undefined;
  return new Date(t).toISOString();
}

function str(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function json(statusCode: number, body: unknown): APIGatewayProxyStructuredResultV2 {
  return {
    statusCode,
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  };
}

function log(level: 'INFO' | 'ERROR', message: string, data: Record<string, unknown>) {
  console.log(JSON.stringify({ level, message, ...data }));
}
