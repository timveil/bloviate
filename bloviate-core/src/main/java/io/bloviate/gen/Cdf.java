/*
 * Copyright (c) 2021 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.gen;

/**
 * Shared inverse-transform sampling over a cumulative-weight (CDF) array, used by the weighted
 * distribution generators ({@link WeightedCategoricalGenerator}, {@link ZipfianIntegerGenerator}).
 */
final class Cdf {

    private Cdf() {
    }

    /**
     * Binary search for the index of the first cumulative-weight bucket strictly greater than
     * {@code draw}. The array must be non-empty and non-decreasing, and {@code draw} is expected in
     * {@code [0, cumulative[last])}.
     *
     * @param cumulative the non-decreasing cumulative-weight array
     * @param draw       the sampled value in the total-weight range
     * @return the index of the selected bucket
     */
    static int upperBound(double[] cumulative, double draw) {
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (draw < cumulative[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }
}
