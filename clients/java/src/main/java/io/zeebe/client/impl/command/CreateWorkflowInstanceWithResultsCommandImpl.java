/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl.command;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.response.WorkflowInstanceEventResult;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.response.CreateWorkflowInstanceWithResultsResponseImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultsResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class CreateWorkflowInstanceWithResultsCommandImpl
    implements FinalCommandStep<WorkflowInstanceEventResult> {

  private final GatewayStub asyncStub;
  private final ZeebeObjectMapper objectMapper;
  private Duration requestTimeout;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;

  public CreateWorkflowInstanceWithResultsCommandImpl(
      GatewayStub asyncStub,
      ZeebeObjectMapper objectMapper,
      Duration requestTimeout,
      Predicate<Throwable> retryPredicate,
      Builder builder) {
    this.asyncStub = asyncStub;
    this.objectMapper = objectMapper;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    this.builder = builder;
  }

  @Override
  public FinalCommandStep<WorkflowInstanceEventResult> requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<WorkflowInstanceEventResult> send() {
    final CreateWorkflowInstanceWithResultsRequest blockingRequest =
        CreateWorkflowInstanceWithResultsRequest.newBuilder().setRequest(builder.build()).build();
    final RetriableClientFutureImpl<
            WorkflowInstanceEventResult, CreateWorkflowInstanceWithResultsResponse>
        future =
            new RetriableClientFutureImpl<>(
                CreateWorkflowInstanceWithResultsResponseImpl::new,
                retryPredicate,
                streamObserver -> sendWithResponse(blockingRequest, streamObserver));

    sendWithResponse(blockingRequest, future);
    return future;
  }

  private void sendWithResponse(
      CreateWorkflowInstanceWithResultsRequest request,
      StreamObserver<CreateWorkflowInstanceWithResultsResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .createWorkflowInstanceWithResults(request, future);
  }
}
