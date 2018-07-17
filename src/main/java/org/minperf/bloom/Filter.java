package org.minperf.bloom;

import org.minperf.bloom.gcs.GolombCompressedSet;
import org.minperf.bloom.gcs.GolombRiceCompressedSet;
import org.minperf.bloom.mphf.MPHFilter;

public interface Filter {

    /**
     * Whether the set may contain the key.
     *
     * @param key the key
     * @return true if the set might contain the key, and false if not
     */
    boolean mayContain(long key);

    /**
     * An alternative implementation.
     *
     * @param key the key
     * @return -1 if the method isn't implemented, 0 for false, >0 for true
     */
    default int mayContainAlternative(long key) {
        return -1;
    }

    long getBitCount();

    public enum Type {
        XOR8 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return XorFilter_8bit.construct(keys);
            }
        },
        CUCKOO8_4 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return CuckooFilter_8bit_4entries.construct(keys);
            }
        },
        BLOOM {
            @Override
            public Filter construct(long[] keys, int setting) {
                return BloomFilter.construct(keys, setting);
            }
        },
        XOR16 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return XorFilter_16bit.construct(keys);
            }
        },
        CUCKOO16_4 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return CuckooFilter_16bit_4entries.construct(keys);
            }
        },
        XOR {
            @Override
            public Filter construct(long[] keys, int bitsPerKey) {
                return XorFilter.construct(keys, bitsPerKey);
            }
        },
        CUCKOO {
            @Override
            public Filter construct(long[] keys, int setting) {
                return CuckooFilter.construct(keys, setting);
            }
        },
        MPHF {
            @Override
            public Filter construct(long[] keys, int setting) {
                return MPHFilter.construct(keys, setting);
            }
        },
        GRCS {
            @Override
            public Filter construct(long[] keys, int setting) {
                return GolombRiceCompressedSet.construct(keys, setting);
            }
        },
        GCS {
            @Override
            public Filter construct(long[] keys, int setting) {
                return GolombCompressedSet.construct(keys, setting);
            }
        };

        /**
         * Construct the filter with the given keys and the setting.
         *
         * @param keys the keys
         * @param setting the setting (roughly bits per fingerprint)
         * @return the constructed filter
         */
        public abstract Filter construct(long[] keys, int setting);

    }

}
