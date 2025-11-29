# Temporal Worker - Bulk CSV File Processor

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.4.3-blue.svg)](https://micronaut.io/)
[![Temporal](https://img.shields.io/badge/Temporal-1.30.1-purple.svg)](https://temporal.io/)
[![Maven](https://img.shields.io/badge/Maven-Build-red.svg)](https://maven.apache.org/)

A highly scalable and fault-tolerant Temporal worker application built with Micronaut framework for processing large CSV files in bulk. The application leverages Temporal's workflow orchestration capabilities to handle massive file processing with intelligent chunking, parallel processing, and automatic retry mechanisms.

## 🚀 Features

### Core Functionality
- **Bulk CSV Processing**: Process large CSV files efficiently with intelligent chunking strategies
- **Temporal Workflow Orchestration**: Reliable and durable workflow execution using Temporal.io
- **Parallel Chunk Processing**: Process multiple file chunks concurrently with configurable parallelism
- **Intelligent Load Management**: Dynamic throttling to prevent overwhelming downstream services (Kafka)
- **Fault Tolerance**: Automatic retry of failed chunks without reprocessing the entire file
- **Large File Support**: Handle multi-GB files with memory-efficient streaming

### Data Pipeline
```
CSV File (S3) → Split into Chunks → Process in Batches → Push to Kafka → 
Monitor via Redis → Aggregate Results → Upload Output CSV → Generate Report
```

### Key Capabilities
- ✅ **Chunked Processing**: Files are split into manageable chunks for parallel processing
- ✅ **Batch Kafka Publishing**: Data pushed to Kafka in configurable batches (default: 100 records)
- ✅ **Redis State Management**: Track processing status and completion state in Redis
- ✅ **S3 Integration**: Read input files and write output files/reports to AWS S3
- ✅ **Multipart Upload**: Efficient upload of large output files using S3 multipart upload
- ✅ **Report Generation**: Optional CASS report generation with detailed processing statistics
- ✅ **Health Monitoring**: Built-in health checks and metrics via Micronaut Management
- ✅ **Graceful Shutdown**: Worker status tracking and self-shutdown capabilities

## 📋 Table of Contents

- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#️-configuration)
- [Usage](#-usage)
- [Workflow Details](#-workflow-details)
- [API Documentation](#-api-documentation)
- [Development](#-development)
- [Testing](#-testing)
- [Deployment](#-deployment)
- [Monitoring](#-monitoring)
- [Troubleshooting](#-troubleshooting)

## 🏗 Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                     Temporal Workflow Engine                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Temporal Worker Application                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   BulkWorkflow                            │  │
│  │  - Orchestrates entire file processing lifecycle          │  │
│  │  - Manages parallel chunk execution                       │  │
│  │  - Handles workflow state and retries                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                  BulkActivities                           │  │
│  │  - generateUploadId()      - processChunk()              │  │
│  │  - splitFileIntoChunks()   - finalizeFileUpload()        │  │
│  │  - uploadChunk()           - createAndUploadReport()     │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
    ┌────────┐    ┌─────────┐    ┌─────────┐    ┌────────┐
    │   S3   │    │  Kafka  │    │  Redis  │    │ Health │
    │ Service│    │ Producer│    │ Service │    │ Monitor│
    └────────┘    └─────────┘    └─────────┘    └────────┘
```

### Processing Flow

1. **Initialization Phase**
   - Generate S3 multipart upload ID for output file
   - Split input file into optimal chunks based on file size and configuration

2. **Submission Phase**
   - Process chunks in parallel (configurable max parallelism)
   - Read CSV data from S3 in streaming mode
   - Split data into batches (default: 100 records)
   - Push batches to Kafka for downstream processing
   - Store batch metadata in Redis

3. **Monitoring Phase**
   - Poll Redis for completion status of submitted batches
   - Track success/failure rates
   - Collect report data if required

4. **Aggregation Phase**
   - Retrieve processed results from Redis
   - Parse and transform response data
   - Upload aggregated results as multipart chunks to S3

5. **Finalization Phase**
   - Complete S3 multipart upload
   - Generate optional CASS report
   - Upload report to S3

## 🔧 Prerequisites

- **Java**: JDK 21 or higher
- **Maven**: 3.6+ for building the project
- **Temporal Server**: Running Temporal cluster or local dev server
- **Apache Kafka**: For event streaming
- **Redis**: For state management
- **AWS S3**: For file storage (or S3-compatible storage)
- **Docker**: (Optional) For containerized deployment

## 📦 Installation

### Clone the Repository

```bash
git clone https://github.com/ArunKumarPal/temporal-worker.git
cd temporal-worker
```

### Build the Project

```bash
mvn clean install
```

### Run Locally

```bash
mvn mn:run
```

Or run the compiled JAR:

```bash
java -jar target/temporal-worker.jar
```

## ⚙️ Configuration

### Application Configuration (`application.yml`)

```yaml
micronaut:
  application:
    name: temporal-worker

# Temporal Configuration
temporal:
  hostport: ${TEMPORAL_HOSTPORT:127.0.0.1:7233}
  namespace: ${TEMPORAL_NAMESPACE:default}
  queue: ${TEMPORAL_QUEUE:bulk-processing-queue}
  client-cert:
    key: ${TEMPORAL_CLIENT_CERT_KEY:}
    pem: ${TEMPORAL_CLIENT_CERT_PEM:}
  encryption:
    enabled: ${TEMPORAL_ENCRYPTION_ENABLED:false}

# Kafka Configuration
kafka:
  bootstrap:
    servers: ${KAFKA_BOOTSTRAP_BROKERS:localhost:9092}
  security:
    protocol: SASL_SSL
  sasl:
    mechanism: AWS_MSK_IAM
  producers:
    default:
      retries: ${KAFKA_PRODUCER_RETIRES:3}
      acks: ${KAFKA_ACK_LEVEL:all}

# Redis Configuration
redis:
  uri: ${REDIS_URI:redis://localhost:6379}

# AWS Configuration
aws:
  env: ${AWS_ENV:dev}
  region: ${AWS_REGION:us-east-1}

# Processing Configuration
request-config:
  batch-size: ${BATCH_SIZE:100}

# S3 Client Configuration
s3-client:
  max-concurrency: ${MAX_CONCURRENCY:50}
  max-pending-connection-acquires: ${MAX_PENDING_CONNECTION_ACQUIRES:10000}
  connection-acquisition-timeout: ${CONNECTION_ACQUISITION_TIMEOUT:60}

# Chunk Processing Configuration
bulk.processor:
  min.chunk.size: ${MIN_CHUNK_SIZE:5242880}  # 5MB
  min.lines.per.chunk: ${MIN_LINES_PER_CHUNK:50000}
  max.chunk: ${MAX_PARALLEL_CHUNK:4}
  sample.lines: ${SAMPLE_LINES:100}

# Report Configuration
report-config:
  vendor-name: ${VENDOR_NAME:Precisely Software Inc.}
  software-name: ${SOFTWARE_NAME:Bulk API Geocode}
  software-version: ${SOFTWARE_VERSION:1.00.00.O}
```

### Environment Variables

Key environment variables to configure:

| Variable | Description | Default |
|----------|-------------|---------|
| `TEMPORAL_HOSTPORT` | Temporal server address | `127.0.0.1:7233` |
| `TEMPORAL_NAMESPACE` | Temporal namespace | `default` |
| `TEMPORAL_QUEUE` | Worker task queue name | `bulk-processing-queue` |
| `KAFKA_BOOTSTRAP_BROKERS` | Kafka broker addresses | `localhost:9092` |
| `REDIS_URI` | Redis connection URI | `redis://localhost:6379` |
| `AWS_REGION` | AWS region for S3 | `us-east-1` |
| `BATCH_SIZE` | Records per Kafka batch | `100` |
| `MAX_PARALLEL_CHUNK` | Max parallel chunks | `4` |
| `MIN_CHUNK_SIZE` | Minimum chunk size (bytes) | `5242880` (5MB) |
| `MIN_LINES_PER_CHUNK` | Min lines per chunk | `50000` |

## 💻 Usage

### Starting the Worker

The worker automatically starts when the application launches:

```bash
java -jar temporal-worker.jar
```

The worker will:
1. Connect to the Temporal server
2. Register workflows and activities
3. Start polling the configured task queue
4. Begin processing workflows as they arrive

### Workflow Execution

To execute a bulk processing workflow, use the Temporal client to start the workflow:

```java
BulkApiRequest request = BulkApiRequest.builder()
    .workspaceId("workspace-123")
    .emailId("user@example.com")
    .fileName("input.csv")
    .fileId("file-456")
    .outputFileId("output-789")
    .apiType("GEOCODE")
    .delimiter(",")
    .reportRequired("Y")
    .build();

WorkflowOptions options = WorkflowOptions.newBuilder()
    .setTaskQueue("bulk-processing-queue")
    .setWorkflowId("bulk-job-" + UUID.randomUUID())
    .build();

BulkWorkflow workflow = client.newWorkflowStub(
    BulkWorkflow.class, 
    options
);

BulkWorkflowResponse response = workflow.executeWorkflow(request);
```

### Input File Format

Input CSV files should be stored in S3 with the following structure:

```
s3://{bucket}/input/{workspaceId}/{emailId}/{fileName}_{fileId}.csv
```

Example CSV:
```csv
input1,input2,input3
test,test2,test3
test4,test5,test6
```

### Output Files

**Processed CSV Output:**
```
s3://{bucket}/output/{workspaceId}/{emailId}/{fileName}_{outputFileId}.csv
```

**Report Output (if enabled):**
```
s3://{bucket}/reports/{workspaceId}/{emailId}/{outputFileId}_report.txt
```

## 🔄 Workflow Details

### Workflow Activities

#### 1. `getMaxParallelChunks()`
Returns the maximum number of chunks that can be processed in parallel.

#### 2. `generateUploadId(GenerateUploadIdRequest)`
Initiates S3 multipart upload and returns upload ID.

#### 3. `splitFileIntoChunks(SplitChunkRequest)`
Analyzes input file and creates optimal chunk boundaries.
- Samples file to estimate average line size
- Calculates chunk boundaries based on size and line count constraints
- Returns list of FileChunk objects with start/end positions

#### 4. `uploadChunk(FileChunk, BulkApiRequest)`
Processes a single chunk:
- Reads CSV data from S3 within chunk boundaries
- Splits into batches (default: 100 records)
- Publishes batches to Kafka
- Stores batch metadata in Redis
- Returns total records and batches submitted

#### 5. `processChunk(ProcessChunkRequest)`
Monitors and aggregates chunk results:
- Polls Redis for completed batches
- Retrieves processed results
- Aggregates data for output
- Uploads chunk as multipart segment
- Collects report statistics
- Returns processing results and upload tag

#### 6. `finalizeFileUpload(CompleteMultipartUploadRequest)`
Completes S3 multipart upload with all part ETags.

#### 7. `createAndUploadReport(CreateReportRequest)`
Generates and uploads optional CASS report with processing statistics.

### Retry and Error Handling

**Activity Retry Policy:**
```java
RetryOptions:
  - initialInterval: 1 second
  - maximumInterval: 1 minute
  - backoffCoefficient: 2.0
  - maximumAttempts: 3
```

**Chunk-Level Fault Tolerance:**
- If a chunk fails, only that chunk is retried
- Other chunks continue processing independently
- Failed chunks are automatically retried per activity retry policy
- Non-retryable failures (e.g., missing S3 file) fail immediately

### Parallel Processing Strategy

The workflow uses dynamic parallelism control:

```java
Queue<FileChunk> chunkQueue = new LinkedList<>(listOfChunks);
List<Promise<ChunkProcessingResult>> running = new ArrayList<>();

while (!running.isEmpty() || !chunkQueue.isEmpty()) {
    // Start new chunks up to max parallelism
    while (running.size() < maxParallelChunks && !chunkQueue.isEmpty()) {
        FileChunk next = chunkQueue.poll();
        running.add(startChunkAsync(next, input));
    }
    
    // Wait for at least one to complete
    Promise.anyOf(running).get();
    
    // Process completed chunks
    processCompleted(running);
}
```

This ensures:
- Maximum parallelism is maintained
- No chunks are started until capacity is available
- Load on Kafka and Redis is controlled
- Temporal workflow history remains manageable

## 📚 API Documentation

### Health Check Endpoint

```
GET /health
```

Response:
```json
{
  "status": "UP",
  "details": {
    "temporal": "UP",
    "redis": "UP",
    "kafka": "UP"
  }
}
```

### Metrics Endpoint

```
GET /prometheus
```

Returns Prometheus-formatted metrics including:
- Workflow execution counts
- Activity execution times
- Worker capacity metrics
- Custom business metrics

## 🔨 Development

### Project Structure

```
src/
├── main/
│   ├── java/com/arun/temporal/worker/
│   │   ├── activities/              # Temporal activity implementations
│   │   │   ├── BulkActivities.java
│   │   │   └── BulkActivitiesImpl.java
│   │   ├── configuration/           # Application configuration
│   │   ├── constant/                # Constants and enums
│   │   ├── exception/               # Custom exceptions
│   │   ├── health/                  # Health check indicators
│   │   ├── kafka/                   # Kafka producers and consumers
│   │   ├── model/                   # Data models and DTOs
│   │   ├── redis/                   # Redis service implementations
│   │   ├── s3/                      # S3 utilities and services
│   │   ├── service/                 # Business services
│   │   ├── temporal/                # Temporal configurations
│   │   ├── util/                    # Utility classes
│   │   ├── worker/                  # Worker factory and management
│   │   └── workflow/                # Temporal workflow implementations
│   │       └── BulkWorkflow.java    # Main workflow interface
│   └── resources/
│       ├── application.yml          # Main configuration
│       └── logback.xml              # Logging configuration
└── test/
    └── java/                        # Unit and integration tests
```

### Adding New Activities

1. Define activity interface in `BulkActivities.java`:
```java
@ActivityMethod
YourResponse yourNewActivity(YourRequest request);
```

2. Implement in `BulkActivitiesImpl.java`:
```java
@Override
public YourResponse yourNewActivity(YourRequest request) {
    workerStatus.startActivity();
    try {
        // Your implementation
        return response;
    } finally {
        workerStatus.endActivity();
    }
}
```

3. Call from workflow:
```java
YourResponse response = bulkActivities.yourNewActivity(request);
```

### Code Quality Tools

The project includes:
- **JaCoCo**: Code coverage reporting
- **Checkstyle**: Code style validation
- **Lombok**: Reduces boilerplate code
- **SLF4J + Logback**: Structured logging with JSON support

## 🧪 Testing

### Run All Tests

```bash
mvn test
```

### Run with Coverage

```bash
mvn clean test jacoco:report
```

Coverage report: `target/site/jacoco/index.html`

### Test Structure

```
src/test/java/com/arun/temporal/worker/
├── activities/              # Activity unit tests
├── health/                  # Health check tests
├── s3/                      # S3 service tests
├── service/                 # Service layer tests
├── util/                    # Utility tests
├── worker/                  # Worker tests
└── workflow/                # Workflow integration tests
```

### Writing Tests

```java
@MicronautTest
class BulkActivitiesImplTest {
    
    @Inject
    BulkActivities bulkActivities;
    
    @Test
    void testSplitFileIntoChunks() {
        SplitChunkRequest request = new SplitChunkRequest("test-key", ",");
        FileChunkListResponse response = bulkActivities.splitFileIntoChunks(request);
        
        assertNotNull(response);
        assertTrue(response.chunks().size() > 0);
    }
}
```

## 🚀 Deployment

### Docker Build

The project includes a Dockerfile for containerized deployment using BellSoft Liberica OpenJDK 21 Alpine base image.

```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t temporal-worker:latest .
```

**Docker Image Details:**
- **Base Image**: `bellsoft/liberica-openjdk-alpine:21`
- **User**: Non-root user (`nonroot`)
- **Working Directory**: `/opt/temporal-worker`
- **Exposed Port**: `8080`
- **JVM Configuration**: G1GC with optimized heap settings (2GB-4GB)

### Docker Run

```bash
docker run -d \
  --name temporal-worker \
  -e TEMPORAL_HOSTPORT=temporal:7233 \
  -e KAFKA_BOOTSTRAP_BROKERS=kafka:9092 \
  -e REDIS_URI=redis://redis:6379 \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=your-access-key \
  -e AWS_SECRET_ACCESS_KEY=your-secret-key \
  -p 8080:8080 \
  temporal-worker:latest
```

### Push to Registry

```bash
# Tag the image
docker tag temporal-worker:latest your-registry.com/temporal-worker:0.1

# Push to registry
docker push your-registry.com/temporal-worker:0.1
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: temporal-worker
spec:
  replicas: 3
  selector:
    matchLabels:
      app: temporal-worker
  template:
    metadata:
      labels:
        app: temporal-worker
    spec:
      containers:
      - name: worker
        image: temporal-worker:latest
        ports:
        - containerPort: 8080
        env:
        - name: TEMPORAL_HOSTPORT
          value: "temporal-frontend:7233"
        - name: KAFKA_BOOTSTRAP_BROKERS
          value: "kafka:9092"
        - name: REDIS_URI
          value: "redis://redis:6379"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

### JVM Tuning

Production JVM settings (configured in Dockerfile):

```bash
java -XX:+UseCompressedOops \
     -XX:+UseG1GC \
     -XX:InitiatingHeapOccupancyPercent=30 \
     -XX:MaxGCPauseMillis=10000 \
     -XX:MaxRAMPercentage=70.0 \
     -Xms2g \
     -Xmx4g \
     -jar temporal-worker.jar
```

## 📊 Monitoring

### Logging

The application uses structured JSON logging with MDC context:

```json
{
  "timestamp": "2025-11-29T10:15:30.123Z",
  "level": "INFO",
  "logger": "BulkActivitiesImpl",
  "message": "Processing chunk 1 of 10",
  "workspaceId": "workspace-123",
  "emailId": "user@example.com",
  "outputFileId": "output-789",
  "queryId": "input.csv_output-789"
}
```

### Metrics

Key metrics to monitor:

- **Workflow Metrics:**
  - Workflow execution time
  - Workflow success/failure rate
  - Active workflow count

- **Activity Metrics:**
  - Activity execution time per activity type
  - Activity retry rate
  - Activity failure rate

- **Business Metrics:**
  - Records processed per second
  - Chunks processed
  - File processing time
  - Kafka throughput
  - Redis operations

### Temporal UI

Access Temporal UI to monitor workflows:

```
http://localhost:8088
```

Features:
- View running workflows
- Inspect workflow history
- Retry failed workflows
- Monitor worker status

## 🔍 Troubleshooting

### Common Issues

#### 1. Worker Not Picking Up Workflows

**Symptoms:** Workflows stay in pending state

**Solutions:**
- Check worker is connected to correct Temporal server
- Verify task queue name matches workflow configuration
- Check worker logs for connection errors
- Ensure worker has sufficient capacity

#### 2. Chunk Processing Failures

**Symptoms:** Activities repeatedly failing

**Solutions:**
- Check S3 bucket permissions
- Verify Kafka broker connectivity
- Confirm Redis is accessible
- Review activity retry configuration
- Check input file format and encoding

#### 3. Memory Issues

**Symptoms:** OutOfMemoryError

**Solutions:**
- Reduce `MAX_PARALLEL_CHUNK` value
- Decrease `MIN_LINES_PER_CHUNK`
- Increase JVM heap size (-Xmx)
- Review chunk size configuration
- Check for memory leaks in custom code

#### 4. Kafka Publishing Delays

**Symptoms:** Slow chunk submission

**Solutions:**
- Increase Kafka producer batch size
- Adjust Kafka producer linger.ms
- Check network latency to Kafka
- Review Kafka broker performance
- Reduce `BATCH_SIZE` if batches are too large

#### 5. S3 Upload Timeouts

**Symptoms:** Multipart upload failures

**Solutions:**
- Increase `CONNECTION_ACQUISITION_TIMEOUT`
- Increase `MAX_CONCURRENCY`
- Check network connectivity to S3
- Verify S3 bucket exists and is accessible
- Review S3 throttling limits

### Debug Logging

Enable debug logging for specific components:

```yaml
logger:
  levels:
    com.arun.temporal.worker.activities: DEBUG
    com.arun.temporal.worker.workflow: DEBUG
    io.temporal: DEBUG
```

### Support and Contact

For issues and questions:
- **GitHub Issues**: [Create an issue](https://github.com/ArunKumarPal/temporal-worker/issues)
- **Owner/Architect/Developer**: Arun Pal

## 👨‍💻 Author

**Arun Pal**
- Role: Owner, Architect & Developer
- GitHub: [@ArunPal](https://github.com/ArunKumarPal)

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 Changelog

### Version 0.1 (Current)
- Initial release
- Bulk CSV processing with chunking
- Kafka integration for batch publishing
- Redis state management
- S3 multipart upload
- CASS report generation
- Temporal workflow orchestration
- Health checks and monitoring
- Docker containerization support
- Developed by Arun Pal

---

**Built with ❤️ using Temporal and Micronaut by Arun Pal**