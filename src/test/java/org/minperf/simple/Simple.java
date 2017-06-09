package org.minperf.simple;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.minperf.universal.StringHash;

public class Simple {

    public static void main(String... args) {
        example();
        printAbcKeySample();
        printMonthSample();
        test();
    }

    private static void example() {
        Set<String> set = new HashSet<>(Arrays.asList("a", "b", "c", "d", "e"));
        int[] desc = SimpleRecSplit.Generator.generate(set);
        for(String s : set) {
            System.out.println(s + " -> " +
                    SimpleRecSplit.Evaluator.evaluate(s, desc));
        }
    }

    private static void printAbcKeySample() {
        StringHash hash = new StringHash();
        String[] list = new String[] { "a", "b", "c", "d" };
        for (String s : list) {
            System.out.print(s + ": ");
            for (int i = 0; i < 20; i++) {
                long x = hash.universalHash(s, i) & 3;
                System.out.print(x + "\t");
            }
            System.out.println();
        }
    }

    private static void printMonthSample() {
        Set<String> set = new LinkedHashSet<String>();
        for(int i=1; i<=12; i++) {
            String s = Month.of(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase();
            set.add(s);
        }
        System.out.println(SimpleRecSplit.Generator.partition(set, 4));
        set = SimpleRecSplit.Generator.partition(set, 4).get(0);
        for (int index = 0;; index++) {
            List<Set<String>> list = SimpleRecSplit.Generator.trySplit(set, index);
            if (list != null) {
                System.out.println(index + " " + list);
                for (int i = 0; i < list.size(); i++) {
                    Set<String> s = list.get(i);
                    System.out.println(s);
                    for (int i2 = 0;; i2++) {
                        if (SimpleRecSplit.Generator.canMapDirectly(s, i2)) {
                            System.out.println("index: " + i2);
                            for (String x : s) {
                                System.out.println(x + " " + SimpleRecSplit.universalHash(x, i2, s.size()));
                            }
                            break;
                        }
                    }
                }
                break;
            }
        }
    }

    public static void test() {
        Set<String> set = new HashSet<String>();
        for (int i = 0; i < 100000; i++) {
            set.add("Hello " + i);
        }
        int[] data = SimpleRecSplit.Generator.generate(set);
        HashSet<Integer> used = new HashSet<Integer>();
        for (String x : set) {
            int e = SimpleRecSplit.Evaluator.evaluate(x, data);
            if (!used.add(e)) {
                e = SimpleRecSplit.Evaluator.evaluate(x, data);
                throw new AssertionError();
            }
        }
    }

}
