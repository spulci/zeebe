/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceResultRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class CreateWorkflowInstanceWithResultTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("WORKFLOW").startEvent().endEvent().done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();
  }

  @Test
  public void shouldSendResultAfterCompletion() {
    // given
    Mockito.clearInvocations(ENGINE.getCommandResponseWriter());
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").withResult().create(1, 1);

    // then
    final Record<WorkflowInstanceResultRecordValue> workflowInstanceResult =
        RecordingExporter.workflowInstanceResultRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(WorkflowInstanceResultIntent.COMPLETED)
            .getFirst();

    Assertions.assertThat(workflowInstanceResult.getValue())
        .hasBpmnProcessId("WORKFLOW")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey);

    verify(ENGINE.getCommandResponseWriter(), times(1))
        .intent(WorkflowInstanceResultIntent.COMPLETED);
    verify(ENGINE.getCommandResponseWriter(), times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldSendResultWithVariablesAfterCompletion() {
    // given
    Mockito.clearInvocations(ENGINE.getCommandResponseWriter());
    final Map variables = Map.of("x", "foo");
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW")
            .withVariables(variables)
            .withResult()
            .create(1, 1);

    // when
    final Record<WorkflowInstanceResultRecordValue> workflowInstanceResult =
        RecordingExporter.workflowInstanceResultRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(WorkflowInstanceResultIntent.COMPLETED)
            .getFirst();

    // then
    Assertions.assertThat(workflowInstanceResult.getValue())
        .hasBpmnProcessId("WORKFLOW")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey);

    assertThat(workflowInstanceResult.getValue().getVariables().get("x")).isEqualTo("foo");
    verify(ENGINE.getCommandResponseWriter(), times(1))
        .intent(WorkflowInstanceResultIntent.COMPLETED);
    verify(ENGINE.getCommandResponseWriter(), times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldSendRejectionImmediately() {

    Mockito.clearInvocations(ENGINE.getCommandResponseWriter());
    // when
    ENGINE.workflowInstance().ofBpmnProcessId("INVALID-WORKFLOW").withResult().asyncCreate(1, 1);

    // then more

    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .rejectionType(RejectionType.NOT_FOUND);
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(anyInt(), anyLong());
  }
}
