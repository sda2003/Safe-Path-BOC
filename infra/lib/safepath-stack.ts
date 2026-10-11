import * as path from 'node:path';
import { CfnOutput, Duration, RemovalPolicy, Stack, StackProps } from 'aws-cdk-lib';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import { HttpLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import { NodejsFunction } from 'aws-cdk-lib/aws-lambda-nodejs';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

export class SafePathStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    // Partition by district so "latest alerts for X" is a single Query, newest first via the sort key.
    const alertsTable = new dynamodb.TableV2(this, 'AlertsTable', {
      partitionKey: { name: 'district', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
      billing: dynamodb.Billing.onDemand(),
      timeToLiveAttribute: 'ttl',
      // Constant partition so island-wide "latest" is one Query, not a Scan.
      globalSecondaryIndexes: [
        {
          indexName: 'byTime',
          partitionKey: { name: 'gsi1pk', type: dynamodb.AttributeType.STRING },
          sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
        },
      ],
      // Demo data only: `cdk destroy` removes the table too.
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const alertsFn = new NodejsFunction(this, 'AlertsFunction', {
      entry: path.join(__dirname, '..', 'lambda', 'alerts.ts'),
      handler: 'handler',
      runtime: lambda.Runtime.NODEJS_22_X,
      architecture: lambda.Architecture.ARM_64,
      memorySize: 256,
      timeout: Duration.seconds(10),
      environment: {
        TABLE_NAME: alertsTable.tableName,
      },
      logGroup: new logs.LogGroup(this, 'AlertsFunctionLogs', {
        retention: logs.RetentionDays.ONE_WEEK,
        removalPolicy: RemovalPolicy.DESTROY,
      }),
      bundling: {
        minify: true,
        sourceMap: true,
      },
    });

    // Least privilege: only the two actions the handler uses, on this table only.
    alertsTable.grant(alertsFn, 'dynamodb:PutItem', 'dynamodb:Query');

    const api = new apigwv2.HttpApi(this, 'SafePathApi', {
      apiName: 'safepath-api',
      createDefaultStage: false,
    });

    const stage = api.addStage('DefaultStage', {
      stageName: '$default',
      autoDeploy: true,
      throttle: { rateLimit: 10, burstLimit: 20 },
    });

    const integration = new HttpLambdaIntegration('AlertsIntegration', alertsFn);

    api.addRoutes({
      path: '/v1/alerts',
      methods: [apigwv2.HttpMethod.POST],
      integration,
    });
    api.addRoutes({
      path: '/v1/alerts/latest',
      methods: [apigwv2.HttpMethod.GET],
      integration,
    });

    new CfnOutput(this, 'ApiUrl', { value: stage.url });
    new CfnOutput(this, 'AlertsTableName', { value: alertsTable.tableName });
    new CfnOutput(this, 'AlertsFunctionName', { value: alertsFn.functionName });
  }
}
