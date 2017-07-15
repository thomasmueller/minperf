package org.minperf.chd;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.universal.UniversalHash;

public class CHD<T> {

    final UniversalHash<T> hash;
    final int lambda;
    final BitBuffer buff;
    int size;
    EliasFanoList arrayList;
    int bucketCount;

    CHD(UniversalHash<T> hash, BitBuffer buff) {
        this(hash, buff, 6);
    }

    CHD(UniversalHash<T> hash, BitBuffer buff, int lambda) {
        this.hash = hash;
        this.buff = buff;
        this.lambda = lambda;
    }

	public void generate(Collection<T> set) {
		int size = set.size();
		buff.writeEliasDelta(size + 1);
		int bucketCount = (size + lambda - 1) / lambda;
		class Bucket {
            int id;
            ArrayList<T> list = new ArrayList<>();
		    Bucket(int id) {
		        this.id = id;
		    }
		}
		ArrayList<Bucket> buckets = new ArrayList<>(bucketCount);
		for (int i = 0; i < bucketCount; i++) {
			buckets.add(new Bucket(i));
		}
		int targetLen = size;
		BitSet mapped = new BitSet(targetLen);
		for (T x : set) {
			long h = hash.universalHash(x, 0);
			int b = Settings.reduce((int) h, bucketCount);
			buckets.get(b).list.add(x);
		}
		Collections.sort(buckets, new Comparator<Bucket>() {

			@Override
			public int compare(Bucket o1, Bucket o2) {
				return Integer.compare(o2.list.size(), o1.list.size());
			}

		});
		int[] array = new int[bucketCount + 1];
		int[] candidates = new int[buckets.get(0).list.size()];
		for (int i = 0; i < bucketCount; i++) {
			Bucket bucket = buckets.get(i);
			if (bucket.list.size() == 0) {
				break;
			}
			int d = 0;
			while (true) {
				int j = 0;
				for (; j < bucket.list.size(); j++) {
					T x = bucket.list.get(j);
					long h = hash.universalHash(x, d);
					int p = Settings.reduce((int) h, targetLen);
					if (mapped.get(p)) {
						j--;
						break;
					}
					candidates[j] = p;
					mapped.set(p);
				}
				if (j == bucket.list.size()) {
					// found
					array[bucket.id + 1] = d;
					break;
				}
				// not found
				while (j >= 0) {
					mapped.clear(candidates[j]);
					j--;
				}
				d++;
			}
		}
		EliasFanoList.generate(array, buff);
	}

    public void load() {
        size = (int) buff.readEliasDelta() - 1;
        bucketCount = (size + lambda - 1) / lambda;
        arrayList = EliasFanoList.load(buff);
    }

    public int evaluate(T x) {
        long h = hash.universalHash(x, 0);
        int b = Settings.reduce((int) h, bucketCount);
        int d = arrayList.get(b + 1);
        h = hash.universalHash(x, d);
        return Settings.reduce((int) h, size);
    }

}
