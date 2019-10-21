/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.fs.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.logstreams.impl.log.fs.FsLogSegment;
import io.zeebe.logstreams.impl.log.fs.FsLogSegments;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FsLogSegmentsTest {
  @Mock protected FsLogSegment firstSegment;

  @Mock protected FsLogSegment secondSegment;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void shouldGetFirstSegment() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment, secondSegment});

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNotNull().isEqualTo(firstSegment);
  }

  @Test
  public void shouldGetFirstSegmentWithInitialSegmentId() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment, secondSegment});

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNotNull().isEqualTo(firstSegment);
  }

  @Test
  public void shouldNotGetFirstSegmentIfEmpty() {
    final FsLogSegments fsLogSegments = FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[0]);

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNull();
  }

  @Test
  public void shouldGetSegment() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment, secondSegment});

    assertThat(fsLogSegments.getSegment(0)).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getSegment(1)).isEqualTo(secondSegment);
  }

  @Test
  public void shouldGetSegmentWithInitialSegmentId() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment, secondSegment});
    when(firstSegment.getSegmentId()).thenReturn(1);

    assertThat(fsLogSegments.getSegment(1)).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getSegment(2)).isEqualTo(secondSegment);
  }

  @Test
  public void shouldNotGetSegmentIfNotExists() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment, secondSegment});
    when(firstSegment.getSegmentId()).thenReturn(1);

    assertThat(fsLogSegments.getSegment(0)).isNull();
    assertThat(fsLogSegments.getSegment(3)).isNull();
  }

  @Test
  public void shouldAddSegment() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment});

    fsLogSegments.addSegment(secondSegment);

    assertThat(fsLogSegments.getSegment(1)).isNotNull().isEqualTo(secondSegment);
  }

  @Test
  public void shouldCloseAllSegments() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(new FsLogSegment[] {firstSegment, secondSegment});

    fsLogSegments.closeAll();

    verify(firstSegment).closeSegment();
    verify(secondSegment).closeSegment();

    assertThat(fsLogSegments.getFirst()).isNull();
  }

  @Test
  public void shouldDeleteUntilSegmentId() {
    // given
    final FsLogSegment thirdSegment = mock(FsLogSegment.class);
    final FsLogSegment fourthSegment = mock(FsLogSegment.class);
    final FsLogSegment fifthSegment = mock(FsLogSegment.class);

    when(firstSegment.getSegmentId()).thenReturn(1);
    when(secondSegment.getSegmentId()).thenReturn(2);
    when(thirdSegment.getSegmentId()).thenReturn(3);
    when(fourthSegment.getSegmentId()).thenReturn(4);
    when(fifthSegment.getSegmentId()).thenReturn(5);

    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(
            new FsLogSegment[] {
              firstSegment, secondSegment, thirdSegment, fourthSegment, fifthSegment
            });

    // when
    fsLogSegments.deleteSegmentsUntil(3);

    // then
    assertThat(fsLogSegments.getFirstSegmentId()).isEqualTo(3);
    assertThat(fsLogSegments.getFirst()).isEqualTo(thirdSegment);
  }
}
