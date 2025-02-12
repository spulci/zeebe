/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ActivateJobsStub
    implements RequestStub<BrokerActivateJobsRequest, BrokerResponse<JobBatchRecord>> {

  public static final long JOB_BATCH_KEY = 123;
  public static final int RETRIES = 12;
  public static final long DEADLINE = 123123123L;

  public static final long WORKFLOW_INSTANCE_KEY = 123L;
  public static final String BPMN_PROCESS_ID = "stubProcess";
  public static final int WORKFLOW_DEFINITION_VERSION = 23;
  public static final long WORKFLOW_KEY = 4532L;
  public static final String ELEMENT_ID = "stubActivity";
  public static final long ELEMENT_INSTANCE_KEY = 459L;

  public static final String CUSTOM_HEADERS = "{\"foo\": 12, \"bar\": \"val\"}";
  public static final String VARIABLES = "{\"foo\": 13, \"bar\": \"world\"}";

  public static final DirectBuffer CUSTOM_HEADERS_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(CUSTOM_HEADERS));
  public static final DirectBuffer VARIABLES_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(VARIABLES));
  private Map<String, Integer> availableJobs = new ConcurrentHashMap<>();

  public long getJobBatchKey() {
    return JOB_BATCH_KEY;
  }

  public int getRetries() {
    return RETRIES;
  }

  public long getDeadline() {
    return DEADLINE;
  }

  public String getCustomHeaders() {
    return CUSTOM_HEADERS;
  }

  public String getVariables() {
    return VARIABLES;
  }

  public long getWorkflowInstanceKey() {
    return WORKFLOW_INSTANCE_KEY;
  }

  public String getBpmnProcessId() {
    return BPMN_PROCESS_ID;
  }

  public int getWorkflowDefinitionVersion() {
    return WORKFLOW_DEFINITION_VERSION;
  }

  public long getWorkflowKey() {
    return WORKFLOW_KEY;
  }

  public String getElementId() {
    return ELEMENT_ID;
  }

  public long getElementInstanceKey() {
    return ELEMENT_INSTANCE_KEY;
  }

  @Override
  public BrokerResponse<JobBatchRecord> handle(BrokerActivateJobsRequest request) throws Exception {
    final int partitionId = request.getPartitionId();

    final JobBatchRecord requestDto = request.getRequestWriter();

    final JobBatchRecord response = new JobBatchRecord();
    response.setMaxJobsToActivate(requestDto.getMaxJobsToActivate());
    response.setWorker(requestDto.getWorkerBuffer());
    response.setType(requestDto.getTypeBuffer());
    response.setTimeout(requestDto.getTimeout());
    addJobs(
        response,
        partitionId,
        requestDto.getMaxJobsToActivate(),
        requestDto.getTypeBuffer(),
        requestDto.getWorkerBuffer());

    return new BrokerResponse<>(
        response, partitionId, Protocol.encodePartitionId(partitionId, JOB_BATCH_KEY));
  }

  public void addAvailableJobs(String type, int amount) {
    availableJobs.put(type, amount);
  }

  private void addJobs(
      JobBatchRecord response,
      int partitionId,
      int amount,
      DirectBuffer type,
      DirectBuffer worker) {

    final int availableAmount = availableJobs.computeIfAbsent(bufferAsString(type), k -> 0);
    final int jobsToActivate = Math.min(amount, availableAmount);
    availableJobs.put(bufferAsString(type), availableAmount - jobsToActivate);
    LongStream.range(0, jobsToActivate)
        .forEach(
            key -> {
              response.jobKeys().add().setValue(Protocol.encodePartitionId(partitionId, key));
              response
                  .jobs()
                  .add()
                  .setType(type)
                  .setWorker(worker)
                  .setRetries(RETRIES)
                  .setDeadline(DEADLINE)
                  .setCustomHeaders(CUSTOM_HEADERS_MSGPACK)
                  .setVariables(VARIABLES_MSGPACK)
                  .setWorkflowInstanceKey(WORKFLOW_INSTANCE_KEY)
                  .setBpmnProcessId(BPMN_PROCESS_ID)
                  .setWorkflowDefinitionVersion(WORKFLOW_DEFINITION_VERSION)
                  .setWorkflowKey(WORKFLOW_KEY)
                  .setElementId(ELEMENT_ID)
                  .setElementInstanceKey(ELEMENT_INSTANCE_KEY);
            });
  }

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerActivateJobsRequest.class, this);
  }
}
