#!/bin/bash

echo "=== File API Kafka Topics Setup ==="

# Wait for Kafka to fully start
echo "Waiting for Kafka to fully start..."
sleep 15

# Create File API topics
echo "Creating File API topics..."
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server ${KAFKA_HOST:-kafka}:9092 --replication-factor 1 --partitions 3 --topic ${FILE_API_TOPIC:-bella_file_api}

# List all topics for verification
echo "Listing all topics:"
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server ${KAFKA_HOST:-kafka}:9092 --list

echo "File API Kafka setup completed!"
echo "================================"

# Keep container running
tail -f /dev/null