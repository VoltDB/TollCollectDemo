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

    private enum Location {
        SKYLINE_TOLL_PLAZA("Skyline Toll Plaza", 0.1),
        NEXUS_CROSSING("Nexus Crossing", 0.04),
        ELEVATION_PASS("Elevation Pass", 0.13),
        INFINITY_BRIDGE("Infinity Bridge", 0.32),
        HORIZON_BRIDGE("Horizon Bridge", 0.15),
        ECHO_LANE_STATION("Echo Lane Station", 0.05),
        QUANTUM_TUNNEL("Quantum Tunnel", 0.34),
        TANNHAUSER_GATE("Tannhäuser Gate", 0.09),
        ASTROLINK_PATH("Astrolink Path", 0.03),
        ORBITAL_GATE("Orbital Gate", 0.08),
        LUMEN_TUNNEL("Lumen Tunnel", 0.12);

        private final String name;
        private final double invalidScanProbabilities;

        Location(String name, double invalidScanProbabilities) {
            this.name = name;
            this.invalidScanProbabilities = invalidScanProbabilities;
        }

        public String getName() {
            return name;
        }

        public boolean shouldProduceInvalidScan() {
            return ThreadLocalRandom.current().nextFloat() < invalidScanProbabilities;
        }
    }

    private static final String[] VEHICLE_TYPES = new String[]{
            "Motorcycle",
            "Car",
            "Small Truck",
            "Large Truck",
            "Bus"
    };

    private Location randomLocation() {
        double exponentialDistributionSample = ThreadLocalRandom.current().nextExponential() * 2;

        int enumIndex = (int) Math.round(exponentialDistributionSample); // Map to nearest integer
        enumIndex = Math.max(0, Math.min(Location.values().length - 1, enumIndex)); // Clamp to valid indices
        return Location.values()[enumIndex];
    }

    private String randomVehicleType() {
        double mean = (VEHICLE_TYPES.length - 1) / 2.0; // Center around the middle index
        double stdDev = VEHICLE_TYPES.length / 4.0; // Adjust spread (smaller for tighter grouping)

        double gaussianDistributionSample = ThreadLocalRandom.current().nextGaussian(mean, stdDev);

        int enumIndex = (int) Math.round(gaussianDistributionSample); // Map to nearest integer
        enumIndex = Math.max(0, Math.min(VEHICLE_TYPES.length - 1, enumIndex)); // Clamp to valid indices
        return VEHICLE_TYPES[enumIndex];
    }

    String randomPlate(Location location) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int plateNumber = random.nextInt(1000);
        if (location.shouldProduceInvalidScan()) {
            return String.format("K%03d", plateNumber);
        } else {
            return String.format("X%03d", plateNumber);
        }
    }

    public PlateRecord generatePlateRecord() {
        long scanTimestamp = System.currentTimeMillis();

        Location location = randomLocation();
        String lane = Integer.toString(ThreadLocalRandom.current().nextInt(5));
        String plateNum = randomPlate(location);
        String vehicleClass = randomVehicleType();

        return new PlateRecord(
                scanTimestamp,
                location.getName(),
                lane,
                plateNum,
                vehicleClass);
    }
}
