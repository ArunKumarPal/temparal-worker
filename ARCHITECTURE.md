# Architecture Documentation

## System Architecture

This document provides detailed architecture diagrams for the Temporal Worker - Bulk CSV File Processor.

---

## 1. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            External Services Layer                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │   AWS S3     │  │   Kafka      │  │    Redis     │  │   Temporal   │   │
│  │   Storage    │  │   Cluster    │  │   Cluster    │  │    Server    │   │
│  │              │  │              │  │              │  │              │   │
│  │ - Input CSV  │  │ - Brokers    │  │ - State Mgmt │  │ - Workflows  │   │
│  │ - Output CSV │  │ - Topics     │  │ - Job Status │  │ - Activities │   │
│  │ - Reports    │  │ - Batches    │  │ - Results    │  │ - History    │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
│         │                  │                  │                  │           │
└─────────┼──────────────────┼──────────────────┼──────────────────┼───────────┘
          │                  │                  │                  │
          └──────────┬───────┴────────┬─────────┴──────────┬───────┘
                     │                │                     │
┌────────────────────┼────────────────┼─────────────────────┼────────────────┐
│                    │                │                     │                 │
│              Temporal Worker Application Layer                              │
│                    │                │                     │                 │
│  ┌─────────────────▼────────────────▼─────────────────────▼──────────────┐ │
│  │                     Micronaut Framework (v4.4.3)                       │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │ │
│  │  │                    Temporal Worker Factory                        │ │ │
│  │  │  - Worker Registration                                            │ │ │
│  │  │  - Task Queue Polling (bulk-processing-queue)                    │ │ │
│  │  │  - Concurrency Management (2 workflows, 4 activities)            │ │ │
│  │  └──────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                         │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │ │
│  │  │                      Workflow Layer                               │ │ │
│  │  │  ┌──────────────────────────────────────────────────────────┐   │ │ │
│  │  │  │          BulkWorkflow (Workflow Interface)                │   │ │ │
│  │  │  │  - executeWorkflow(BulkApiRequest)                        │   │ │ │
│  │  │  │  - Returns: BulkWorkflowResponse                          │   │ │ │
│  │  │  └──────────────────────────────────────────────────────────┘   │ │ │
│  │  │  ┌──────────────────────────────────────────────────────────┐   │ │ │
│  │  │  │       BulkWorkflowImpl (Implementation)                   │   │ │ │
│  │  │  │  - Orchestration Logic                                    │   │ │ │
│  │  │  │  - Parallel Chunk Management                              │   │ │ │
│  │  │  │  - Dynamic Task Scheduling                                │   │ │ │
│  │  │  │  - Error Recovery & Retry Coordination                    │   │ │ │
│  │  │  └──────────────────────────────────────────────────────────┘   │ │ │
│  │  └──────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                         │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │ │
│  │  │                      Activity Layer                               │ │ │
│  │  │  ┌──────────────────────────────────────────────────────────┐   │ │ │
│  │  │  │         BulkActivities (Activity Interface)               │   │ │ │
│  │  │  │  - getMaxParallelChunks()                                 │   │ │ │
│  │  │  │  - generateUploadId()                                     │   │ │ │
│  │  │  │  - splitFileIntoChunks()                                  │   │ │ │
│  │  │  │  - uploadChunk()                                          │   │ │ │
│  │  │  │  - processChunk()                                         │   │ │ │
│  │  │  │  - finalizeFileUpload()                                   │   │ │ │
│  │  │  │  - createAndUploadReport()                                │   │ │ │
│  │  │  └──────────────────────────────────────────────────────────┘   │ │ │
│  │  │  ┌──────────────────────────────────────────────────────────┐   │ │ │
│  │  │  │        BulkActivitiesImpl (Implementation)                │   │ │ │
│  │  │  │  - Business Logic Execution                               │   │ │ │
│  │  │  │  - Service Orchestration                                  │   │ │ │
│  │  │  │  - Worker Status Tracking                                 │   │ │ │
│  │  │  └──────────────────────────────────────────────────────────┘   │ │ │
│  │  └──────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                         │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Service Layer                                │ │ │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │ │ │
│  │  │  │    S3    │  │  Kafka   │  │  Redis   │  │  Report  │        │ │ │
│  │  │  │ Service  │  │ Producer │  │ Service  │  │   Util   │        │ │ │
│  │  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │ │ │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐                      │ │ │
│  │  │  │   CSV    │  │Encryption│  │  Health  │                      │ │ │
│  │  │  │Converter │  │ Service  │  │  Monitor │                      │ │ │
│  │  │  └──────────┘  └──────────┘  └──────────┘                      │ │ │
│  │  └──────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                         │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │ │
│  │  │                    Utility & Support Layer                        │ │ │
│  │  │  - File Chunk Reader        - MDC Logging                        │ │ │
│  │  │  - S3 Utilities            - JSON Helper                         │ │ │
│  │  │  - Address Reader           - Report Utilities                   │ │ │
│  │  │  - Custom Context Propagator                                     │ │ │
│  │  └──────────────────────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                     Configuration & Infrastructure                       │ │
│  │  - Application.yml          - AWS Configuration                          │ │
│  │  - Temporal Configuration   - S3 Client Configuration                    │ │
│  │  - Kafka Configuration      - Redis Configuration                        │ │
│  │  - Logback (JSON Logging)   - Health Endpoints                           │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Temporal Worker Application                      │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                            WORKFLOW LAYER                                 │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ BulkWorkflow                                                        │  │
│  │  ├─ Workflow Orchestration                                         │  │
│  │  │   ├─ Chunk Queue Management                                     │  │
│  │  │   ├─ Parallel Execution Control                                 │  │
│  │  │   └─ Promise-based Async Coordination                           │  │
│  │  │                                                                  │  │
│  │  ├─ Activity Invocation                                            │  │
│  │  │   ├─ Activity Stub Creation                                     │  │
│  │  │   ├─ Retry Policy Configuration                                 │  │
│  │  │   └─ Timeout Management                                         │  │
│  │  │                                                                  │  │
│  │  └─ State Management                                               │  │
│  │      ├─ Record Count Tracking                                      │  │
│  │      ├─ Chunk Status Monitoring                                    │  │
│  │      └─ Report Data Aggregation                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           ACTIVITY LAYER                                  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ BulkActivities Interface                                           │  │
│  │                                                                     │  │
│  │  Activity 1: getMaxParallelChunks()                                │  │
│  │  ├─ Returns: MaxParallelChunkResponse                              │  │
│  │  └─ Purpose: Get parallelism configuration                         │  │
│  │                                                                     │  │
│  │  Activity 2: generateUploadId(GenerateUploadIdRequest)             │  │
│  │  ├─ Returns: GenerateUploadIdResponse (S3 Upload ID)               │  │
│  │  └─ Purpose: Initialize S3 multipart upload                        │  │
│  │                                                                     │  │
│  │  Activity 3: splitFileIntoChunks(SplitChunkRequest)                │  │
│  │  ├─ Returns: FileChunkListResponse (List<FileChunk>)               │  │
│  │  └─ Purpose: Analyze file and create chunk boundaries              │  │
│  │                                                                     │  │
│  │  Activity 4: uploadChunk(FileChunk, BulkApiRequest)                │  │
│  │  ├─ Returns: ChunkSubmitResult (records, batches)                  │  │
│  │  └─ Purpose: Read chunk, batch data, publish to Kafka              │  │
│  │                                                                     │  │
│  │  Activity 5: processChunk(ProcessChunkRequest)                     │  │
│  │  ├─ Returns: ChunkProcessingResult (status, etag, report data)     │  │
│  │  └─ Purpose: Monitor Redis, aggregate results, upload to S3        │  │
│  │                                                                     │  │
│  │  Activity 6: finalizeFileUpload(CompleteMultipartUploadRequest)    │  │
│  │  ├─ Returns: void                                                  │  │
│  │  └─ Purpose: Complete S3 multipart upload with all parts           │  │
│  │                                                                     │  │
│  │  Activity 7: createAndUploadReport(CreateReportRequest)            │  │
│  │  ├─ Returns: void                                                  │  │
│  │  └─ Purpose: Generate CASS report and upload to S3                 │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                            SERVICE LAYER                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │  S3 Service  │  │KafkaProducer │  │RedisService  │                   │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤                   │
│  │ getUploadId  │  │ sendEvent()  │  │ get()        │                   │
│  │ uploadPart   │  │ batching     │  │ set()        │                   │
│  │ complete     │  │ headers      │  │ delete()     │                   │
│  │ download     │  │ serialization│  │ exists()     │                   │
│  └──────────────┘  └──────────────┘  └──────────────┘                   │
│                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │S3Aggregator  │  │ ReportUtil   │  │ Encryption   │                   │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤                   │
│  │ convertCSV   │  │ generatePDF  │  │ encrypt()    │                   │
│  │ aggregate    │  │ formatReport │  │ decrypt()    │                   │
│  │ uploadPart   │  │ uploadReport │  │ hashData()   │                   │
│  └──────────────┘  └──────────────┘  └──────────────┘                   │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │ AWS S3 SDK   │  │ Kafka Client │  │ Redis Client │                   │
│  │ (Async)      │  │ (Micronaut)  │  │ (Lettuce)    │                   │
│  └──────────────┘  └──────────────┘  └──────────────┘                   │
│                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │ Temporal SDK │  │ Health Check │  │ Metrics      │                   │
│  │ (1.30.1)     │  │ Endpoints    │  │ (Prometheus) │                   │
│  └──────────────┘  └──────────────┘  └──────────────┘                   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           INPUT DATA FLOW                                │
└─────────────────────────────────────────────────────────────────────────┘

     ┌─────────────┐
     │   S3 Bucket │
     │  input.csv  │
     └──────┬──────┘
            │
            │ (1) Download & Analyze
            ▼
     ┌──────────────────┐
     │ splitFileInto    │
     │    Chunks()      │
     │                  │
     │ • Sample lines   │
     │ • Estimate size  │
     │ • Calculate      │
     │   boundaries     │
     └──────┬───────────┘
            │
            │ (2) Returns: List<FileChunk>
            │     [Chunk1, Chunk2, Chunk3, ...]
            ▼
     ┌─────────────────────┐
     │  Workflow Queue     │
     │  Management         │
     │                     │
     │  Queue: [C1,C2,C3]  │
     │  Running: []        │
     │  Max Parallel: 4    │
     └──────┬──────────────┘
            │
            │ (3) Start chunks (up to max parallel)
            ▼
     ┌─────────────────────────────────────────────┐
     │       Parallel Chunk Processing             │
     │                                             │
     │  Chunk 1        Chunk 2        Chunk 3      │
     │     │              │              │         │
     │     ▼              ▼              ▼         │
     │ uploadChunk()  uploadChunk()  uploadChunk()│
     └─────────────────────────────────────────────┘
            │
            │ (4) For each chunk
            ▼
     ┌─────────────────────┐
     │  uploadChunk()      │
     │                     │
     │  Read CSV data      │
     │  from S3 (stream)   │
     │  ▼                  │
     │  Split into batches │
     │  (100 records each) │
     │  ▼                  │
     │  Batch 1: [R1..R100]│
     │  Batch 2: [R101..R200]│
     │  ...                │
     └──────┬──────────────┘
            │
            │ (5) For each batch
            ▼
     ┌─────────────────────┐
     │  Kafka Producer     │
     │                     │
     │  Publish event:     │
     │  {                  │
     │   batchId: "..."    │
     │   records: [...]    │
     │   metadata: {...}   │
     │  }                  │
     └──────┬──────────────┘
            │
            │ (6) Store batch metadata
            ▼
     ┌─────────────────────┐
     │  Redis              │
     │                     │
     │  Key: batch_{id}    │
     │  Value: {           │
     │    status: "PENDING"│
     │    recordCount: 100 │
     │  }                  │
     └─────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       PROCESSING DATA FLOW                               │
└─────────────────────────────────────────────────────────────────────────┘

     ┌─────────────────────┐
     │  External Service   │
     │  (Consumes Kafka)   │
     │                     │
     │  • Process records  │
     │  • Generate results │
     │  • Update Redis     │
     └──────┬──────────────┘
            │
            │ (7) Updates batch status
            ▼
     ┌─────────────────────┐
     │  Redis              │
     │                     │
     │  Key: batch_{id}    │
     │  Value: {           │
     │    status: "COMPLETED"│
     │    results: [...]   │
     │    timestamp: ...   │
     │  }                  │
     └──────┬──────────────┘
            │
            │ (8) Worker polls Redis
            ▼
     ┌─────────────────────┐
     │  processChunk()     │
     │                     │
     │  • Poll Redis       │
     │    for completion   │
     │  • Retrieve results │
     │  • Aggregate data   │
     │  • Generate CSV     │
     └──────┬──────────────┘
            │
            │ (9) Upload as multipart
            ▼
     ┌─────────────────────┐
     │  S3 Multipart       │
     │  Upload             │
     │                     │
     │  Part 1: Chunk 1    │
     │  Part 2: Chunk 2    │
     │  Part 3: Chunk 3    │
     │  ...                │
     │                     │
     │  uploadId: "xyz123" │
     └──────┬──────────────┘
            │
            │ (10) Collect report data
            ▼
     ┌─────────────────────┐
     │  Report Aggregation │
     │                     │
     │  • Total records    │
     │  • Success count    │
     │  • Error count      │
     │  • Statistics       │
     └──────┬──────────────┘
            │
            │ (11) All chunks complete
            ▼
     ┌─────────────────────────────────┐
     │  finalizeFileUpload()           │
     │                                 │
     │  Complete multipart with:       │
     │  • uploadId                     │
     │  • List of ETags from all parts │
     └──────┬──────────────────────────┘
            │
            │ (12) Create final output
            ▼
     ┌─────────────────────┐
     │  S3 Output          │
     │                     │
     │  output.csv         │
     │  (Combined from     │
     │   all parts)        │
     └─────────────────────┘
            │
            │ (13) Generate report (if required)
            ▼
     ┌─────────────────────┐
     │ createAndUpload     │
     │    Report()         │
     │                     │
     │ • Format report     │
     │ • Include stats     │
     │ • Upload to S3      │
     └──────┬──────────────┘
            │
            ▼
     ┌─────────────────────┐
     │  S3 Report          │
     │                     │
     │  report.txt         │
     │  (CASS format)      │
     └─────────────────────┘
```

---

## 4. Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         KUBERNETES CLUSTER                               │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                         Namespace: temporal-workers                │ │
│  │                                                                    │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  Deployment: temporal-worker                                │  │ │
│  │  │  Replicas: 3                                                │  │ │
│  │  │                                                             │  │ │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │  │ │
│  │  │  │   Pod 1     │  │   Pod 2     │  │   Pod 3     │        │  │ │
│  │  │  │             │  │             │  │             │        │  │ │
│  │  │  │  Worker App │  │  Worker App │  │  Worker App │        │  │ │
│  │  │  │  :8080      │  │  :8080      │  │  :8080      │        │  │ │
│  │  │  │             │  │             │  │             │        │  │ │
│  │  │  │  Resources: │  │  Resources: │  │  Resources: │        │  │ │
│  │  │  │  CPU: 1-2   │  │  CPU: 1-2   │  │  CPU: 1-2   │        │  │ │
│  │  │  │  Mem: 2-4Gi │  │  Mem: 2-4Gi │  │  Mem: 2-4Gi │        │  │ │
│  │  │  └─────────────┘  └─────────────┘  └─────────────┘        │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                                                                    │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  Service: temporal-worker-svc                               │  │ │
│  │  │  Type: ClusterIP                                            │  │ │
│  │  │  Port: 8080                                                 │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                                                                    │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  ConfigMap: worker-config                                   │  │ │
│  │  │  - application.yml                                          │  │ │
│  │  │  - logback.xml                                              │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                                                                    │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  Secret: worker-secrets                                     │  │ │
│  │  │  - AWS_ACCESS_KEY_ID                                        │  │ │
│  │  │  - AWS_SECRET_ACCESS_KEY                                    │  │ │
│  │  │  - TEMPORAL_CLIENT_CERT_KEY                                 │  │ │
│  │  │  - TEMPORAL_CLIENT_CERT_PEM                                 │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│         │                    │                    │                     │
│         ▼                    ▼                    ▼                     │
│  ┌─────────────┐      ┌─────────────┐     ┌─────────────┐             │
│  │  Temporal   │      │   Kafka     │     │   Redis     │             │
│  │  Service    │      │   Service   │     │   Service   │             │
│  └─────────────┘      └─────────────┘     └─────────────┘             │
└──────────────────────────────────────────────────────────────────────────┘
                                │
                                │ External
                                ▼
                         ┌─────────────┐
                         │   AWS S3    │
                         │   Buckets   │
                         └─────────────┘
```

---

## 5. Concurrency & Parallelism Model

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      WORKFLOW CONCURRENCY MODEL                          │
└─────────────────────────────────────────────────────────────────────────┘

Worker Configuration:
├─ maxConcurrentWorkflowTaskPollers: 2
├─ maxConcurrentActivityTaskPollers: 4
├─ maxConcurrentWorkflowTaskExecutionSize: 2
└─ maxConcurrentActivityExecutionSize: 4

┌─────────────────────────────────────────────────────────────────────────┐
│  Worker Instance                                                         │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  Workflow Execution 1                                              │ │
│  │  ├─ File: large_file_1.csv (1M records)                           │ │
│  │  └─ Status: Processing                                            │ │
│  │                                                                    │ │
│  │     ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │ │
│  │     │  Activity 1  │  │  Activity 2  │  │  Activity 3  │         │ │
│  │     │  Chunk 1     │  │  Chunk 2     │  │  Chunk 3     │         │ │
│  │     │  Processing  │  │  Processing  │  │  Processing  │         │ │
│  │     └──────────────┘  └──────────────┘  └──────────────┘         │ │
│  │                                                                    │ │
│  │     ┌──────────────┐                                              │ │
│  │     │  Activity 4  │                                              │ │
│  │     │  Chunk 4     │                                              │ │
│  │     │  Processing  │                                              │ │
│  │     └──────────────┘                                              │ │
│  │                                                                    │ │
│  │     [Chunk 5, Chunk 6, ... Chunk N]  ← Waiting in Queue          │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  Workflow Execution 2                                              │ │
│  │  ├─ File: large_file_2.csv (500K records)                         │ │
│  │  └─ Status: Starting                                              │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘

Chunk Processing Flow:
┌────────────────────────────────────────────────────────────────────────┐
│  Dynamic Parallelism Control                                           │
│                                                                         │
│  Initial State:                                                        │
│    chunkQueue = [C1, C2, C3, C4, C5, C6, C7, C8, C9, C10]             │
│    running = []                                                        │
│    maxParallel = 4                                                     │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  Iteration 1: Start 4 chunks                                 │    │
│  │    chunkQueue = [C5, C6, C7, C8, C9, C10]                    │    │
│  │    running = [Promise<C1>, Promise<C2>, Promise<C3>, Promise<C4>] │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  Wait: Promise.anyOf(running) → C2 completes                 │    │
│  │    running = [Promise<C1>, Promise<C3>, Promise<C4>]         │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  Iteration 2: Start 1 more chunk (C5)                        │    │
│  │    chunkQueue = [C6, C7, C8, C9, C10]                        │    │
│  │    running = [Promise<C1>, Promise<C3>, Promise<C4>, Promise<C5>] │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ... continues until all chunks processed                              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Error Handling & Retry Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      ERROR HANDLING HIERARCHY                            │
└─────────────────────────────────────────────────────────────────────────┘

Level 1: Activity Retry (Automatic)
┌────────────────────────────────────────────────────────────────────────┐
│  Activity Execution Attempt                                             │
│  ├─ Attempt 1: FAILED (Connection timeout)                            │
│  │   └─ Wait: 1 second (initialInterval)                              │
│  ├─ Attempt 2: FAILED (S3 throttling)                                 │
│  │   └─ Wait: 2 seconds (backoffCoefficient: 2.0)                     │
│  ├─ Attempt 3: SUCCESS                                                │
│  └─ Result: Activity completed successfully                           │
│                                                                         │
│  Retry Configuration:                                                  │
│  ├─ initialInterval: 1 second                                         │
│  ├─ maximumInterval: 1 minute                                         │
│  ├─ backoffCoefficient: 2.0                                           │
│  ├─ maximumAttempts: 3                                                │
│  └─ Non-retryable exceptions: NoSuchKeyException, etc.                │
└────────────────────────────────────────────────────────────────────────┘

Level 2: Chunk-Level Retry (Workflow Logic)
┌────────────────────────────────────────────────────────────────────────┐
│  Chunk Processing                                                       │
│  ├─ Chunk 1: SUCCESS ✓                                                │
│  ├─ Chunk 2: FAILED (after 3 attempts) ✗                              │
│  ├─ Chunk 3: SUCCESS ✓                                                │
│  ├─ Chunk 4: SUCCESS ✓                                                │
│  └─ Chunk 2: RETRY (requeued by workflow) → SUCCESS ✓                 │
│                                                                         │
│  Benefit: Only failed chunk is reprocessed, not entire file            │
└────────────────────────────────────────────────────────────────────────┘

Level 3: Workflow Retry (Temporal Framework)
┌────────────────────────────────────────────────────────────────────────┐
│  Workflow Execution                                                     │
│  └─ Non-retryable failure (e.g., S3 bucket not found)                 │
│      └─ Workflow marked as FAILED                                     │
│          └─ Manual intervention required                              │
│                                                                         │
│  Temporal ensures:                                                     │
│  ├─ Complete workflow history preserved                               │
│  ├─ State recovery on worker restart                                  │
│  └─ Ability to manually retry or compensate                           │
└────────────────────────────────────────────────────────────────────────┘

Error Categories:
┌────────────────────────────────────────────────────────────────────────┐
│  Retryable Errors (Automatic retry)                                    │
│  ├─ Network timeouts                                                   │
│  ├─ Service throttling (S3, Kafka, Redis)                             │
│  ├─ Temporary service unavailability                                  │
│  └─ Transient failures                                                │
│                                                                         │
│  Non-Retryable Errors (Immediate failure)                             │
│  ├─ NoSuchKeyException (S3 file not found)                            │
│  ├─ Invalid input file format                                         │
│  ├─ Authentication/authorization failures                             │
│  └─ Business logic validation failures                                │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Technology Stack

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          TECHNOLOGY STACK                                │
└─────────────────────────────────────────────────────────────────────────┘

Core Framework:
├─ Micronaut 4.4.3
│  ├─ Dependency Injection
│  ├─ HTTP Server (Netty)
│  ├─ Configuration Management
│  └─ Health & Metrics

Workflow Engine:
├─ Temporal SDK 1.30.1
│  ├─ Workflow Orchestration
│  ├─ Activity Execution
│  ├─ Retry & Timeout Management
│  └─ State Persistence

Language & Runtime:
├─ Java 21 (OpenJDK)
│  ├─ Virtual Threads (Project Loom)
│  ├─ Pattern Matching
│  └─ Records

Build & Dependencies:
├─ Maven 3.x
│  ├─ Dependency Management
│  ├─ Build Lifecycle
│  └─ Plugin Execution

Messaging & Streaming:
├─ Apache Kafka
│  ├─ Micronaut Kafka
│  ├─ AWS MSK IAM Auth
│  └─ Producer API

Data Storage:
├─ AWS S3
│  ├─ AWS SDK v2
│  ├─ Async Client (Netty)
│  └─ Multipart Upload
├─ Redis
│  ├─ Lettuce Client
│  └─ State Management

Data Processing:
├─ OpenCSV 5.12.0
├─ Apache Commons CSV 1.11.0
└─ Jackson (JSON processing)

Logging & Monitoring:
├─ Logback 1.5.13
├─ Logstash Encoder 7.4
├─ SLF4J
├─ Micrometer
└─ Prometheus Exporter

Testing:
├─ JUnit 5
├─ Mockito
├─ Micronaut Test
├─ Reactor Test
└─ JaCoCo (Coverage)

Utilities:
├─ Lombok 1.18.34
├─ Project Reactor
└─ RxJava2

Container & Deployment:
├─ Docker
│  ├─ BellSoft Liberica OpenJDK Alpine
│  └─ Multi-stage builds
└─ Kubernetes
   ├─ Deployments
   ├─ Services
   ├─ ConfigMaps
   └─ Secrets
```

---

## 8. Security Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          SECURITY LAYERS                                 │
└─────────────────────────────────────────────────────────────────────────┘

Application Security:
├─ Non-root Container Execution
│  └─ User: nonroot (UID: non-zero)
│
├─ Secrets Management
│  ├─ Kubernetes Secrets
│  │  ├─ AWS Credentials
│  │  ├─ Temporal Certificates
│  │  └─ Encryption Keys
│  └─ Environment Variable Injection
│
├─ Data Encryption
│  ├─ In-Transit: TLS/SSL
│  │  ├─ S3: HTTPS
│  │  ├─ Kafka: SASL_SSL
│  │  └─ Redis: TLS (optional)
│  └─ At-Rest: AWS S3 encryption
│
└─ Authentication & Authorization
   ├─ AWS IAM Roles
   ├─ Kafka MSK IAM Auth
   └─ Temporal mTLS (optional)

Network Security:
├─ Private Network Communication
├─ Service-to-Service Authentication
└─ Egress/Ingress Rules
```

---

**Architecture Documentation by Arun Kumar Pal**  
*Owner, Architect & Developer*
