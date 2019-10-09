/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSubprocess;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class SubProcessTransformer implements ModelElementTransformer<SubProcess> {

  @Override
  public Class<SubProcess> getType() {
    return SubProcess.class;
  }

  @Override
  public void transform(SubProcess element, TransformContext context) {
    final ExecutableWorkflow currentWorkflow = context.getCurrentWorkflow();
    final ExecutableSubprocess subprocess =
        currentWorkflow.getElementById(element.getId(), ExecutableSubprocess.class);

    if (element.triggeredByEvent()) {
      if (element.getScope() instanceof FlowNode) {
        final FlowNode scope = (FlowNode) element.getScope();
        final ExecutableFlowElementContainer parentSubProc =
            currentWorkflow.getElementById(scope.getId(), ExecutableFlowElementContainer.class);

        parentSubProc.getEvents().add(subprocess);
      } else {
        // top-level start event
        currentWorkflow.getEvents().add(subprocess);
      }

      subprocess
          .getStartEvents()
          .forEach(
              startEvent -> {
                startEvent.setEventSubProcess(subprocess.getId());
                startEvent.bindLifecycleState(
                    WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.EVENT_SUBPROC_EVENT_OCCURRED);
              });
    }

    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.CONTAINER_ELEMENT_ACTIVATING);
    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.CONTAINER_ELEMENT_ACTIVATED);
    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.CONTAINER_ELEMENT_TERMINATING);
  }
}
