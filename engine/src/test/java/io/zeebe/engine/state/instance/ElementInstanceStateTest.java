/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ElementInstanceStateTest {

  private static final long WORKFLOW_KEY = 123;

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private ElementInstanceState elementInstanceState;

  @Before
  public void setUp() {
    final ZeebeState zeebeState = stateRule.getZeebeState();
    elementInstanceState = zeebeState.getWorkflowState().getElementInstanceState();
  }

  @Test
  public void shouldCreateNewInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();

    // when
    final ElementInstance elementInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then
    assertElementInstance(elementInstance, 0);
  }

  @Test
  public void shouldCreateNewInstanceWithParent() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceRecord otherRecord = createWorkflowInstanceRecord();
    otherRecord.setElementId("subProcess");
    final ElementInstance childInstance =
        elementInstanceState.newInstance(
            parentInstance, 101, otherRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    assertElementInstance(parentInstance, 1);
    assertChildInstance(childInstance, 101, "subProcess");
  }

  @Test
  public void shouldSpawnToken() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.spawnToken(100);

    // then
    final ElementInstance elementInstance = elementInstanceState.getInstance(100);
    Assertions.assertThat(elementInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    Assertions.assertThat(elementInstance.getNumberOfActiveExecutionPaths()).isEqualTo(1);
    Assertions.assertThat(elementInstance.getNumberOfActiveTokens()).isEqualTo(1);
  }

  @Test
  public void shouldConsumeToken() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.spawnToken(100);

    // when
    elementInstanceState.consumeToken(100);

    // then
    final ElementInstance elementInstance = elementInstanceState.getInstance(100);
    Assertions.assertThat(elementInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    Assertions.assertThat(elementInstance.getNumberOfActiveExecutionPaths()).isEqualTo(0);
    Assertions.assertThat(elementInstance.getNumberOfActiveTokens()).isEqualTo(0);
  }

  @Test
  public void shouldFindElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ElementInstance instance = elementInstanceState.getInstance(100);

    // then
    assertElementInstance(instance, 0);
  }

  @Test
  public void shouldFindChildInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);

    // then
    assertChildInstance(childInstance, 101, "subProcess");
  }

  @Test
  public void shouldFindParentInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final ElementInstance updatedParentInstance = elementInstanceState.getInstance(100L);

    // then
    assertElementInstance(updatedParentInstance, 1);
  }

  @Test
  public void shouldRemoveParentInstanceAfterRemovingChild() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.removeInstance(101L);

    // when
    elementInstanceState.removeInstance(100L);

    // then
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);
    Assertions.assertThat(childInstance).isNull();

    final ElementInstance parent = elementInstanceState.getInstance(100L);
    Assertions.assertThat(parent).isNull();

    final List<ElementInstance> children = elementInstanceState.getChildren(100L);
    Assertions.assertThat(children).hasSize(0);
  }

  @Test
  public void shouldRemoveChildInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    workflowInstanceRecord.setElementId("subProcess2");
    elementInstanceState.newInstance(
        parentInstance, 102, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    elementInstanceState.removeInstance(101L);

    // then
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);
    Assertions.assertThat(childInstance).isNull();

    final List<ElementInstance> children = elementInstanceState.getChildren(100L);
    Assertions.assertThat(children).hasSize(1);

    final ElementInstance childInstance2 = elementInstanceState.getInstance(102L);
    assertChildInstance(childInstance2, 102L, "subProcess2");

    final ElementInstance updatedParent = elementInstanceState.getInstance(100L);
    assertElementInstance(updatedParent, 1);
  }

  @Test
  public void shouldUpdateElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.spawnToken();
    instance.setState(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    instance.setJobKey(5);
    elementInstanceState.updateInstance(instance);

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    Assertions.assertThat(updatedInstance.getKey()).isEqualTo(100);
    Assertions.assertThat(updatedInstance.getState())
        .isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    Assertions.assertThat(updatedInstance.getJobKey()).isEqualTo(5);
    Assertions.assertThat(updatedInstance.canTerminate()).isTrue();

    Assertions.assertThat(updatedInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    Assertions.assertThat(updatedInstance.getNumberOfActiveExecutionPaths()).isEqualTo(1);
    Assertions.assertThat(updatedInstance.getNumberOfActiveTokens()).isEqualTo(1);

    final WorkflowInstanceRecord record = updatedInstance.getValue();
    assertWorkflowInstanceRecord(record);
  }

  @Test
  public void shouldNotUpdateElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.spawnToken();
    instance.setState(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    instance.setJobKey(5);

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    Assertions.assertThat(updatedInstance.getKey()).isEqualTo(100);
    Assertions.assertThat(updatedInstance.getState())
        .isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    Assertions.assertThat(updatedInstance.getJobKey()).isEqualTo(0L);
    Assertions.assertThat(updatedInstance.canTerminate()).isTrue();

    Assertions.assertThat(updatedInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    Assertions.assertThat(updatedInstance.getNumberOfActiveExecutionPaths()).isEqualTo(0);
    Assertions.assertThat(updatedInstance.getNumberOfActiveTokens()).isEqualTo(0);

    final WorkflowInstanceRecord record = updatedInstance.getValue();
    assertWorkflowInstanceRecord(record);
  }

  @Test
  public void shouldNotUpdateElementInstanceWithoutFlush() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.spawnToken();
    instance.setState(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    instance.setJobKey(5);

    // then
    final ElementInstance oldInstance = elementInstanceState.getInstance(100);

    assertElementInstance(oldInstance, 0);
  }

  @Test
  public void shouldCollectChildInstances() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    workflowInstanceRecord.setElementId("subProcess2");
    elementInstanceState.newInstance(
        parentInstance, 102, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final List<ElementInstance> children = elementInstanceState.getChildren(100L);

    // then
    Assertions.assertThat(children).hasSize(2);
    assertChildInstance(children.get(0), 101, "subProcess");
    assertChildInstance(children.get(1), 102, "subProcess2");
  }

  @Test
  public void shouldStoreAndCollectRecord() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.storeRecord(
        123L,
        100,
        createWorkflowInstanceRecord(),
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        Purpose.DEFERRED);

    // then
    final List<IndexedRecord> storedRecords = elementInstanceState.getDeferredRecords(100);

    Assertions.assertThat(storedRecords).hasSize(1);
    final IndexedRecord indexedRecord = storedRecords.get(0);
    Assertions.assertThat(indexedRecord.getKey()).isEqualTo(123L);
    Assertions.assertThat(indexedRecord.getState())
        .isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertWorkflowInstanceRecord(indexedRecord.getValue());
  }

  @Test
  public void shouldRemoveSingleRecord() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.storeRecord(
        123L,
        100,
        createWorkflowInstanceRecord(),
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        Purpose.DEFERRED);
    elementInstanceState.storeRecord(
        124L,
        100,
        createWorkflowInstanceRecord(),
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        Purpose.DEFERRED);

    // when
    elementInstanceState.removeStoredRecord(100, 123, Purpose.DEFERRED);

    // then
    final List<IndexedRecord> storedRecords = elementInstanceState.getDeferredRecords(100);

    Assertions.assertThat(storedRecords).hasSize(1);
    final IndexedRecord indexedRecord = storedRecords.get(0);
    Assertions.assertThat(indexedRecord.getKey()).isEqualTo(124L);
    Assertions.assertThat(indexedRecord.getState())
        .isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertWorkflowInstanceRecord(indexedRecord.getValue());
  }

  @Test
  public void shouldRemoveStoredRecordsOnInstanceRemoval() {
    // given
    final int key = 100;

    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        key, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    elementInstanceState.storeRecord(
        123L,
        100,
        createWorkflowInstanceRecord(),
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        Purpose.DEFERRED);

    // when
    elementInstanceState.removeInstance(key);

    // then
    Assertions.assertThat(elementInstanceState.getDeferredRecords(key)).isEmpty();
  }

  @Test
  public void shouldNotLeakMemoryOnRemoval() {
    // given
    final int parent = 100;
    final int child = 101;

    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            parent, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    setVariableLocal(parent, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));

    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, child, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    setVariableLocal(child, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));

    // when
    elementInstanceState.removeInstance(101);
    elementInstanceState.removeInstance(100);

    // then
    Assertions.assertThat(elementInstanceState.isEmpty()).isTrue();
  }

  @Test
  public void shouldUpdateAwaitResultMetadata() {
    final long key = 10L;
    final int streamId = 2;
    final long requestId = 10000L;

    // when
    elementInstanceState.setAwaitResultRequestMetadata(
        key,
        new AwaitWorkflowInstanceResultMetadata()
            .setRequestStreamId(streamId)
            .setRequestId(requestId));
    final AwaitWorkflowInstanceResultMetadata metadata =
        elementInstanceState.getAwaitResultRequestMetadata(key);

    // then
    assertThat(metadata.getRequestId()).isEqualTo(requestId);
    assertThat(metadata.getRequestStreamId()).isEqualTo(streamId);
  }

  private void assertElementInstance(ElementInstance elementInstance, int childCount) {
    Assertions.assertThat(elementInstance.getKey()).isEqualTo(100);
    Assertions.assertThat(elementInstance.getState())
        .isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    Assertions.assertThat(elementInstance.getJobKey()).isEqualTo(0);
    Assertions.assertThat(elementInstance.canTerminate()).isTrue();

    Assertions.assertThat(elementInstance.getNumberOfActiveElementInstances())
        .isEqualTo(childCount);
    Assertions.assertThat(elementInstance.getNumberOfActiveExecutionPaths()).isEqualTo(childCount);
    Assertions.assertThat(elementInstance.getNumberOfActiveTokens()).isEqualTo(0);

    final WorkflowInstanceRecord record = elementInstance.getValue();

    assertWorkflowInstanceRecord(record);
  }

  private void assertChildInstance(ElementInstance childInstance, long key, String elementId) {
    Assertions.assertThat(childInstance.getKey()).isEqualTo(key);
    Assertions.assertThat(childInstance.getState())
        .isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    Assertions.assertThat(childInstance.getJobKey()).isEqualTo(0);
    Assertions.assertThat(childInstance.canTerminate()).isTrue();

    Assertions.assertThat(childInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    Assertions.assertThat(childInstance.getNumberOfActiveExecutionPaths()).isEqualTo(0);
    Assertions.assertThat(childInstance.getNumberOfActiveTokens()).isEqualTo(0);

    assertWorkflowInstanceRecord(childInstance.getValue(), wrapString(elementId));
  }

  private WorkflowInstanceRecord createWorkflowInstanceRecord() {
    final WorkflowInstanceRecord workflowInstanceRecord = new WorkflowInstanceRecord();
    workflowInstanceRecord.setElementId("startEvent");
    workflowInstanceRecord.setBpmnProcessId(wrapString("process1"));
    workflowInstanceRecord.setWorkflowInstanceKey(1000L);
    workflowInstanceRecord.setFlowScopeKey(1001L);
    workflowInstanceRecord.setVersion(1);
    workflowInstanceRecord.setWorkflowKey(2);
    workflowInstanceRecord.setBpmnElementType(BpmnElementType.START_EVENT);

    return workflowInstanceRecord;
  }

  private void assertWorkflowInstanceRecord(WorkflowInstanceRecord record) {
    assertWorkflowInstanceRecord(record, wrapString("startEvent"));
  }

  private void assertWorkflowInstanceRecord(WorkflowInstanceRecord record, DirectBuffer elementId) {
    assertThat(record.getElementIdBuffer()).isEqualTo(elementId);
    assertThat(record.getBpmnProcessIdBuffer()).isEqualTo(wrapString("process1"));
    assertThat(record.getWorkflowInstanceKey()).isEqualTo(1000L);
    assertThat(record.getFlowScopeKey()).isEqualTo(1001L);
    assertThat(record.getVersion()).isEqualTo(1);
    assertThat(record.getWorkflowKey()).isEqualTo(2);
    assertThat(record.getBpmnElementType()).isEqualTo(BpmnElementType.START_EVENT);
  }

  public void setVariableLocal(long scopeKey, DirectBuffer name, DirectBuffer value) {
    elementInstanceState
        .getVariablesState()
        .setVariableLocal(
            scopeKey, WORKFLOW_KEY, name, 0, name.capacity(), value, 0, value.capacity());
  }
}
