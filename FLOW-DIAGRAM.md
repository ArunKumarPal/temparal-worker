# Flow Diagrams

This document provides detailed flow diagrams for the Temporal Worker - Bulk CSV File Processor.

---

## 1. End-to-End Processing Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         COMPLETE WORKFLOW FLOW                              │
└─────────────────────────────────────────────────────────────────────────────┘

START: Workflow Triggered
│
├─ Input: BulkApiRequest
│  ├─ workspaceId
│  ├─ emailId
│  ├─ fileName
│  ├─ fileId
│  ├─ outputFileId
│  ├─ apiType
│  ├─ delimiter
│  └─ reportRequired
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: INITIALIZATION                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
│
├─ Step 1.1: Get Max Parallel Chunks
│  └─ Activity: getMaxParallelChunks()
│     └─ Returns: maxParallelChunks = 4 (configurable)
│
├─ Step 1.2: Generate S3 Upload ID
│  └─ Activity: generateUploadId(fileUploadKey)
│     ├─ Initialize multipart upload
│     └─ Returns: uploadId = "xyz123..."
│
├─ Step 1.3: Split File into Chunks
│  └─ Activity: splitFileIntoChunks(fileInputKey, delimiter)
│     ├─ Get file size from S3
│     ├─ Sample first N lines (default: 100)
│     ├─ Estimate average line size
│     ├─ Calculate optimal chunk boundaries
│     │  ├─ Min chunk size: 5MB
│     │  └─ Min lines per chunk: 50,000
│     └─ Returns: List<FileChunk>
│        ├─ Chunk 1: [startByte: 0, endByte: 5242880, lines: ~50000]
│        ├─ Chunk 2: [startByte: 5242881, endByte: 10485760, ...]
│        └─ ... (N chunks total)
│
├─ Step 1.4: Initialize Tracking Variables
│  ├─ successfulRecordCount = 0
│  ├─ totalRecordCount = 0
│  ├─ partDetails = []
│  ├─ finalReportData = new ReportData()
│  └─ chunkQueue = LinkedList(chunks)
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 2: PARALLEL CHUNK SUBMISSION                                          │
└─────────────────────────────────────────────────────────────────────────────┘
│
├─ While (chunkQueue not empty OR running tasks exist)
│  │
│  ├─ Step 2.1: Start New Chunks (up to max parallel)
│  │  │
│  │  ├─ While (running.size < maxParallelChunks AND chunkQueue not empty)
│  │  │  │
│  │  │  ├─ Poll next chunk from queue
│  │  │  │
│  │  │  ├─ Activity: uploadChunk(chunk, bulkApiRequest)
│  │  │  │  │
│  │  │  │  ├─ Step 2.1.1: Read CSV Data from S3
│  │  │  │  │  ├─ Download chunk byte range
│  │  │  │  │  └─ Stream data (memory efficient)
│  │  │  │  │
│  │  │  │  ├─ Step 2.1.2: Parse CSV
│  │  │  │  │  ├─ Split by delimiter
│  │  │  │  │  ├─ Handle headers
│  │  │  │  │  └─ Validate format
│  │  │  │  │
│  │  │  │  ├─ Step 2.1.3: Batch Records
│  │  │  │  │  ├─ Group into batches (100 records each)
│  │  │  │  │  │  ├─ Batch 1: Records 1-100
│  │  │  │  │  │  ├─ Batch 2: Records 101-200
│  │  │  │  │  │  └─ ...
│  │  │  │  │  └─ totalBatches = ceil(totalRecords / batchSize)
│  │  │  │  │
│  │  │  │  ├─ Step 2.1.4: Publish to Kafka
│  │  │  │  │  │
│  │  │  │  │  ├─ For each batch:
│  │  │  │  │  │  ├─ Create KafkaEvent
│  │  │  │  │  │  │  ├─ batchId = UUID
│  │  │  │  │  │  │  ├─ chunkNumber
│  │  │  │  │  │  │  ├─ records = [...]
│  │  │  │  │  │  │  └─ metadata
│  │  │  │  │  │  │
│  │  │  │  │  │  ├─ Add headers:
│  │  │  │  │  │  │  ├─ type: "kafkaEvent"
│  │  │  │  │  │  │  ├─ schemaVersion: "1"
│  │  │  │  │  │  │  └─ messageId: UUID
│  │  │  │  │  │  │
│  │  │  │  │  │  └─ Producer.send(topic, event)
│  │  │  │  │  │
│  │  │  │  │  └─ Log: "Batch X published to Kafka"
│  │  │  │  │
│  │  │  │  ├─ Step 2.1.5: Store Batch Metadata in Redis
│  │  │  │  │  │
│  │  │  │  │  ├─ For each batch:
│  │  │  │  │  │  ├─ Key: "batch_{batchId}"
│  │  │  │  │  │  └─ Value: {
│  │  │  │  │  │     "status": "PENDING",
│  │  │  │  │  │     "chunkNumber": N,
│  │  │  │  │  │     "recordCount": 100,
│  │  │  │  │  │     "timestamp": "..."
│  │  │  │  │  │    }
│  │  │  │  │  │
│  │  │  │  │  └─ Log: "Batch metadata stored in Redis"
│  │  │  │  │
│  │  │  │  └─ Returns: ChunkSubmitResult
│  │  │  │     ├─ totalRecords
│  │  │  │     └─ totalBatches
│  │  │  │
│  │  │  ├─ Update: totalRecordCount += totalRecords
│  │  │  │
│  │  │  ├─ Start Async: processChunk(chunkNumber, uploadId, totalBatches)
│  │  │  │  └─ Add Promise to running list
│  │  │  │
│  │  │  └─ Log: "Chunk N submitted with M batches"
│  │  │
│  │  └─ Continue to next iteration
│  │
│  ├─ Step 2.2: Wait for Any Chunk to Complete
│  │  │
│  │  └─ Promise.anyOf(running).get()
│  │     └─ Blocks until at least one chunk completes
│  │
│  ├─ Step 2.3: Process Completed Chunks
│  │  │
│  │  ├─ Get all completed Promises
│  │  │
│  │  ├─ For each completed chunk:
│  │  │  ├─ Get result: ChunkProcessingResult
│  │  │  ├─ Update: successfulRecordCount += result.totalRecordCount
│  │  │  ├─ Aggregate: finalReportData.add(result.reportData)
│  │  │  ├─ Store: partDetails.add(new PartDetail(chunkId, eTag))
│  │  │  └─ Log: "Chunk N completed: M records processed"
│  │  │
│  │  └─ Remove completed Promises from running list
│  │
│  └─ Loop back to Step 2.1
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 3: CHUNK PROCESSING (Parallel, Per Chunk)                            │
└─────────────────────────────────────────────────────────────────────────────┘
│
├─ Activity: processChunk(chunkNumber, uploadId, totalBatches, request)
│  │
│  ├─ Step 3.1: Initialize Polling
│  │  ├─ completedBatches = 0
│  │  ├─ aggregatedResults = []
│  │  └─ reportData = new ReportData()
│  │
│  ├─ Step 3.2: Poll Redis for Batch Completion
│  │  │
│  │  ├─ While (completedBatches < totalBatches)
│  │  │  │
│  │  │  ├─ For each batchId in chunk:
│  │  │  │  │
│  │  │  │  ├─ Get from Redis: batch_{batchId}
│  │  │  │  │
│  │  │  │  ├─ If status == "COMPLETED":
│  │  │  │  │  ├─ Retrieve results
│  │  │  │  │  ├─ aggregatedResults.add(results)
│  │  │  │  │  ├─ completedBatches++
│  │  │  │  │  └─ Delete key from Redis
│  │  │  │  │
│  │  │  │  └─ If status == "FAILED":
│  │  │  │     └─ Handle error / retry logic
│  │  │  │
│  │  │  ├─ If not all complete:
│  │  │  │  └─ Sleep(pollingInterval) // e.g., 5 seconds
│  │  │  │
│  │  │  └─ Log: "Batch completion: X/Y batches done"
│  │  │
│  │  └─ All batches completed
│  │
│  ├─ Step 3.3: Transform & Aggregate Results
│  │  │
│  │  ├─ For each batch result:
│  │  │  ├─ Parse response data
│  │  │  ├─ Format as CSV lines
│  │  │  └─ Collect report statistics
│  │  │
│  │  └─ Generate CSV content for chunk
│  │
│  ├─ Step 3.4: Upload Chunk as Multipart
│  │  │
│  │  ├─ Convert aggregated results to CSV bytes
│  │  ├─ partNumber = chunkNumber
│  │  │
│  │  ├─ S3.uploadPart(
│  │  │    bucketName,
│  │  │    outputKey,
│  │  │    uploadId,
│  │  │    partNumber,
│  │  │    csvBytes
│  │  │  )
│  │  │
│  │  └─ Returns: eTag for this part
│  │
│  ├─ Step 3.5: Collect Report Data (if required)
│  │  │
│  │  ├─ reportData.totalRecords = aggregatedResults.size()
│  │  ├─ reportData.successCount = ...
│  │  ├─ reportData.errorCount = ...
│  │  └─ reportData.statistics = ...
│  │
│  └─ Returns: ChunkProcessingResult
│     ├─ id = chunkNumber
│     ├─ uploadTagId = eTag
│     ├─ totalRecordCount
│     ├─ totalBatchCount
│     └─ reportData
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 4: FINALIZATION                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
│
├─ Step 4.1: Complete Multipart Upload
│  │
│  └─ Activity: finalizeFileUpload(fileKey, uploadId, partDetails)
│     │
│     ├─ Prepare CompletedParts list:
│     │  ├─ Sort partDetails by partNumber
│     │  └─ For each part:
│     │     └─ CompletedPart(partNumber, eTag)
│     │
│     ├─ S3.completeMultipartUpload(
│     │    bucketName,
│     │    fileKey,
│     │    uploadId,
│     │    completedParts
│     │  )
│     │
│     └─ Log: "Output file finalized: {fileKey}"
│
├─ Step 4.2: Generate Report (if required)
│  │
│  └─ If (reportRequired == "Y")
│     │
│     └─ Activity: createAndUploadReport(reportKey, reportData, reportDetail, reportType)
│        │
│        ├─ Step 4.2.1: Format Report
│        │  ├─ Generate CASS format
│        │  ├─ Include statistics:
│        │  │  ├─ Total records processed
│        │  │  ├─ Success count
│        │  │  ├─ Error count
│        │  │  ├─ Processing time
│        │  │  └─ Vendor information
│        │  │
│        │  └─ Create report content
│        │
│        ├─ Step 4.2.2: Upload to S3
│        │  │
│        │  └─ S3.putObject(
│        │       bucketName,
│        │       reportKey,
│        │       reportContent
│        │     )
│        │
│        └─ Log: "Report uploaded: {reportKey}"
│
├─ Step 4.3: Log Final Statistics
│  │
│  ├─ Log: "Total records submitted: {totalRecordCount}"
│  ├─ Log: "Total records processed: {successfulRecordCount}"
│  ├─ Log: "Processing time: {duration}"
│  └─ Log: "Job completed for outputFileId: {outputFileId}"
│
└─ Step 4.4: Return Workflow Response
   │
   └─ Returns: BulkWorkflowResponse
      ├─ apiType
      ├─ header
      ├─ emailId
      ├─ workspaceId
      ├─ fileId
      ├─ fileName
      └─ outputFileId

END: Workflow Completed Successfully
```

---

## 2. Kafka Event Publishing Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       KAFKA PUBLISHING FLOW                                  │
└─────────────────────────────────────────────────────────────────────────────┘

START: uploadChunk() Activity
│
├─ Input: FileChunk, BulkApiRequest
│
├─ Step 1: Read CSV Data
│  └─ Stream chunk from S3
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 2: Batch Creation                                                   │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  records = parseCSV(chunkData)                                            │
│  batchSize = 100 (configurable)                                           │
│  totalRecords = records.size()                                            │
│  batches = []                                                             │
│                                                                            │
│  For i = 0 to totalRecords step batchSize:                               │
│    ├─ batchRecords = records[i : i+batchSize]                            │
│    ├─ batchId = UUID.randomUUID()                                        │
│    │                                                                      │
│    └─ batch = {                                                          │
│         "batchId": batchId,                                              │
│         "chunkNumber": chunk.number,                                     │
│         "workspaceId": request.workspaceId,                              │
│         "outputFileId": request.outputFileId,                            │
│         "records": batchRecords,                                         │
│         "recordCount": batchRecords.size(),                              │
│         "metadata": {                                                    │
│           "apiType": request.apiType,                                    │
│           "delimiter": request.delimiter,                                │
│           "timestamp": currentTime                                       │
│         }                                                                │
│       }                                                                  │
│                                                                            │
│  batches.add(batch)                                                      │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 3: Publish Each Batch to Kafka                                     │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  For each batch in batches:                                              │
│    │                                                                      │
│    ├─ Step 3.1: Create Kafka Event                                       │
│    │  │                                                                   │
│    │  └─ kafkaEvent = KafkaEvent.builder()                               │
│    │       .batchId(batch.batchId)                                       │
│    │       .records(batch.records)                                       │
│    │       .metadata(batch.metadata)                                     │
│    │       .build()                                                      │
│    │                                                                      │
│    ├─ Step 3.2: Serialize Event                                          │
│    │  │                                                                   │
│    │  └─ eventJson = ObjectMapper.writeValueAsString(kafkaEvent)         │
│    │                                                                      │
│    ├─ Step 3.3: Add Headers                                              │
│    │  │                                                                   │
│    │  └─ headers = [                                                     │
│    │       RecordHeader("type", "kafkaEvent"),                           │
│    │       RecordHeader("schemaVersion", "1"),                           │
│    │       RecordHeader("messageId", UUID.randomUUID())                  │
│    │     ]                                                               │
│    │                                                                      │
│    ├─ Step 3.4: Send to Kafka                                            │
│    │  │                                                                   │
│    │  ├─ Producer.send(                                                  │
│    │  │    topic: "bulk-processing-topic",                               │
│    │  │    key: batch.batchId,                                           │
│    │  │    value: eventJson,                                             │
│    │  │    headers: headers                                              │
│    │  │  )                                                               │
│    │  │                                                                   │
│    │  └─ Wait for acknowledgment (acks=all)                              │
│    │                                                                      │
│    ├─ Step 3.5: Handle Response                                          │
│    │  │                                                                   │
│    │  ├─ On Success:                                                     │
│    │  │  ├─ Log: "Batch {batchId} published successfully"               │
│    │  │  └─ Continue                                                     │
│    │  │                                                                   │
│    │  └─ On Failure:                                                     │
│    │     ├─ Log error                                                    │
│    │     ├─ Retry (up to 3 times per producer config)                   │
│    │     └─ Throw exception if all retries fail                         │
│    │                                                                      │
│    └─ Step 3.6: Store in Redis                                           │
│       │                                                                   │
│       └─ Redis.set(                                                      │
│            key: "batch_{batchId}",                                       │
│            value: {                                                      │
│              "status": "PENDING",                                        │
│              "chunkNumber": batch.chunkNumber,                           │
│              "recordCount": batch.recordCount,                           │
│              "workspaceId": batch.workspaceId,                           │
│              "outputFileId": batch.outputFileId,                         │
│              "timestamp": currentTime,                                   │
│              "ttl": 3600 // 1 hour                                       │
│            }                                                             │
│          )                                                               │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 4: Return Summary                                                   │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Returns: ChunkSubmitResult                                              │
│    ├─ totalRecords = totalRecords                                        │
│    └─ totalBatches = batches.size()                                      │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘

END: uploadChunk() Activity

─────────────────────────────────────────────────────────────────────────────

DOWNSTREAM: External Service Processing
│
├─ Kafka Consumer (External Service)
│  │
│  ├─ Poll messages from topic
│  │
│  ├─ For each message:
│  │  ├─ Deserialize KafkaEvent
│  │  ├─ Process records (geocoding, validation, etc.)
│  │  ├─ Generate results
│  │  │
│  │  └─ Update Redis:
│  │     │
│  │     └─ Redis.set(
│  │          key: "batch_{batchId}",
│  │          value: {
│  │            "status": "COMPLETED",
│  │            "results": [...processed results...],
│  │            "successCount": N,
│  │            "errorCount": M,
│  │            "completedAt": timestamp
│  │          }
│  │        )
│  │
│  └─ Worker polls Redis and retrieves results in processChunk()
│
└─ END
```

---

## 3. Chunk Processing & Redis Polling Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CHUNK PROCESSING FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

START: processChunk() Activity
│
├─ Input: chunkNumber, uploadId, totalBatches, bulkApiRequest
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 1: Initialize                                                        │
├───────────────────────────────────────────────────────────────────────────┤
│  completedBatches = 0                                                      │
│  aggregatedResults = []                                                    │
│  reportData = new ReportData()                                            │
│  batchIds = getBatchIdsForChunk(chunkNumber, outputFileId)                │
│  pollingInterval = 5 seconds                                              │
│  maxPollingTime = 60 minutes                                              │
│  startTime = now()                                                        │
└───────────────────────────────────────────────────────────────────────────┘
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 2: Poll Redis for Batch Completion                                  │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  WHILE (completedBatches < totalBatches AND not timeout):                │
│    │                                                                      │
│    ├─ FOR each batchId in batchIds:                                      │
│    │  │                                                                   │
│    │  ├─ Redis.get("batch_{batchId}")                                    │
│    │  │                                                                   │
│    │  ├─ IF key exists:                                                  │
│    │  │  │                                                                │
│    │  │  ├─ batchData = parse JSON                                       │
│    │  │  │                                                                │
│    │  │  ├─ IF batchData.status == "COMPLETED":                          │
│    │  │  │  │                                                             │
│    │  │  │  ├─ Extract results                                           │
│    │  │  │  │  ├─ results = batchData.results                            │
│    │  │  │  │  ├─ successCount = batchData.successCount                  │
│    │  │  │  │  └─ errorCount = batchData.errorCount                     │
│    │  │  │  │                                                             │
│    │  │  │  ├─ aggregatedResults.addAll(results)                         │
│    │  │  │  │                                                             │
│    │  │  │  ├─ Update report data:                                       │
│    │  │  │  │  ├─ reportData.successCount += successCount                │
│    │  │  │  │  └─ reportData.errorCount += errorCount                   │
│    │  │  │  │                                                             │
│    │  │  │  ├─ completedBatches++                                        │
│    │  │  │  │                                                             │
│    │  │  │  ├─ Redis.delete("batch_{batchId}")                           │
│    │  │  │  │  └─ Cleanup completed batch                                │
│    │  │  │  │                                                             │
│    │  │  │  └─ Log: "Batch {batchId} completed and retrieved"           │
│    │  │  │                                                                │
│    │  │  ├─ ELSE IF batchData.status == "FAILED":                        │
│    │  │  │  │                                                             │
│    │  │  │  ├─ Log error: "Batch {batchId} failed"                       │
│    │  │  │  ├─ Handle failure (retry or skip)                            │
│    │  │  │  └─ completedBatches++ (to avoid infinite loop)               │
│    │  │  │                                                                │
│    │  │  └─ ELSE IF batchData.status == "PENDING":                       │
│    │  │     └─ Continue waiting                                          │
│    │  │                                                                   │
│    │  └─ ELSE (key not found):                                           │
│    │     └─ Log warning: "Batch {batchId} not found in Redis"           │
│    │                                                                      │
│    ├─ Progress: Log "{completedBatches}/{totalBatches} batches completed"│
│    │                                                                      │
│    ├─ IF completedBatches < totalBatches:                                │
│    │  ├─ Sleep(pollingInterval)                                          │
│    │  └─ Check timeout: (now() - startTime > maxPollingTime)            │
│    │                                                                      │
│    └─ LOOP back                                                          │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 3: Transform Results to CSV                                         │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  csvLines = []                                                            │
│                                                                            │
│  FOR each result in aggregatedResults:                                   │
│    ├─ Parse result JSON                                                  │
│    ├─ Extract fields                                                     │
│    ├─ Format as CSV line                                                 │
│    └─ csvLines.add(formattedLine)                                        │
│                                                                            │
│  csvContent = csvLines.join("\n")                                        │
│  csvBytes = csvContent.getBytes(UTF-8)                                   │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 4: Upload as Multipart                                              │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  partNumber = chunkNumber                                                │
│                                                                            │
│  response = S3.uploadPart(                                               │
│    bucket: outputBucket,                                                 │
│    key: outputFileKey,                                                   │
│    uploadId: uploadId,                                                   │
│    partNumber: partNumber,                                               │
│    body: csvBytes                                                        │
│  )                                                                        │
│                                                                            │
│  eTag = response.ETag                                                    │
│  Log: "Part {partNumber} uploaded, ETag: {eTag}"                         │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
│
▼
┌───────────────────────────────────────────────────────────────────────────┐
│  Step 5: Return Processing Result                                         │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Returns: ChunkProcessingResult                                          │
│    ├─ id = chunkNumber                                                   │
│    ├─ uploadTagId = eTag                                                 │
│    ├─ totalRecordCount = aggregatedResults.size()                        │
│    ├─ totalBatchCount = completedBatches                                │
│    └─ reportData = reportData                                            │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘

END: processChunk() Activity
```

---

## 4. Error Handling Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ERROR HANDLING FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

Activity Execution:
│
├─ TRY: Execute Activity Logic
│  │
│  ├─ Activity logic executes...
│  │
│  └─ IF Success:
│     └─ Return result → Continue workflow
│
├─ CATCH: Exception Occurred
│  │
│  ├─ Determine Exception Type
│  │
│  ├─ CASE 1: Retryable Exception
│  │  │  (Network timeout, throttling, temporary failure)
│  │  │
│  │  ├─ Temporal Retry Logic:
│  │  │  │
│  │  │  ├─ Attempt 1 Failed
│  │  │  │  └─ Wait: initialInterval (1 second)
│  │  │  │
│  │  │  ├─ Attempt 2 Failed
│  │  │  │  └─ Wait: initialInterval * backoffCoefficient (2 seconds)
│  │  │  │
│  │  │  ├─ Attempt 3 Failed
│  │  │  │  └─ Wait: previous * backoffCoefficient (4 seconds)
│  │  │  │
│  │  │  ├─ IF attempts < maximumAttempts (3):
│  │  │  │  └─ Retry activity
│  │  │  │
│  │  │  └─ ELSE:
│  │  │     └─ Throw ApplicationFailure
│  │  │        └─ Workflow handles failure
│  │  │
│  │  └─ Log: "Activity retry attempt N/3"
│  │
│  ├─ CASE 2: Non-Retryable Exception
│  │  │  (NoSuchKeyException, validation error, auth failure)
│  │  │
│  │  ├─ Throw: ApplicationFailure.newNonRetryableFailure()
│  │  │  └─ Skip retry logic
│  │  │
│  │  ├─ Workflow receives failure
│  │  │
│  │  └─ Workflow Decision:
│  │     │
│  │     ├─ IF critical (e.g., missing input file):
│  │     │  └─ Fail entire workflow immediately
│  │     │
│  │     └─ IF non-critical (e.g., single chunk):
│  │        ├─ Log error
│  │        ├─ Mark chunk as failed
│  │        └─ Continue with remaining chunks
│  │
│  └─ CASE 3: Workflow-Level Error
│     │
│     ├─ Multiple chunks failed
│     │  OR
│     ├─ Unrecoverable state
│     │
│     ├─ Workflow marks itself as FAILED
│     │
│     └─ Temporal preserves complete history
│        └─ Admin can inspect and manually retry
│
└─ Finally: Cleanup Resources
   └─ workerStatus.endActivity()

─────────────────────────────────────────────────────────────────────────────

CHUNK-LEVEL RETRY FLOW:
│
├─ Workflow maintains chunk queue
│
├─ IF chunk activity fails (after retries):
│  │
│  ├─ Remove from running list
│  │
│  ├─ Decide: Retry chunk or skip
│  │  │
│  │  ├─ IF transient failure:
│  │  │  └─ Add chunk back to queue
│  │  │     └─ Will be picked up in next iteration
│  │  │
│  │  └─ IF permanent failure:
│  │     └─ Log and skip chunk
│  │        └─ Continue with other chunks
│  │
│  └─ Advantage: Only failed chunk retried, not entire file
│
└─ Workflow continues with remaining chunks
```

---

## 5. Multipart Upload Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    S3 MULTIPART UPLOAD FLOW                                  │
└─────────────────────────────────────────────────────────────────────────────┘

PHASE 1: Initialization
│
├─ Activity: generateUploadId()
│  │
│  ├─ Request: S3.createMultipartUpload(
│  │   bucket: outputBucket,
│  │   key: "output/workspace/email/file_outputId.csv"
│  │ )
│  │
│  ├─ Response: { uploadId: "xyz123..." }
│  │
│  └─ Store uploadId in workflow state
│
▼

PHASE 2: Upload Parts (Parallel)
│
├─ Each chunk becomes one part
│
├─ Chunk 1 → Part 1
│  │
│  ├─ processChunk() generates CSV for chunk 1
│  │
│  ├─ S3.uploadPart(
│  │   bucket: outputBucket,
│  │   key: outputFileKey,
│  │   uploadId: "xyz123...",
│  │   partNumber: 1,
│  │   body: chunk1CsvBytes
│  │ )
│  │
│  └─ Returns: { eTag: "etag1..." }
│     └─ Store: partDetails[1] = { partNumber: 1, eTag: "etag1..." }
│
├─ Chunk 2 → Part 2 (parallel)
│  │
│  ├─ processChunk() generates CSV for chunk 2
│  │
│  ├─ S3.uploadPart(
│  │   uploadId: "xyz123...",
│  │   partNumber: 2,
│  │   body: chunk2CsvBytes
│  │ )
│  │
│  └─ Returns: { eTag: "etag2..." }
│     └─ Store: partDetails[2] = { partNumber: 2, eTag: "etag2..." }
│
├─ ... (N chunks processing in parallel)
│
└─ All chunks complete → All parts uploaded
   └─ partDetails = [
        { partNumber: 1, eTag: "etag1..." },
        { partNumber: 2, eTag: "etag2..." },
        { partNumber: 3, eTag: "etag3..." },
        ...
      ]
│
▼

PHASE 3: Complete Upload
│
├─ Activity: finalizeFileUpload()
│  │
│  ├─ Sort partDetails by partNumber (important!)
│  │
│  ├─ Request: S3.completeMultipartUpload(
│  │   bucket: outputBucket,
│  │   key: outputFileKey,
│  │   uploadId: "xyz123...",
│  │   parts: [
│  │     { partNumber: 1, eTag: "etag1..." },
│  │     { partNumber: 2, eTag: "etag2..." },
│  │     { partNumber: 3, eTag: "etag3..." },
│  │     ...
│  │   ]
│  │ )
│  │
│  ├─ S3 combines all parts into single file
│  │
│  └─ Response: { location: "s3://bucket/output/..." }
│     └─ Final output CSV file created
│
▼

RESULT: Single CSV file with all processed data
  └─ User can download from S3
```

---

## 6. Report Generation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REPORT GENERATION FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

START: Workflow completion
│
├─ IF reportRequired == "Y":
│  │
│  ├─ finalReportData has been aggregated during chunk processing:
│  │  ├─ totalRecords
│  │  ├─ successCount
│  │  ├─ errorCount
│  │  ├─ processingTime
│  │  └─ Additional statistics per chunk
│  │
│  └─ Activity: createAndUploadReport()
│     │
│     ├─ Step 1: Format Report (CASS Format)
│     │  │
│     │  ├─ Header Section:
│     │  │  ├─ Vendor: "Precisely Software Inc."
│     │  │  ├─ Software: "Bulk API Geocode"
│     │  │  ├─ Version: "1.00.00.O"
│     │  │  ├─ Customer: {customerName}
│     │  │  └─ Date: {processingDate}
│     │  │
│     │  ├─ Summary Section:
│     │  │  ├─ Total Records: {totalRecords}
│     │  │  ├─ Successfully Processed: {successCount}
│     │  │  ├─ Errors: {errorCount}
│     │  │  ├─ Success Rate: {successRate}%
│     │  │  └─ Processing Time: {duration}
│     │  │
│     │  ├─ Detail Section:
│     │  │  ├─ Per-chunk statistics
│     │  │  ├─ Error breakdown by type
│     │  │  └─ Performance metrics
│     │  │
│     │  └─ Footer Section:
│     │     └─ Report generated timestamp
│     │
│     ├─ Step 2: Convert to Text/PDF
│     │  └─ reportContent = formatAsCASS(finalReportData, reportDetail)
│     │
│     ├─ Step 3: Upload to S3
│     │  │
│     │  └─ S3.putObject(
│     │       bucket: outputBucket,
│     │       key: "reports/workspace/email/outputId_report.txt",
│     │       body: reportContent
│     │     )
│     │
│     └─ Log: "Report uploaded successfully"
│
└─ ELSE:
   └─ Skip report generation

END
```

---

## 7. Worker Lifecycle Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        WORKER LIFECYCLE                                      │
└─────────────────────────────────────────────────────────────────────────────┘

Application Startup:
│
├─ 1. Micronaut Application Start
│  └─ TemporalWorkerApplication.main()
│
├─ 2. Dependency Injection
│  ├─ Inject: TemporalWorkerFactory
│  ├─ Inject: BulkActivities
│  ├─ Inject: WorkflowClient
│  └─ Inject: All services
│
├─ 3. ApplicationStartupEvent Triggered
│  │
│  └─ onApplicationEvent()
│     │
│     ├─ Create WorkerFactory
│     │  └─ WorkerFactory.newInstance(workflowClient)
│     │
│     ├─ Create Worker
│     │  └─ workerFactory.newWorker(queueName, options)
│     │     └─ Queue: "bulk-processing-queue"
│     │     └─ Options:
│     │        ├─ maxConcurrentWorkflowTaskPollers: 2
│     │        ├─ maxConcurrentActivityTaskPollers: 4
│     │        ├─ maxConcurrentWorkflowTaskExecutionSize: 2
│     │        └─ maxConcurrentActivityExecutionSize: 4
│     │
│     ├─ Register Workflow
│     │  └─ worker.registerWorkflowImplementationTypes(BulkWorkflow.class)
│     │
│     ├─ Register Activities
│     │  └─ worker.registerActivitiesImplementations(bulkActivities)
│     │
│     └─ Start Worker
│        └─ workerFactory.start()
│           │
│           └─ Worker starts polling Temporal for tasks
│
▼

Worker Running:
│
├─ Poll for Workflow Tasks
│  │
│  ├─ Temporal assigns workflow execution
│  │
│  └─ Execute workflow logic
│
├─ Poll for Activity Tasks
│  │
│  ├─ Temporal assigns activity execution
│  │
│  └─ Execute activity logic
│
└─ Health Monitoring
   ├─ /health endpoint returns status
   └─ Prometheus metrics exposed

▼

Graceful Shutdown:
│
├─ Shutdown signal received (SIGTERM)
│
├─ Stop accepting new tasks
│
├─ Wait for running tasks to complete
│  └─ Timeout: configurable
│
├─ Close connections:
│  ├─ Temporal client
│  ├─ Kafka producer
│  ├─ Redis client
│  └─ S3 client
│
└─ Application exit
```

---

**Flow Documentation by Arun Kumar Pal**  
*Owner, Architect & Developer*
