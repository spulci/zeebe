package io.zeebe.engine.processor.workflow.response;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import org.agrona.DirectBuffer;

public class SendResponseHandler implements TypedRecordProcessor<WorkflowInstanceCreationRecord> {

  ElementInstanceState state;
  WorkflowInstanceCreationRecord completedRecord = new WorkflowInstanceCreationRecord();

  public SendResponseHandler(ElementInstanceState state) {
    this.state = state;
  }

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceCreationRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final WorkflowInstanceCreationRecord workflowRecord = record.getValue();
    final DirectBuffer variablesAsDocument =
        state.getVariablesState().getVariablesAsDocument(workflowRecord.getWorkflowInstanceKey());

    completedRecord.reset();
    completedRecord
        .setWorkflowInstanceKey(workflowRecord.getWorkflowInstanceKey())
        .setWorkflowKey(workflowRecord.getWorkflowKey())
        .setVariables(variablesAsDocument)
        .setBpmnProcessId(workflowRecord.getBpmnProcessId())
        .setVersion(workflowRecord.getVersion());

    streamWriter.appendFollowUpEvent(
        record.getKey(), WorkflowInstanceCreationIntent.COMPLETED_WITH_RESULT, completedRecord);
    responseWriter.writeEventOnCommand(
        record.getKey(),
        WorkflowInstanceCreationIntent.COMPLETED_WITH_RESULT,
        completedRecord,
        record);
  }
}
