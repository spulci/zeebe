/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class TypedEventRegistry {

  public static final Map<ValueType, Class<? extends UnifiedRecordValue>> EVENT_REGISTRY;

  static {
    final EnumMap<ValueType, Class<? extends UnifiedRecordValue>> registry =
        new EnumMap<>(ValueType.class);
    registry.put(ValueType.DEPLOYMENT, DeploymentRecord.class);
    registry.put(ValueType.JOB, JobRecord.class);
    registry.put(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceRecord.class);
    registry.put(ValueType.INCIDENT, IncidentRecord.class);
    registry.put(ValueType.MESSAGE, MessageRecord.class);
    registry.put(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecord.class);
    registry.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, MessageStartEventSubscriptionRecord.class);
    registry.put(
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionRecord.class);
    registry.put(ValueType.JOB_BATCH, JobBatchRecord.class);
    registry.put(ValueType.TIMER, TimerRecord.class);
    registry.put(ValueType.VARIABLE, VariableRecord.class);
    registry.put(ValueType.VARIABLE_DOCUMENT, VariableDocumentRecord.class);
    registry.put(ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationRecord.class);
    registry.put(ValueType.ERROR, ErrorRecord.class);
    registry.put(ValueType.WORKFLOW_INSTANCE_RESULT, WorkflowInstanceResultRecord.class);

    EVENT_REGISTRY = Collections.unmodifiableMap(registry);
  }
}
