/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.myorg.flinkinvariants;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.events.EshopRecord;

import static org.myorg.flinkinvariants.Connectors.getEshopRecordKafkaSource;

/**
 * Basic KafkaSource connector example.
 * Reads events from eshop_event_bus and prints them.
 */
public class EshopPrintJob {


	public static void main(String[] args) throws Exception {
		String broker = "localhost:29092";
		String topic = "eshop_event_bus";
		String groupId = "flink-invariant-checker";

		// Sets up the execution environment, which is the main entry point
		// to building Flink applications.
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		KafkaSource<EshopRecord> source = getEshopRecordKafkaSource(broker, topic, groupId);

		env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
				//.filter(r -> r.EventName.equals("ProductPriceChangedIntegrationEvent")
				//		|| r.EventName.equals("UserCheckoutAcceptedIntegrationEvent"))
				.print();

		// Execute program, beginning computation.
		env.execute("Flink Eshop Invariant Checker");
	}

}


