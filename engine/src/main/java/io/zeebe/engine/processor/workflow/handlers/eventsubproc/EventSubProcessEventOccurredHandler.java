/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.eventsubproc;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public class EventSubProcessEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {

  private final WorkflowState state;
  private final WorkflowInstanceRecord containerRecord = new WorkflowInstanceRecord();

  public EventSubProcessEventOccurredHandler(ZeebeState state) {
    super(null);
    this.state = state.getWorkflowState();
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    final WorkflowInstanceRecord event = context.getValue();
    final long workflowKey = event.getWorkflowKey();
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);

    final long scopeKey = context.getValue().getFlowScopeKey();
    final EventTrigger triggeredEvent = getTriggeredEvent(context, scopeKey);
    if (triggeredEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error("No triggered event for key {}", context.getKey());
      return false;
    }

    activateContainer(context, workflow, scopeKey);
    final WorkflowInstanceRecord startRecord =
        getEventRecord(context, triggeredEvent, BpmnElementType.START_EVENT)
            .setWorkflowInstanceKey(context.getValue().getWorkflowInstanceKey())
            .setVersion(workflow.getVersion())
            .setBpmnProcessId(workflow.getBpmnProcessId())
            .setFlowScopeKey(scopeKey);

    deferEvent(
        context,
        workflowKey,
        context.getValue().getWorkflowInstanceKey(),
        startRecord,
        triggeredEvent);

    return true;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return true;
  }

  private void activateContainer(
      final BpmnStepContext<T> context, final DeployedWorkflow workflow, long scopeKey) {
    final ExecutableCatchEventElement startEvent =
        workflow
            .getWorkflow()
            .getElementById(context.getValue().getElementId(), ExecutableCatchEventElement.class);
    final DirectBuffer subprocessId = startEvent.getEventSubProcess();

    containerRecord
        .setElementId(subprocessId)
        .setBpmnElementType(BpmnElementType.SUB_PROCESS)
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setWorkflowKey(workflow.getKey())
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(context.getValue().getWorkflowInstanceKey());

    context
        .getOutput()
        .appendFollowUpEvent(scopeKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, containerRecord);
  }
}
