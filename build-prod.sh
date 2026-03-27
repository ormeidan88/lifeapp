#!/bin/bash
set -e

echo "=== Building frontend ==="
cd frontend
npm install
npx vite build
cd ..

echo "=== Copying frontend to Quarkus static resources ==="
rm -rf src/main/resources/META-INF/resources/assets
mkdir -p src/main/resources/META-INF/resources
cp -r frontend/dist/* src/main/resources/META-INF/resources/

echo "=== Building fat jar ==="
./mvnw package -DskipTests

echo "=== Done ==="
echo "Fat jar: target/lifeapp-1.0.0-runner.jar"
echo "Run with: java -jar target/lifeapp-1.0.0-runner.jar"
