package org.minperf.bloom;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Function;

public class AnalyzeResults {

    final static String homeDir = System.getProperty("user.home");

    final static HashMap<String, String> namesMap = new HashMap<>();
    static {
        String[] map = {
                "CuckooSemiSortStable13", "CuckooSort13",
                "CuckooStable8", "Cuckoo8",
                "CuckooStable12", "Cuckoo12",
                "CuckooStable16", "Cuckoo16",
                };
        for (int i = 0; i < map.length; i += 2) {
            namesMap.put(map[i], map[i + 1]);
        }
    }

    int algorithmId = -1;
    long size;
    int randomAlgorithm;
    boolean warning;
    String[] dataLine;

    public static void main(String... args) throws IOException {
        new AnalyzeResults().processFile();
    }

    private void processFile() throws IOException {
        LineNumberReader r = new LineNumberReader(new BufferedReader(new FileReader(homeDir + "/temp/results.txt")));
        while (true) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            // System.out.println(line);
            if (line.isEmpty() || line.startsWith("million") || line.startsWith("adds/sec")
                    || line.startsWith("Using")) {
                continue;
            }
            String[] list = line.split(" +");
            if (Character.isDigit(line.charAt(0)) && line.indexOf(" size ") >= 0) {
                processEntry();
                // eg. "14:56:21 alg 0 size 20 -1"
                algorithmId = Integer.parseInt(list[2]);
                size = Integer.parseInt(list[4]);
                randomAlgorithm = Integer.parseInt(list[5]);
                warning = false;
                continue;
            }
            if (line.startsWith("WARNING")) {
                warning = true;
                continue;
            }
            dataLine = list;
        }
        processEntry();
        r.close();
        combineData();
        printQueryTimeVersusSpaceOverhead();
        printQueryTimeVersusSpaceUsage();
        printQueryTimeVersusFPP();
        printFppVersusSpaceOverhead();
        printFppVersusSpaceUsage();
//        listAll();
//        printInsertTimes();
//        printFpp();
//        printSpaceOverhead();
//        printLookup25();
//        printLookup75();
    }

    static String[] algorithmNames = new String[100];
    ArrayList<Data> allData = new ArrayList<>();
    ArrayList<Data> data = new ArrayList<>();

    static class Data {
        int algorithmId;
        int randomAlgorithm;
        double addSecond, find0, find25, find50, find75, find100;
        double e, bitsItem, optBitsItem, wastedSpace, keysMillions;
        boolean failed;

        public String toString() {
            return "alg " + algorithmNames[algorithmId] + " rnd " + randomAlgorithm + " add " + addSecond + " f0 "
                    + find0 + " f25 " + find25 + " f75 " + find75 + " f100 " + find100 + " e " + e + " bitItem "
                    + bitsItem + " opt " + optBitsItem + " waste " + wastedSpace + " keys " + keysMillions + " failed "
                    + failed + "";
        }

        public String getName() {
            String n = algorithmNames[algorithmId];
            String n2 = namesMap.get(n);
            return n2 == null ? n : n2;
        }

        public String getType() {
            String n = algorithmNames[algorithmId];
            String n2 = namesMap.get(n);
            n = n2 == null ? n : n2;
            n = n.replaceAll("[0-9]", "");
            return n;
        }

    }

    private void combineData() {
        for (int i = 0; i < allData.size(); i += 3) {
            Data[] tree = new Data[3];
            tree[0] = allData.get(i);
            tree[1] = allData.get(i + 1);
            tree[2] = allData.get(i + 2);
            if (tree[0].randomAlgorithm == 0) {
                continue;
            }
            if (tree[0].find100 == 0 || tree[1].find100 == 0 || tree[2].find100 == 0) {
                System.out.println("missing entry at " + tree[0]);
            } else {
                if (tree[0].randomAlgorithm < 0) {
                    // TODO verify results with randomAlgorithm >= 0 match
                    Data combined = combineData(tree);
                    data.add(combined);
                }
            }
        }
    }

    private Data combineData(Data[] list) {
        for (Data d : list) {
            if (d.failed) {
                return null;
            }
        }
        Data result = new Data();
        result.algorithmId = list[0].algorithmId;
        result.randomAlgorithm = list[0].randomAlgorithm;
        result.keysMillions = list[0].keysMillions;
        result.addSecond = combineData(list, 2, (d) -> d.addSecond);
        result.find0 = combineData(list, 3, (d) -> d.find0);
        result.find25 = combineData(list, 3, (d) -> d.find25);
        result.find50 = combineData(list, 3, (d) -> d.find50);
        result.find75 = combineData(list, 3, (d) -> d.find75);
        result.find100 = combineData(list, 3, (d) -> d.find100);
        result.e = combineData(list, 100, (d) -> d.e);
        result.bitsItem = combineData(list, 2, (d) -> d.bitsItem);
        result.optBitsItem = combineData(list, 10, (d) -> d.optBitsItem);
        result.wastedSpace = combineData(list, 50, (d) -> d.wastedSpace);
        return result;
    }

    private double combineData(Data[] list, int maxPercentDiff, Function<Data, Double> f) {
        double[] x = new double[list.length];
        for (int i = 0; i < list.length; i++) {
            x[i] = f.apply(list[i]);
        }
        if (x.length != 3) {
            throw new AssertionError();
        }
        Arrays.sort(x);
        double median = x[1];
        double diff1 = Math.abs(x[0] - median);
        double diff2 = Math.abs(x[2] - median);
        if (diff1 > maxPercentDiff * median / 100 && diff2 > maxPercentDiff * median / 100) {
            System.out.println("avg +/- > " + maxPercentDiff + "% " + Arrays.toString(x));
            System.out.println(list[0]);
            System.out.println(list[1]);
            System.out.println(list[2]);
        }
        return median;
    }

    private void processEntry() {
        if (algorithmId < 0) {
            return;
        }
        Data data = new Data();
        data.algorithmId = algorithmId;
        data.randomAlgorithm = randomAlgorithm;
        data.keysMillions = size;
        // million find find find find find optimal wasted million
        // adds/sec 0% 25% 50% 75% 100% ε bits/item bits/item space keys
        // Xor12SplitMix/2 5.77 24.98 24.95 24.94 25.00 24.93 0.026% 14.76 11.89
        // 24.1% 60.0
        if (dataLine == null) {
            // no data
            throw new AssertionError();
            // data.failed = true;
        } else {
            String name = dataLine[0].replace('_', '-');
            if (algorithmNames[algorithmId] == null) {
                algorithmNames[algorithmId] = name;
            } else if (!name.equals(algorithmNames[algorithmId])) {
                throw new AssertionError();
            }
            data.addSecond = Double.parseDouble(dataLine[1]);
            data.find0 = Double.parseDouble(dataLine[2]);
            data.find25 = Double.parseDouble(dataLine[3]);
            data.find50 = Double.parseDouble(dataLine[4]);
            data.find75 = Double.parseDouble(dataLine[5]);
            data.find100 = Double.parseDouble(dataLine[6]);
            data.e = Double.parseDouble(dataLine[7].substring(0, dataLine[7].length() - 1));
            data.bitsItem = Double.parseDouble(dataLine[8]);
            data.optBitsItem = Double.parseDouble(dataLine[9]);
            data.wastedSpace = Double.parseDouble(dataLine[10].substring(0, dataLine[10].length() - 1));
            data.failed = warning;
            double keys = Double.parseDouble(dataLine[11]);
            if (keys != data.keysMillions) {
                throw new AssertionError();
            }
        }
        // System.out.println(data);
        allData.add(data);
        dataLine = null;
    }

    static int removeHighest(double[] sort) {
        double max = 0;
        int best = -1;
        for (int i = 0; i < sort.length; i++) {
            double x = sort[i];
            if (x > max) {
                best = i;
                max = x;
            }
        }
        if (best >= 0) {
            sort[best] = 0;
        }
        return best;
    }

    double[] sort() {
        final double[] sort = new double[algorithmNames.length];
        for(Data d : data) {
            sort[d.algorithmId] = 1 / d.addSecond;
        }
        Collections.sort(data, (o1, o2) -> {
            if (o1.algorithmId == o2.algorithmId) {
                return 0;
            }
            return Double.compare(sort[o1.algorithmId], sort[o2.algorithmId]);
        });
        return sort;
    }

    void listAll() {
        System.out.println("==== All data ====================================");
        double[] sort = sort();
        System.out.println("name construct find0 find25 find50 find75 find100 bitsPerItem e spaceOverhead millionKeys");
        while(true) {
            int algorithmId = removeHighest(sort);
            if (algorithmId < 0) {
                break;
            }
            String name = null;
            for(Data d : data) {
                if (algorithmId != d.algorithmId) {
                    continue;
                }
                name = algorithmNames[d.algorithmId];
                System.out.println(name +
                        " " + nanosPerKey(d.addSecond) +
                        " " + nanosPerKey(d.find0) +
                        " " + nanosPerKey(d.find25) +
                        " " + nanosPerKey(d.find50) +
                        " " + nanosPerKey(d.find75) +
                        " " + nanosPerKey(d.find100) +
                        " " + d.bitsItem +
                        " " + d.e +
                        " " + d.wastedSpace +
                        " " + d.keysMillions +
                        "");
            }
        }
    }

    void printLookup25() {
        System.out.println("==== Lookup 25% ====================================");
        double[] sort = sort();
        nextAlgorithm:
        while(true) {
            int algorithmId = removeHighest(sort);
            if (algorithmId < 0) {
                break;
            }
            String name = null;
            boolean first = true;
            for(Data d : data) {
                if (algorithmId != d.algorithmId) {
                    continue;
                }
                name = algorithmNames[d.algorithmId];
                if (!listAlgorithm(name)) {
                    continue nextAlgorithm;
                }
                if (first) {
                    first = false;
                    printPlot();
                }
                printData(d.keysMillions, nanosPerKey(d.find25));
            }
            printEnd(name);
        }
    }

    void printLookup75() {
        System.out.println("==== Lookup 75% ====================================");
        double[] sort = sort();
        nextAlgorithm:
        while(true) {
            int algorithmId = removeHighest(sort);
            if (algorithmId < 0) {
                break;
            }
            String name = null;
            boolean first = true;
            for(Data d : data) {
                if (algorithmId != d.algorithmId) {
                    continue;
                }
                name = algorithmNames[d.algorithmId];
                if (!listAlgorithm(name)) {
                    continue nextAlgorithm;
                }
                if (first) {
                    first = false;
                    printPlot();
                }
                printData(d.keysMillions, nanosPerKey(d.find75));
            }
            printEnd(name);
        }
    }

    void printFpp() {
        System.out.println("==== FPP ====================================");
        double[] sort = sort();
        nextAlgorithm:
        while(true) {
            int algorithmId = removeHighest(sort);
            if (algorithmId < 0) {
                break;
            }
            String name = null;
            boolean first = true;
            for(Data d : data) {
                if (algorithmId != d.algorithmId) {
                    continue;
                }
                name = algorithmNames[d.algorithmId];
                if (!listAlgorithm(name)) {
                    continue nextAlgorithm;
                }
                if (first) {
                    first = false;
                    printPlot();
                }
                printData(d.keysMillions, d.e);
            }
            printEnd(name);
        }
    }

    void printInsertTimes() {
        System.out.println("==== insert ====================================");
        double[] sort = sort();
        nextAlgorithm:
        while(true) {
            int algorithmId = removeHighest(sort);
            if (algorithmId < 0) {
                break;
            }
            String name = null;
            boolean first = true;
            for(Data d : data) {
                if (algorithmId != d.algorithmId) {
                    continue;
                }
                name = algorithmNames[d.algorithmId];
                if (!listAlgorithm(name)) {
                    continue nextAlgorithm;
                }
                if (first) {
                    first = false;
                    printPlot();
                }
                printData(d.keysMillions, nanosPerKey(d.addSecond));
            }
            printEnd(name);
        }
    }

    private void printQueryTimeVersusSpaceOverhead() {
        System.out.println("==== Query Time vs Overhead ====================================");
        sortByName();
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("waste ns  label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 0;
            double x = nanosPerKey(d.find100);
            System.out.println(d.wastedSpace + " " + x + " " +
                    d.getName() + " " + color + " " + align);
            typeList += "(" + d.wastedSpace + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }

    private void printQueryTimeVersusFPP() {
        System.out.println("==== Query Time vs FPP ====================================");
        sortByName();
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("fpp ns  label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 0;
            double x = nanosPerKey(d.find100);
            System.out.println(d.e + " " + x + " " +
                    d.getName() + " " + color + " " + align);
            typeList += "(" + d.e + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }

    private void printQueryTimeVersusSpaceUsage() {
        System.out.println("==== Query Time vs Space Usage ====================================");
        sortByName();
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("bits/key ns  label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 0;
            double x = nanosPerKey(d.find100);
            System.out.println(d.bitsItem + " " + x + " " +
                    d.getName() + " " + color + " " + align);
            typeList += "(" + d.bitsItem + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }


    private void printFppVersusSpaceOverhead() {
        System.out.println("==== FPP vs Overhead ====================================");
        sortByName();
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("waste fpp  label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 0;
            double x = d.e;
            System.out.println(d.wastedSpace + " " + x + " " +
                    d.getName() + " " + color + " " + align);
            typeList += "(" + d.wastedSpace + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }

    private void sortByName() {
        data.sort((o1, o2) -> {
            int result = o1.getType().compareTo(o2.getType());
            return result == 0 ? -Double.compare(o1.e, o2.e) : result;
        });
    }

    void printSpaceOverhead() {
        System.out.println("==== Space Overhead ====================================");
        double[] sort = sort();
        nextAlgorithm:
        while(true) {
            int algorithmId = removeHighest(sort);
            if (algorithmId < 0) {
                break;
            }
            String name = null;
            boolean first = true;
            for(Data d : data) {
                if (algorithmId != d.algorithmId) {
                    continue;
                }
                name = algorithmNames[d.algorithmId];
                if (!listAlgorithm(name)) {
                    continue nextAlgorithm;
                }
                if (first) {
                    first = false;
                    printPlot();
                }
                printData(d.keysMillions , d.wastedSpace);
            }
            printEnd(name);
        }
    }

    private void printFppVersusSpaceUsage() {
        System.out.println("==== FPP vs Space Usage ====================================");
        sortByName();
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("bits/key fpp label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 0;
            double x = d.e;
            System.out.println(d.bitsItem + " " + x + " " +
                    d.getName() + " " + color + " " + align);
            typeList += "(" + d.bitsItem + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }

    static boolean listAlgorithm(String name) {
        return !name.equals("GCS");
    }

    private void printEnd(String name) {
        System.out.println("};");
        System.out.println("    \\addlegendentry{" + name + "}");
    }

    private void printPlot() {
        System.out.print("    \\addplot plot coordinates {");
    }

    private void printData(double x, double y) {
        System.out.print("(" + x + ", " + y + ")");
    }

    static double nanosPerKey(double millionKeysPerSecond) {
        return Math.round(1 / millionKeysPerSecond * 1000);
    }

}
