package io.zeebe.client.impl.command;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultsResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class CreateWorkflowInstanceWithResultsCommandImpl implements
  FinalCommandStep<CreateWorkflowInstanceWithResultsResponse> {

  private final GatewayStub asyncStub;
  private final ZeebeObjectMapper objectMapper;
  private Duration requestTimeout;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;

  public CreateWorkflowInstanceWithResultsCommandImpl(GatewayStub asyncStub,
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
  public FinalCommandStep<CreateWorkflowInstanceWithResultsResponse> requestTimeout(
    Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<CreateWorkflowInstanceWithResultsResponse> send() {
    CreateWorkflowInstanceWithResultsRequest blockingRequest =
      CreateWorkflowInstanceWithResultsRequest.newBuilder().setRequest(builder.build()).build();
    final RetriableClientFutureImpl<CreateWorkflowInstanceWithResultsResponse, CreateWorkflowInstanceWithResultsResponse> future =
      new RetriableClientFutureImpl<>(
        Function.identity(),
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
