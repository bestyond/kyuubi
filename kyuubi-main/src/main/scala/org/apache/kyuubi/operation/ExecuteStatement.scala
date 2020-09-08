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

package org.apache.kyuubi.operation

import org.apache.hive.service.rpc.thrift.{TCLIService, TExecuteStatementReq, TSessionHandle}

import org.apache.kyuubi.session.Session

class ExecuteStatement(
    session: Session,
    client: TCLIService.Iface,
    remoteSessionHandle: TSessionHandle,
    statement: String)
  extends KyuubiOperation(
    OperationType.EXECUTE_STATEMENT, session, client, remoteSessionHandle) {

  override protected def runInternal(): Unit = {
    try {
      val req = new TExecuteStatementReq(remoteSessionHandle, statement)
      val resp = client.ExecuteStatement(req)
      verifyTStatus(resp.getStatus)
      _remoteOpHandle = resp.getOperationHandle
    } catch onError()
  }
}