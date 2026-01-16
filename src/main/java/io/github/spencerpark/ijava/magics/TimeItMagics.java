/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 ${author}
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class TimeItMagics {
    private final int epochs = 3;
    private final int loops = 5;

    @CellMagic(aliases = { "time", "timeit" })
    public void timeIt(List<String> args, String body) throws Exception {
        if (args == null)
            args = Collections.emptyList();

        if (!args.isEmpty() && ("-h".equals(args.get(0)) || "--help".equals(args.get(0)))) {
            System.out.println("help: \nexample: \n");
            System.out.println("%%time epochs=3 loops=5\n1 + 1");
            return;
        }

        // parse input args like epochs=3 loops=5 warmup=1 iterations=10
        Map<String, Integer> params = args.stream()
                .map(arg -> arg.split("="))
                .filter(kv -> kv.length > 1 && StringUtils.isNotEmpty(kv[0]) && StringUtils.isNotEmpty(kv[1])
                        && kv[1].matches("\\d+"))
                .collect(Collectors.toMap(kv -> kv[0], kv -> Integer.parseInt(kv[1])));

        int warmup = params.getOrDefault("warmup", 1);
        int iterations = params.getOrDefault("iterations", 5);

        List<Long> samples = new ArrayList<>(iterations);

        for (int w = 0; w < warmup; w++) {
            IJava.getKernelInstance().evalRaw(body);
        }

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            IJava.getKernelInstance().evalRaw(body);
            long end = System.nanoTime();
            samples.add(end - start);
        }

        // compute statistics
        long min = samples.stream().mapToLong(Long::longValue).min().orElse(0L);
        long max = samples.stream().mapToLong(Long::longValue).max().orElse(0L);
        double avg = samples.stream().mapToLong(Long::longValue).average().orElse(0.0);
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        long median = sorted.get(sorted.size() / 2);

        System.out.printf("samples: %s\n", samples);
        System.out.printf("min=%d median=%d avg=%.2f max=%d (nanoseconds)\n", min, median, avg, max);
    }
}
