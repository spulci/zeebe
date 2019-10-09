/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord.TimerType;
import java.util.Collection;
import java.util.Collections;
import org.agrona.DirectBuffer;

public class ExecutableSubprocess extends ExecutableFlowElementContainer
    implements ExecutableCatchEvent {

  public ExecutableSubprocess(String id) {
    super(id);
  }

  @Override
  public boolean isTimer() {
    return getStartEvents().get(0).isTimer();
  }

  @Override
  public boolean isMessage() {
    return getStartEvents().get(0).isMessage();
  }

  @Override
  public ExecutableMessage getMessage() {
    return getStartEvents().get(0).getMessage();
  }

  @Override
  public Timer getTimer() {
    return getStartEvents().get(0).getTimer();
  }

  @Override
  public TimerType getTimerType() {
    return TimerType.EVENT_SUBPROC;
  }

  @Override
  public DirectBuffer getEventId() {
    return getStartEvents().get(0).getId();
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return Collections.singletonList(getEventId());
  }
}
