/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

public class ContainerEventOccurredHandler<T extends ExecutableFlowElementContainer>
    extends EventOccurredHandler<T> {
  private final WorkflowInstanceRecord deferredStartRecord = new WorkflowInstanceRecord();

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    // activate the subprocess's start event
    //    final long wfInstanceKey = context.getValue().getWorkflowInstanceKey();

    deferredStartRecord.reset();
    deferredStartRecord
        .setBpmnElementType(BpmnElementType.START_EVENT)
        .setBpmnProcessId(context.getValue().getBpmnProcessId())
        .setElementId(context.getValue().getElementId())
        .setWorkflowInstanceKey(context.getValue().getWorkflowInstanceKey())
        .setWorkflowKey(context.getValue().getWorkflowKey())
        .setVersion(context.getValue().getVersion());

    context
        .getOutput()
        .deferRecord(
            context.getKey(), deferredStartRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    //
    //    final IndexedRecord deferredRecord =
    //        context.getElementInstanceState().getDeferredRecords(wfInstanceKey).stream()
    //            .filter(r ->
    // r.getValue().getElementId().equals(context.getValue().getElementId()))
    //            .findFirst()
    //            .orElse(null);
    //
    //    if (deferredRecord == null) {
    //      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
    //          "No event subprocess with element id '{}' could be found.",
    //          context.getValue().getElementId());
    //      return false;
    //    }

    //    context
    //        .getOutput()
    //        .appendFollowUpEvent(
    //            context.getKey(), WorkflowInstanceIntent.ELEMENT_ACTIVATING,
    // deferredRecord.getValue());
    return true;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return true;
  }

  @Override
  protected boolean shouldTransition() {
    return false;
  }
}
