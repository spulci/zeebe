/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;

public final class NoopResponseWriter implements TypedResponseWriter {

  @Override
  public void writeRejectionOnCommand(TypedRecord<?> command, RejectionType type, String reason) {}

  @Override
  public void writeEvent(TypedRecord<?> event) {}

  @Override
  public void writeEventOnCommand(
      long eventKey, Intent eventState, UnpackedObject eventValue, TypedRecord<?> command) {}

  @Override
  public void writeResponse(
      long eventKey,
      Intent eventState,
      UnpackedObject eventValue,
      ValueType valueType,
      long requestId,
      int requestStreamId) {}

  @Override
  public boolean flush() {
    return false;
  }
}
