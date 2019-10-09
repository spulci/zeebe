/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.db.DbValue;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord.TimerType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TimerInstance implements DbValue {

  public static final int NO_ELEMENT_INSTANCE = -1;

  private final DirectBuffer handlerNodeId = new UnsafeBuffer(0, 0);
  private long workflowKey;
  private long key;
  private long elementInstanceKey;
  private long workflowInstanceKey;
  private long dueDate;
  private int repetitions;
  private TimerType timerType;

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public long getDueDate() {
    return dueDate;
  }

  public void setDueDate(long dueDate) {
    this.dueDate = dueDate;
  }

  public long getKey() {
    return key;
  }

  public void setKey(long key) {
    this.key = key;
  }

  public DirectBuffer getHandlerNodeId() {
    return handlerNodeId;
  }

  public void setHandlerNodeId(DirectBuffer handlerNodeId) {
    this.handlerNodeId.wrap(handlerNodeId);
  }

  public int getRepetitions() {
    return repetitions;
  }

  public void setRepetitions(int repetitions) {
    this.repetitions = repetitions;
  }

  public long getWorkflowKey() {
    return this.workflowKey;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public TimerType getTimerType() {
    return timerType;
  }

  public void setTimerStart(TimerType type) {
    this.timerType = type;
  }

  @Override
  public int getLength() {
    return 5 * Long.BYTES + 3 * Integer.BYTES + handlerNodeId.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, elementInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, workflowInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, dueDate, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, key, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, workflowKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, repetitions, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putInt(offset, timerType.ordinal(), ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    offset = writeIntoBuffer(buffer, offset, handlerNodeId);
    assert offset == getLength() : "End offset differs from getLength()";
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    elementInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    workflowInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    dueDate = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    key = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    workflowKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    repetitions = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    timerType = TimerType.values()[buffer.getInt(offset, ZB_DB_BYTE_ORDER)];
    offset += Integer.BYTES;

    offset = readIntoBuffer(buffer, offset, handlerNodeId);
    assert offset == length : "End offset differs from length";
  }
}
