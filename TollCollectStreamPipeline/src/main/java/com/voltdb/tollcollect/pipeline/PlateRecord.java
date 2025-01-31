/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.pipeline;

public record PlateRecord(
        long scanTimestamp,
        String location,
        String lane,
        String plateNum,
        String vehicleClass
) {
}
