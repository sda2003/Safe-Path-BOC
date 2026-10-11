#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { SafePathStack } from '../lib/safepath-stack';

const app = new cdk.App();

// The AWS project is locked to a single Region; every resource must live there.
new SafePathStack(app, 'SafePathStack', {
  env: { region: 'ap-southeast-2', account: process.env.CDK_DEFAULT_ACCOUNT },
  description: 'SafePath MVP: citizen alert ingestion (API Gateway -> Lambda -> DynamoDB)',
});

cdk.Tags.of(app).add('project', 'safepath');
