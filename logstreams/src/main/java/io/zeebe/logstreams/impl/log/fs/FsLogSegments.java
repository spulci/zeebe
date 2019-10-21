/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log.fs;

import java.util.concurrent.CopyOnWriteArrayList;

public final class FsLogSegments {

  protected CopyOnWriteArrayList<FsLogSegment> segments;

  private FsLogSegments(FsLogSegment[] initialSegments) {
    this.segments = new CopyOnWriteArrayList<>(initialSegments);
  }

  public static FsLogSegments fromFsLogSegmentsArray(FsLogSegment[] initialSegments) {
    return new FsLogSegments(initialSegments);
  }

  /** invoked by the conductor after a new segment has been allocated */
  public void addSegment(FsLogSegment segment) {
    segments.add(segment);
  }

  public FsLogSegment getSegment(int segmentId) {
    final int firstSegmentId = getFirstSegmentId();
    final int segmentIdx = segmentId - firstSegmentId;

    if (0 <= segmentIdx && segmentIdx < segments.size()) {
      return segments.get(segmentIdx);
    } else {
      return null;
    }
  }

  public void deleteSegmentsUntil(int segmentId) {
    segments.removeIf(
        segment -> {
          final boolean shouldBeRemoved = segment.getSegmentId() < segmentId;
          if (shouldBeRemoved) {
            segment.closeSegment();
            segment.delete();
          }
          return shouldBeRemoved;
        });
  }

  public int getFirstSegmentId() {
    if (segments.isEmpty()) {
      throw new IllegalStateException("Expected to have at least one open segment!");
    }

    return segments.get(0).getSegmentId();
  }

  public void closeAll() {
    segments.removeIf(
        segment -> {
          segment.closeSegment();
          return true;
        });
  }

  public FsLogSegment getFirst() {
    if (segments.isEmpty()) {
      return null;
    }
    return segments.get(0);
  }

  public int getLastSegmentId() {
    final int size = segments.size();
    return segments.get(size - 1).getSegmentId();
  }
}
