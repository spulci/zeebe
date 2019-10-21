/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EventSubprocessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerEventSubprocess() {
    // given
    final BpmnModelInstance model = createEventSubProcModel();

    // when
    final long workflowKey =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("proc").withVariable("innerKey", "123").create();

    // then
    final Record<WorkflowInstanceRecordValue> timerTriggered =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();
    Assertions.assertThat(timerTriggered.getValue())
        .hasWorkflowKey(workflowKey)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start");

    assertNonInterruptingLifecycle(timerTriggered);
  }

  @Test
  public void shouldTriggerNestedEventSubproc() {
    // given
    final BpmnModelInstance model = createNestedEventSubproc();

    // when
    ENGINE.deployment().withXmlResource(model).deploy();
    ENGINE.workflowInstance().ofBpmnProcessId("proc").create();

    // then
    final Record<WorkflowInstanceRecordValue> timerTriggered =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();

    assertNonInterruptingLifecycle(timerTriggered);
  }

  @Test
  public void shouldNotInterruptProcess() {}

  @Test
  public void testVariables() {}

  private void assertNonInterruptingLifecycle(Record<WorkflowInstanceRecordValue> timerTriggered) {
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .skipUntil(r -> r.getPosition() > timerTriggered.getPosition())
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "event_sub_flow"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_proc"));
  }

  private static BpmnModelInstance createEventSubProcModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("proc");
    builder
        .eventSubProcess("firstSubProcess")
        .startEvent("start_sub_1")
        .message(b -> b.name("msg").zeebeCorrelationKey("innerKey"))
        .endEvent();
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .timerWithDuration("PT1S")
        .sequenceFlowId("event_sub_flow")
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_proc")
        .done();
  }

  private static BpmnModelInstance createNestedEventSubproc() {
    final EmbeddedSubProcessBuilder embeddedBuilder =
        Bpmn.createExecutableProcess("proc")
            .startEvent("proc_start")
            .subProcess("sub_proc")
            .embeddedSubProcess();
    embeddedBuilder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .timerWithDuration("PT1S")
        .sequenceFlowId("event_sub_flow")
        .endEvent("event_sub_end");
    return embeddedBuilder
        .startEvent("sub_start")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_end")
        .done();
  }
}
