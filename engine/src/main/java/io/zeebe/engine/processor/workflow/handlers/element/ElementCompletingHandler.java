/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.engine.state.instance.ElementInstanceState.RequestMetadata;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import io.zeebe.protocol.record.value.ErrorType;

/**
 * Applies output mappings to the scope.
 *
 * @param <T>
 */
public class ElementCompletingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  private final IOMappingHelper ioMappingHelper;

  public ElementCompletingHandler() {
    this(new IOMappingHelper());
  }

  public ElementCompletingHandler(IOMappingHelper ioMappingHelper) {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETED, ioMappingHelper);
  }

  public ElementCompletingHandler(
      WorkflowInstanceIntent nextState, IOMappingHelper ioMappingHelper) {
    super(nextState);
    this.ioMappingHelper = ioMappingHelper;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    try {
      ioMappingHelper.applyOutputMappings(context);
      sendResponse(context);
      return true;
    } catch (MappingException e) {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, e.getMessage());
    }

    return false;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }

  private void sendResponse(BpmnStepContext<T> context) {
    final long elementInstanceKey = context.getElementInstance().getKey();
    final RequestMetadata requestMetadata =
        context.getElementInstanceState().getRequestMetadata(elementInstanceKey);
    if (requestMetadata != null) {
      final WorkflowInstanceRecord record = context.getValue();
      final WorkflowInstanceResultRecord newRecord = new WorkflowInstanceResultRecord();
      newRecord
          .setBpmnProcessId(record.getBpmnProcessId())
          .setWorkflowKey(record.getWorkflowKey())
          .setWorkflowInstanceKey(record.getWorkflowInstanceKey())
          .setVersion(record.getVersion());
      context
          .getCommandWriter()
          .appendFollowUpCommand(
              context.getKey(),
              WorkflowInstanceResultIntent.SEND,
              newRecord,
              recordMetadata -> {
                recordMetadata.requestId(requestMetadata.getRequestId());
                recordMetadata.requestStreamId((int) requestMetadata.getRequestStreamId());
              });
    }
  }
}
