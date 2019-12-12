/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.arrow.adapter.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.memory.BaseAllocator;
import org.apache.arrow.memory.BufferLedger;
import org.apache.arrow.memory.Ownerships;
import org.apache.arrow.vector.ipc.message.ArrowFieldNode;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;

import io.netty.buffer.ArrowBuf;

/**
 * ArrowRecordBatchBuilderImpl used to wrap native returned data into an ArrowRecordBatch.
 */
public class ArrowRecordBatchBuilderImpl {

  private final BaseAllocator allocator;
  private final ArrowRecordBatchBuilder recordBatchBuilder;

  /**
   * Create ArrowRecordBatchBuilderImpl instance from ArrowRecordBatchBuilder.
   * @param allocator BufferAllocator for native created buffers
   * @param recordBatchBuilder ArrowRecordBatchBuilder instance.
   */
  public ArrowRecordBatchBuilderImpl(BaseAllocator allocator, ArrowRecordBatchBuilder recordBatchBuilder) {
    this.allocator = allocator;
    this.recordBatchBuilder = recordBatchBuilder;
  }

  /**
   * Build ArrowRecordBatch from ArrowRecordBatchBuilder instance.
   * @throws IOException throws exception
   */
  public ArrowRecordBatch build() throws IOException {
    if (recordBatchBuilder.length == 0) {
      return null;
    }

    List<ArrowFieldNode> nodes = new ArrayList<ArrowFieldNode>();
    for (ArrowFieldNodeBuilder tmp : recordBatchBuilder.nodeBuilders) {
      nodes.add(new ArrowFieldNode(tmp.length, tmp.nullCount));
    }

    List<ArrowBuf> buffers = new ArrayList<ArrowBuf>();
    for (ArrowBufBuilder tmp : recordBatchBuilder.bufferBuilders) {
      buffers.add(createArrowBuf(tmp));
    }
    return createRecordBatch(nodes, buffers);
  }

  /**
   * Create {@link ArrowRecordBatch} then decrease ref count numbers by 1 for the populated buffers.
   * We have to do this because each {@link ArrowRecordBatch} already holds internal ref for buffers it uses.
   *
   * @see ArrowRecordBatch#ArrowRecordBatch(int, java.util.List, java.util.List, boolean)
   */
  private ArrowRecordBatch createRecordBatch(List<ArrowFieldNode> nodes, List<ArrowBuf> buffers) {
    try {
      return new ArrowRecordBatch(recordBatchBuilder.length, nodes, buffers);
    } finally {
      buffers.forEach(b -> b.getReferenceManager().release());
    }
  }

  private ArrowBuf createArrowBuf(ArrowBufBuilder tmp) {
    final AdaptorAllocationManager allocationManager =
        new AdaptorAllocationManager(tmp.nativeInstanceId, allocator, tmp.memoryAddress, tmp.size);
    final BufferLedger ledger = Ownerships.get().takeOwnership(allocator, allocationManager);
    return new ArrowBuf(ledger, null, tmp.size, tmp.memoryAddress, false);
  }
} 
