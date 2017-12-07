package org.minperf.chd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.universal.UniversalHash;

/**
 * This implementation does not use holes.
 *
 * @param <T> the key type
 */
public class CHD2<T> {

    final UniversalHash<T> hash;
    final int lambda;
    final int k;
    final BitBuffer buff;
    int size;
    int targetLen;
    EliasFanoList arrayList;
    int bucketCount;

    CHD2(UniversalHash<T> hash, BitBuffer buff) {
        this(hash, buff, 6, 8);
    }

    CHD2(UniversalHash<T> hash, BitBuffer buff, int lambda, int k) {
        this.hash = hash;
        this.buff = buff;
        this.lambda = lambda;
        this.k = k;
    }

	public void generate(Collection<T> set) {
		int size = set.size();
		int targetLen = (int) (size + k - 1) / k;
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
		int[] counters = new int[targetLen];
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
					if (counters[p] >= k) {
						j--;
						break;
					}
					candidates[j] = p;
					counters[p]++;
				}
				if (j == bucket.list.size()) {
					// found
					array[bucket.id + 1] = d;
					break;
				}
				// not found
				while (j >= 0) {
				    counters[candidates[j]]--;
					j--;
				}
				d++;
			}
		}
		EliasFanoList.generate(array, buff);
	}

    public void load() {
        size = (int) buff.readEliasDelta() - 1;
        targetLen = (int) (size + k - 1) / k;
        bucketCount = (size + lambda - 1) / lambda;
        arrayList = EliasFanoList.load(buff);
    }

    public int evaluate(T x) {
        long h = hash.universalHash(x, 0);
        int b = Settings.reduce((int) h, bucketCount);
        int d = arrayList.get(b + 1);
        h = hash.universalHash(x, d);
        return Settings.reduce((int) h, targetLen);
    }

}
