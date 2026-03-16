#!/bin/bash
# scripts/localstack-init.sh
# Creates the S3 bucket in LocalStack on startup

echo "Creating S3 bucket: life-enrichment-photos"
aws --endpoint-url=http://localhost:4566 s3 mb s3://life-enrichment-photos --region us-east-1
echo "S3 bucket created successfully"
