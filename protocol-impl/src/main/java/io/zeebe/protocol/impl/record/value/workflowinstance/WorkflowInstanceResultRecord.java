package io.zeebe.protocol.impl.record.value.workflowinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordValueWithVariables;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public class WorkflowInstanceResultRecord extends UnifiedRecordValue
    implements RecordValueWithVariables, WorkflowInstanceRelated {

  private final StringProperty bpmnProcessIdProperty = new StringProperty("bpmnProcessId", "");
  private final LongProperty workflowKeyProperty = new LongProperty("workflowKey", -1);
  private final IntegerProperty versionProperty = new IntegerProperty("version", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");
  private final LongProperty workflowInstanceKeyProperty =
      new LongProperty("workflowInstanceKey", -1);

  public WorkflowInstanceResultRecord() {
    this.declareProperty(bpmnProcessIdProperty)
        .declareProperty(workflowKeyProperty)
        .declareProperty(workflowInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty);
  }

  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public WorkflowInstanceResultRecord setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceResultRecord setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public int getVersion() {
    return versionProperty.getValue();
  }

  public WorkflowInstanceResultRecord setVersion(int version) {
    this.versionProperty.setValue(version);
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProperty.getValue();
  }

  public WorkflowInstanceResultRecord setWorkflowKey(long key) {
    this.workflowKeyProperty.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public WorkflowInstanceResultRecord setVariables(DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProperty.getValue();
  }

  public WorkflowInstanceResultRecord setWorkflowInstanceKey(long instanceKey) {
    this.workflowInstanceKeyProperty.setValue(instanceKey);
    return this;
  }
}
