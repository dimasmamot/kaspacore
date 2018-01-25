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

/*
 * We have to use "com.databricks.spark.avro" because "SchemaConverters.toSqlType"
 * is only visible in the scope of the package.
 */
package com.databricks.spark.avro

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col


object DebugConfluentSparkAvroUtils {
  def main(args: Array[String]): Unit = {
    val kafkaUrl = args(0)
    val schemaRegistryUrl = args(1)
    val topic = args(2)

    val spark = SparkSession.builder().master("local[2]").getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")
    val df = spark.readStream.format("kafka")
      .option("kafka.bootstrap.servers", kafkaUrl)
      .option("subscribe", topic)
      .option("startingOffsets", "earliest")
      .load()

    val utils = new ConfluentSparkAvroUtils(schemaRegistryUrl)
    val keyDes = utils.deserializerForSubject(topic + "-key")
    val valDes = utils.deserializerForSubject(topic + "-value")

    val decoded = df.select(
      valDes(col("value")).alias("value")
    )

    val parsed = decoded.select("value.*")

    val sigCount = parsed.groupBy("sig_id").count()
    val alertmsg = parsed.groupBy("alert_msg").count()

    val query1 = sigCount
      .writeStream
      .outputMode("complete")
      .format("console")
      .start()

    val query2 = alertmsg
      .writeStream
      .outputMode("complete")
      .format("console")
      .start() 

    query1.awaitTermination()
    query2.awaitTermination()
  }
}
