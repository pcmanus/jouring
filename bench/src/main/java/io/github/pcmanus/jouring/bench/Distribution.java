package io.github.pcmanus.jouring.bench;

import java.util.function.Function;
import java.util.stream.Stream;

enum Distribution {
    RANDOM(ReadTaskGenerators::uniform),
    ZIPF(ReadTaskGenerators::zipf);

    final Function<Benchmark.Parameters, Stream<ReadTask>> generator;

    Distribution(Function<Benchmark.Parameters, Stream<ReadTask>> generator) {
        this.generator = generator;
    }

}
