/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.pipeline;

import java.util.concurrent.ThreadLocalRandom;

public class PlateRecordGenerator {

    private static final String[] LOCATIONS = new String[]{
            "Skyline Toll Plaza",
            "Nexus Crossing",
            "Elevation Pass",
            "Infinity Bridge",
            "Horizon Bridge",
            "Echo Lane Station",
            "Quantum Tunnel",
            "Tannhäuser Gate",
            "Astrolink Path",
            "Orbital Gate",
            "Lumen Tunnel"
    };

    private static final String[] VEHICLE_TYPES = new String[]{
            "Motorcycle",
            "Car",
            "Small Truck",
            "Large Truck",
            "Bus"
    };

    private String randomLocation() {
        double exponentialDistributionSample = ThreadLocalRandom.current().nextExponential() * 2;

        int enumIndex = (int) Math.round(exponentialDistributionSample); // Map to nearest integer
        enumIndex = Math.max(0, Math.min(LOCATIONS.length - 1, enumIndex)); // Clamp to valid indices
        return LOCATIONS[enumIndex];
    }

    private String randomVehicleType() {
        double mean = (VEHICLE_TYPES.length - 1) / 2.0; // Center around the middle index
        double stdDev = VEHICLE_TYPES.length / 4.0; // Adjust spread (smaller for tighter grouping)

        double gaussianDistributionSample = ThreadLocalRandom.current().nextGaussian(mean, stdDev);

        int enumIndex = (int) Math.round(gaussianDistributionSample); // Map to nearest integer
        enumIndex = Math.max(0, Math.min(VEHICLE_TYPES.length - 1, enumIndex)); // Clamp to valid indices
        return VEHICLE_TYPES[enumIndex];
    }

    public String randomPlate(String location) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int plateNumber = random.nextInt(1000);
        boolean shouldGenerateInvalidPlate = random.nextDouble() > 0.9;
        if (shouldGenerateInvalidPlate) {
            return String.format("K%03d", plateNumber);
        } else {
            return String.format("X%03d", plateNumber);
        }
    }

    public PlateRecord generatePlateRecord() {
        long scanTimestamp = System.currentTimeMillis();

        String location = randomLocation();
        String lane = Integer.toString(ThreadLocalRandom.current().nextInt(5));
        String plateNum = randomPlate(location);
        String vehicleClass = randomVehicleType();

        return new PlateRecord(
                scanTimestamp,
                location,
                lane,
                plateNum,
                vehicleClass);
    }
}
