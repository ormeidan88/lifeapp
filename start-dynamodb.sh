#!/bin/bash
# Start DynamoDB Local for development
# Download from: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.DownloadingAndRunning.html

DYNAMO_DIR="${DYNAMO_DIR:-$HOME/dynamodb-local}"

if [ ! -f "$DYNAMO_DIR/DynamoDBLocal.jar" ]; then
  echo "DynamoDB Local not found at $DYNAMO_DIR"
  echo "Download it from AWS and extract to $DYNAMO_DIR"
  echo "Or set DYNAMO_DIR to the correct path"
  exit 1
fi

echo "Starting DynamoDB Local on port 8001..."
cd "$DYNAMO_DIR"
java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb -port 8001
