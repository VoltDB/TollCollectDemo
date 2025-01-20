package com.voltdb.tollcollect.pipeline;


class PlateRecordGeneratorTest {

    public static void main(String[] args) {
        PlateRecordGenerator plateRecordGenerator = new PlateRecordGenerator();
        for (int i = 0; i < 10000; i++) {
            plateRecordGenerator.generatePlateRecord();
        }
    }
}