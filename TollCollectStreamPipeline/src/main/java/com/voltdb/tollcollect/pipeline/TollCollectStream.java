/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.pipeline;

import org.voltdb.stream.api.ExecutionContext;
import org.voltdb.stream.api.Sinks;
import org.voltdb.stream.api.Sources;
import org.voltdb.stream.api.pipeline.VoltPipeline;
import org.voltdb.stream.api.pipeline.VoltStreamBuilder;

public class TollCollectStream implements VoltPipeline {

    @Override
    public void define(VoltStreamBuilder stream) {
        // Get the configuration value from the environment.
        ExecutionContext.ConfigurationContext configurator = stream.getExecutionContext().configurator();
        int tps = configurator.findByPath("tps").asInt();
        String voltdbServer = configurator.findByPath("voltdb.server").asString();

        PlateRecordGenerator plateRecordGenerator = new PlateRecordGenerator();

        stream
                .withName("Toll Collector Data Stream")
                .consumeFromSource(
                        Sources.generateAtRate(
                                tps,
                                plateRecordGenerator::generatePlateRecord
                        )
                )
                .processWith(
                        record -> new Object[]{
                                record.scanTimestamp(),
                                record.location(),
                                record.lane(),
                                record.plateNum(),
                                record.vehicleClass()
                        }
                )
                .terminateWithSink(
                        Sinks.volt().procedureCall()
                                .withProcedureName("ProcessPlate")
                                .withHostAndStandardPort(voltdbServer)
                );
    }
}
