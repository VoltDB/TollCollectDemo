package com.voltdb.tollcollect.pipeline;

public record PlateRecord(
        long scanTimestamp,
        String location,
        String lane,
        String plateNum,
        String vehicleClass
) {
}
