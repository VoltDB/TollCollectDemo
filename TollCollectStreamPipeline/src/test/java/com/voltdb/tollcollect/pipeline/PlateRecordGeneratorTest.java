/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.pipeline;


class PlateRecordGeneratorTest {

    public static void main(String[] args) {
        PlateRecordGenerator plateRecordGenerator = new PlateRecordGenerator();
        for (int i = 0; i < 10000; i++) {
            plateRecordGenerator.generatePlateRecord();
        }
    }
}