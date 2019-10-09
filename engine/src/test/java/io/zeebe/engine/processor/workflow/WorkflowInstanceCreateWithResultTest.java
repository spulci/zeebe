package io.zeebe.engine.processor.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.test.util.MsgPackUtil;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

public class WorkflowInstanceCreateWithResultTest {
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance SERVICE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start").endEvent("end").done();

  @Rule public Timeout timeoutRule = new Timeout(2, TimeUnit.MINUTES);
  private final StreamProcessorRule envRule = new StreamProcessorRule();
  private final WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);
  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Test
  public void shouldSendResultAfterCompletion() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    final Record<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    waitUntil(
        () -> envRule.events().withIntent(WorkflowInstanceCreationIntent.SEND_RESULT).exists());

    Mockito.verify(envRule.getCommandResponseWriter(), times(1))
        .intent(WorkflowInstanceCreationIntent.COMPLETED_WITH_RESULT);
  }

  @Test
  public void shouldSendResultWithVariablesAfterCompletion() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    String variables = "{'x':1}";
    final Record<WorkflowInstanceRecord> createdEvent =
      streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID).setVariables(
        MsgPackUtil.asMsgPack(variables)));

    // then
    final WorkflowInstanceCreationRecord result = envRule.events()
      .onlyWorkflowInstanceCreationRecords()
      .withIntent(WorkflowInstanceCreationIntent.COMPLETED_WITH_RESULT).findFirst().get().getValue();

    assertThat(result.getVariables().containsKey("x")).isTrue();
    assertThat(result.getVariables().get("x")).isEqualTo(1);

    Mockito.verify(envRule.getCommandResponseWriter(), times(1))
      .intent(WorkflowInstanceCreationIntent.COMPLETED_WITH_RESULT);
  }
}
