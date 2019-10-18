/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.instance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.CommandProcessor;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.spec.MsgpackReaderException;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public class CreateWorkflowInstanceProcessor
    implements CommandProcessor<WorkflowInstanceCreationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected at least a bpmnProcessId or a key greater than -1, but none given";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS =
      "Expected to find workflow definition with process ID '%s', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION =
      "Expected to find workflow definition with process ID '%s' and version '%d', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_KEY =
      "Expected to find workflow definition with key '%d', but none found";
  private static final String ERROR_MESSAGE_NO_NONE_START_EVENT =
      "Expected to create instance of workflow with none start event, but there is no such event";
  private static final String ERROR_INVALID_VARIABLES_REJECTION_MESSAGE =
      "Expected to set variables from document, but the document is invalid: '%s'";
  private static final String ERROR_INVALID_VARIABLES_LOGGED_MESSAGE =
      "Expected to set variables from document, but the document is invalid";

  protected final WorkflowInstanceRecord newWorkflowInstance = new WorkflowInstanceRecord();
  private final WorkflowState workflowState;
  private final ElementInstanceState elementInstanceState;
  private final VariablesState variablesState;
  private final KeyGenerator keyGenerator;

  public CreateWorkflowInstanceProcessor(
      WorkflowState workflowState,
      ElementInstanceState elementInstanceState,
      VariablesState variablesState,
      KeyGenerator keyGenerator) {
    this.workflowState = workflowState;
    this.elementInstanceState = elementInstanceState;
    this.variablesState = variablesState;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void onCommand(
      TypedRecord<WorkflowInstanceCreationRecord> command,
      CommandControl<WorkflowInstanceCreationRecord> controller,
      TypedStreamWriter streamWriter) {
    final WorkflowInstanceCreationRecord record = command.getValue();
    final DeployedWorkflow workflow = getWorkflow(record, controller);
    if (workflow == null || !isValidWorkflow(controller, workflow)) {
      return;
    }

    final long workflowInstanceKey = keyGenerator.nextKey();
    if (!setVariablesFromDocument(controller, record, workflow.getKey(), workflowInstanceKey)) {
      return;
    }

    final ElementInstance workflowInstance = createElementInstance(workflow, workflowInstanceKey);
    streamWriter.appendFollowUpEvent(
        workflowInstanceKey,
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        workflowInstance.getValue());

    record
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setVersion(workflow.getVersion())
        .setWorkflowKey(workflow.getKey());
    controller.accept(WorkflowInstanceCreationIntent.CREATED, record);
  }

  private boolean isValidWorkflow(
      CommandControl<WorkflowInstanceCreationRecord> controller, DeployedWorkflow workflow) {
    if (workflow.getWorkflow().getNoneStartEvent() == null) {
      controller.reject(RejectionType.INVALID_STATE, ERROR_MESSAGE_NO_NONE_START_EVENT);
      return false;
    }

    return true;
  }

  private boolean setVariablesFromDocument(
      CommandControl<WorkflowInstanceCreationRecord> controller,
      WorkflowInstanceCreationRecord record,
      long workflowKey,
      long workflowInstanceKey) {
    try {
      variablesState.setVariablesLocalFromDocument(
          workflowInstanceKey, workflowKey, record.getVariablesBuffer());
    } catch (MsgpackReaderException e) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(ERROR_INVALID_VARIABLES_LOGGED_MESSAGE, e);
      controller.reject(
          RejectionType.INVALID_ARGUMENT,
          String.format(ERROR_INVALID_VARIABLES_REJECTION_MESSAGE, e.getMessage()));

      return false;
    }

    return true;
  }

  private ElementInstance createElementInstance(
      DeployedWorkflow workflow, long workflowInstanceKey) {
    newWorkflowInstance.reset();
    newWorkflowInstance.setBpmnProcessId(workflow.getBpmnProcessId());
    newWorkflowInstance.setVersion(workflow.getVersion());
    newWorkflowInstance.setWorkflowKey(workflow.getKey());
    newWorkflowInstance.setWorkflowInstanceKey(workflowInstanceKey);
    newWorkflowInstance.setBpmnElementType(BpmnElementType.PROCESS);
    newWorkflowInstance.setElementId(workflow.getWorkflow().getId());
    newWorkflowInstance.setFlowScopeKey(-1);

    final ElementInstance instance =
        elementInstanceState.newInstance(
            workflowInstanceKey, newWorkflowInstance, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    return instance;
  }

  private DeployedWorkflow getWorkflow(
      WorkflowInstanceCreationRecord record, CommandControl controller) {
    final DeployedWorkflow workflow;

    final DirectBuffer bpmnProcessId = record.getBpmnProcessIdBuffer();

    if (bpmnProcessId.capacity() > 0) {
      if (record.getVersion() >= 0) {
        workflow = getWorkflow(bpmnProcessId, record.getVersion(), controller);
      } else {
        workflow = getWorkflow(bpmnProcessId, controller);
      }
    } else if (record.getWorkflowKey() >= 0) {
      workflow = getWorkflow(record.getWorkflowKey(), controller);
    } else {
      controller.reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED);
      workflow = null;
    }

    return workflow;
  }

  private DeployedWorkflow getWorkflow(DirectBuffer bpmnProcessId, CommandControl controller) {
    final DeployedWorkflow workflow =
        workflowState.getLatestWorkflowVersionByProcessId(bpmnProcessId);
    if (workflow == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(ERROR_MESSAGE_NOT_FOUND_BY_PROCESS, bufferAsString(bpmnProcessId)));
    }

    return workflow;
  }

  private DeployedWorkflow getWorkflow(
      DirectBuffer bpmnProcessId, int version, CommandControl controller) {
    final DeployedWorkflow workflow =
        workflowState.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);
    if (workflow == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(
              ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION,
              bufferAsString(bpmnProcessId),
              version));
    }

    return workflow;
  }

  private DeployedWorkflow getWorkflow(long key, CommandControl controller) {
    final DeployedWorkflow workflow = workflowState.getWorkflowByKey(key);
    if (workflow == null) {
      controller.reject(
          RejectionType.NOT_FOUND, String.format(ERROR_MESSAGE_NOT_FOUND_BY_KEY, key));
    }

    return workflow;
  }
}
