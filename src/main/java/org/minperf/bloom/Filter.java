package org.minperf.bloom;

public interface Filter {

    boolean mayContain(long key);
    long getBitCount();

    enum Type {
        BLOOM {
            @Override
            Filter construct(long[] keys, int bitsPerKey) {
                return BloomFilter.construct(keys, bitsPerKey);
            }
        },
        XOR {
            @Override
            Filter construct(long[] keys, int bitsPerKey) {
                return XorFilter.construct(keys, bitsPerKey);
            }
        },
        CUCKOO {
            @Override
            Filter construct(long[] keys, int setting) {
                return CuckooFilter.construct(keys, setting);
            }
        },
        MPHF {
            @Override
            Filter construct(long[] keys, int setting) {
                return MPHFilter.construct(keys, setting);
            }
        };

        /**
         * Construct the filter with the given keys and the setting.
         *
         * @param keys the keys
         * @param setting the setting (roughly bits per fingerprint)
         * @return the constructed filter
         */
        abstract Filter construct(long[] keys, int setting);

    }

}
