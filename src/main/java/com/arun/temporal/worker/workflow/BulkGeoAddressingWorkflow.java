package com.arun.temporal.worker.workflow;

import com.arun.temporal.worker.model.BulkApiRequest;
import com.arun.temporal.worker.model.BulkWorkflowResponse;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Workflow interface for processing bulk files using Temporal.
 */
@WorkflowInterface
public interface BulkGeoAddressingWorkflow {
    /**
     * Executes the bulk file processing workflow.
     *
     * @param input the input request containing file and processing metadata
     * @return a result message with total records processed
     */
    @WorkflowMethod
    BulkWorkflowResponse executeWorkflow(BulkApiRequest input);
}
