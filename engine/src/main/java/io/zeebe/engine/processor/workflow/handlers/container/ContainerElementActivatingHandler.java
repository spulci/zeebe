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
import java.util.List;

public class ContainerElementActivatingHandler<T extends ExecutableFlowElementContainer>
    extends ElementActivatingHandler<T> {

  private final CatchEventSubscriber catchEventSubscriber;

  public ContainerElementActivatingHandler(CatchEventSubscriber catchEventSubscriber) {
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final List<ExecutableCatchEvent> eventSubprocesses = context.getElement().getEvents();

    if (!eventSubprocesses.isEmpty() && !catchEventSubscriber.subscribeToEvents(context)) {
      return false;
    }

    return super.handleState(context);
  }
}
