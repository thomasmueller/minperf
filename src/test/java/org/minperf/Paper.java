package org.minperf;

/**
 * A helper class to produce data of the RecSplit paper.
 */
public class Paper {

    public static void main(String... args) {

        simpleTest();

        // 4.3 Split Rule
        SettingsTest.printSplitRule();
        // 4.4 Data Format
        BitCodes.printPositiveMapping();
        Graphics.generateSampleTikz();
        // 4.7 Probabilities
        Probability.bucketTooLarge();
        Probability.asymmetricCase();
        // 4.8 Rice
        BitCodes.printRiceExamples();
        BitCodes.printEliasDeltaExample();
        // 4.9 Space Usage and Generation Time
        TimeAndSpaceEstimator.spaceUsageEstimateSmallSet();
        TimeAndSpaceEstimator.spaceUsageEstimate();
        // 5.2 Time and Space Complexity of Evaluation
        TimeAndSpaceEstimator.listMaxRecursionDepth();
        // 6.1 Reasonable Parameter Values
        RandomizedTest.reasonableParameterValues();
        // 6.2 Using Real-World Data
        WikipediaTest.main();

        // does not always work (depends on the hardware):
        // 4.1 Parameters
        RandomizedTest.verifyParameters();

        // slow:

        // 4.5 Evaluation Algorithm
        TimeAndSpaceEstimator.listEvalulationTimes();
        // 4.1 Parameters
        TimeAndSpaceEstimator.calcBestSizes();
        RandomizedTest.verifyParametersBestSize();
        // 6 Experimental Results
        RandomizedTest.experimentalResults();

    }

    private static void simpleTest() {
        for (int i = 8; i < 1000; i *= 2) {
            RandomizedTest.test(2, i, i, true);
        }
        for (int i = 100; i < 10000; i *= 2) {
            RandomizedTest.test(6, 20, i, true);
        }
    }

}
