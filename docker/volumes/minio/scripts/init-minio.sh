#!/bin/bash

echo "=== MinIO Initialization ==="

# Wait for MinIO to be ready using mc
echo "Waiting for MinIO to be ready..."
retry_count=0
max_retries=30

check_minio_ready() {
  mc alias set myminio-check http://localhost:9000 ${MINIO_ROOT_USER:-bella_user} ${MINIO_ROOT_PASSWORD:-12345678} > /dev/null 2>&1
  return $?
}

while ! check_minio_ready; do
  retry_count=$((retry_count+1))
  if [ $retry_count -ge $max_retries ]; then
    echo "MinIO service failed to start after ${max_retries} attempts. Exiting."
    exit 1
  fi
  echo "Waiting for MinIO service... (Attempt ${retry_count}/${max_retries})"
  sleep 2
done

echo "MinIO is ready!"

# Get environment variables or use defaults
MINIO_ROOT_USER=${MINIO_ROOT_USER:-bella_user}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:-12345678}
BUCKET_NAME=${MINIO_BUCKET:-bella-file-api}

echo "Configuring MinIO alias..."
if mc alias set myminio http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" > /dev/null 2>&1; then
  echo "MinIO client configured successfully"
else
  echo "Failed to configure MinIO client. Check credentials."
  exit 1
fi

echo "Creating bucket: $BUCKET_NAME"
output=$(mc mb myminio/"$BUCKET_NAME" 2>&1)
status=$?

if [ $status -eq 0 ]; then
  echo "Bucket '$BUCKET_NAME' created successfully"
elif [[ "$output" == *"already own it"* ]] || [[ "$output" == *"already exists"* ]]; then
  echo "Bucket '$BUCKET_NAME' already exists"
else
  echo "Failed to create bucket '$BUCKET_NAME': $output"
  exit 1
fi

# Set bucket policy
echo "Setting bucket policy..."
output=$(mc policy set download myminio/"$BUCKET_NAME" 2>&1)
if [ $? -eq 0 ]; then
  echo "Bucket policy set successfully"
else
  echo "Failed to set bucket policy: $output"
fi

echo "Listing buckets:"
mc ls myminio

echo "MinIO initialization completed!"
echo "Keeping container running..."
tail -f /dev/null