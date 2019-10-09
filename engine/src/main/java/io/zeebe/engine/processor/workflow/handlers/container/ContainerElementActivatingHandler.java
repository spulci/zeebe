/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.engine.state.deployment.WorkflowState;
import java.util.Collections;
import java.util.List;

public class ContainerElementActivatingHandler<T extends ExecutableFlowElementContainer>
    extends ElementActivatingHandler<T> {

  private final WorkflowState workflowState;
  private final CatchEventSubscriber catchEventSubscriber;

  public ContainerElementActivatingHandler(
      WorkflowState workflowState, CatchEventSubscriber catchEventSubscriber) {
    this.workflowState = workflowState;
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final T containerElement = context.getElement();
    final List<ExecutableCatchEvent> containerEvents = containerElement.getEvents();

    if (!containerEvents.isEmpty()) {
      for (ExecutableCatchEvent event : containerEvents) {
        if (event.isTimer()) {
          workflowState
              .getEventScopeInstanceState()
              .createIfNotExists(
                  context.getValue().getWorkflowKey(),
                  Collections.singletonList(event.getEventId()));
        }
      }

      if (!catchEventSubscriber.subscribeToEvents(context)) {
        return false;
      }
    }

    return super.handleState(context);
  }
}
