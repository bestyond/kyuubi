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

package org.apache.kyuubi.service

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.kyuubi.config.KyuubiConf

abstract class SeverLike(name: String) extends CompositeService(name) {

  private val OOMHook = new Runnable { override def run(): Unit = stop() }
  private val started = new AtomicBoolean(false)

  protected val backendService: AbstractBackendService
  private lazy val frontendService = new FrontendService(backendService, OOMHook)

  def connectionUrl: String = frontendService.connectionUrl

  override def initialize(conf: KyuubiConf): Unit = {
    addService(backendService)
    addService(frontendService)
    super.initialize(conf)
  }

  override def start(): Unit = {
    super.start()
    started.set(true)
  }

  protected def stopServer(): Unit

  override def stop(): Unit = {
    try {
      stopServer()
    } catch {
      case t: Throwable =>
        warn(s"Error stopping spark ${t.getMessage}", t)
    } finally {
      if (started.getAndSet(false)) {
        super.stop()
      }
    }
  }
}
