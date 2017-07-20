package org.minperf.chd;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.monotoneList.EliasFanoMonotoneList;
import org.minperf.universal.UniversalHash;

public class CHD<T> {

    final UniversalHash<T> hash;
    final int lambda;
    final BitBuffer buff;
    final double factor;
    int size;
    EliasFanoList list;
    int bucketCount;
    int targetLen;
    EliasFanoMonotoneList holes;
    // long hashCalls;

    CHD(UniversalHash<T> hash, BitBuffer buff) {
        this(hash, buff, 6, 1.0);
    }

    CHD(UniversalHash<T> hash, BitBuffer buff, int lambda, double factor) {
        this.hash = hash;
        this.buff = buff;
        this.lambda = lambda;
        this.factor = factor;
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
		int targetLen = (int) (size * factor);
		BitSet mapped = new BitSet(targetLen);
		for (T x : set) {
			long h = hash.universalHash(x, 0);
			// hashCalls++;
			int b = Settings.reduce((int) h, bucketCount);
			buckets.get(b).list.add(x);
		}
		Collections.sort(buckets, new Comparator<Bucket>() {

			@Override
			public int compare(Bucket o1, Bucket o2) {
				return Integer.compare(o2.list.size(), o1.list.size());
			}

		});
		int[] list = new int[bucketCount + 1];
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
					// hashCalls++;
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
					list[bucket.id + 1] = d;
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
		EliasFanoList.generate(list, buff);
		if (targetLen > size) {
		    // TODO for guaranteed constant evaluate,
		    // we would need a sparse rank or similar
		    int[] holes = new int[targetLen - size];
            for (int i = 0, j = 0; i < targetLen; i++) {
                if (!mapped.get(i)) {
                    holes[j++] = i;
                }
            }
            long pos = buff.position();
            EliasFanoMonotoneList.generate(holes, buff);
            pos = buff.position() - pos;
		}
	}

    public void load() {
        size = (int) buff.readEliasDelta() - 1;
        bucketCount = (size + lambda - 1) / lambda;
        list = EliasFanoList.load(buff);
        targetLen = (int) (size * factor);
        if (targetLen > size) {
            holes = EliasFanoMonotoneList.load(buff);
        }
    }

    public int evaluate(T x) {
        long h = hash.universalHash(x, 0);
        int b = Settings.reduce((int) h, bucketCount);
        int d = list.get(b + 1);
        h = hash.universalHash(x, d);
        int result = Settings.reduce((int) h, targetLen);
        if (targetLen > size) {
            result -= holesBelow(result);
        }
        return result;
    }

    private int holesBelow(int x) {
        int low = 0, high = targetLen - size - 1;
        while(low <= high) {
            int mid = (low + high) >>> 1;
            int y = holes.get(mid);
            if (y == x) {
                return mid + 1;
            } else if (y < x) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

}
