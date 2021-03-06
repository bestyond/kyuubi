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

package org.apache.kyuubi.engine.spark

import java.nio.file.{Files, Path, Paths}

import scala.collection.mutable.ArrayBuffer

import org.apache.kyuubi._
import org.apache.kyuubi.config.KyuubiConf
import org.apache.kyuubi.engine.EngineConf.ENGINE_SPARK_MAIN_RESOURCE
import org.apache.kyuubi.engine.ProcessBuilderLike

class SparkProcessBuilder(
    override val proxyUser: String,
    conf: Map[String, String],
    override val env: Map[String, String] = sys.env)
  extends ProcessBuilderLike {

  import SparkProcessBuilder._

  override protected val executable: String = {
    val path = env.get("SPARK_HOME").map { sparkHome =>
      Paths.get(sparkHome, "bin", "spark-submit").toAbsolutePath
    } getOrElse {
      val sparkVer = SPARK_COMPILE_VERSION
      val hadoopVer = HADOOP_COMPILE_VERSION.take(3)
      val hiveVer = if (HIVE_COMPILE_VERSION.take(3).toDouble < 2.3) "-hive1.2" else ""
      Paths.get(
        "..",
        "externals",
        "kyuubi-download",
        "target",
        s"spark-$sparkVer-bin-hadoop$hadoopVer$hiveVer",
        "bin", "spark-submit")
    }
    path.toAbsolutePath.toFile.getCanonicalPath
  }

  override def mainClass: String = "org.apache.kyuubi.engine.spark.SparkSQLEngine"

  override def mainResource: Option[String] = {
    // 1. get the main resource jar for user specified config first
    val jarName = s"kyuubi-spark-sql-engine-$KYUUBI_VERSION.jar"
    conf.get(ENGINE_SPARK_MAIN_RESOURCE.key).filter { userSpecified =>
      Files.exists(Paths.get(userSpecified))
    }.orElse {
      // 2. get the main resource jar from system build default
      env.get(KyuubiConf.KYUUBI_HOME)
        .map { Paths.get(_, "externals", "engines", "spark", jarName) }
        .filter(Files.exists(_)).map(_.toAbsolutePath.toFile.getCanonicalPath)
    }.orElse {
      // 3. get the main resource from dev environment
      Option(Paths.get("externals", "kyuubi-spark-sql-engine", "target", jarName))
        .filter(Files.exists(_)).orElse {
        Some(Paths.get("..", "externals", "kyuubi-spark-sql-engine", "target", jarName))
      }.map(_.toAbsolutePath.toFile.getCanonicalPath)
    }
  }

  override protected def workingDir: Path = {
    env.get("KYUUBI_WORK_DIR_ROOT").map { root =>
      Utils.createTempDir(root, proxyUser)
    }.getOrElse {
      Utils.createTempDir(namePrefix = proxyUser)
    }
  }

  override protected def commands: Array[String] = {
    val buffer = new ArrayBuffer[String]()
    buffer += executable
    buffer += CLASS
    buffer += mainClass
    conf.foreach { case (k, v) =>
      buffer += CONF
      buffer += s"$k=$v"
    }
    buffer += PROXY_USER
    buffer += proxyUser

    mainResource.foreach { r => buffer += r }

    buffer.toArray
  }

  override def toString: String = commands.mkString(" ")
}


object SparkProcessBuilder {
  private final val CONF = "--conf"
  private final val CLASS = "--class"
  private final val PROXY_USER = "--proxy-user"
}
