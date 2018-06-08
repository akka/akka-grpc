# gRPC Benchmarks

Benchmark test project that is using the same approach and proto messages as
[grpc-java](https://github.com/grpc/grpc-java/tree/master/benchmarks).

It is compatible with grpc-java and it's possible to test combinations of client and server from
grpc-java and akka-grpc.

## How to run

Server:

```
sbt  "runMain akka.grpc.benchmarks.qps.AsyncServer --address=localhost:50051"
```

Client with unary calls:

```
sbt "runMain akka.grpc.benchmarks.qps.AsyncClient --address=localhost:50051 --warmup_duration=15 --duration=30 --channels=1 --outstanding_rpcs=16"
```

Client with streaming calls:

```
sbt "runMain akka.grpc.benchmarks.qps.AsyncClient --address=localhost:50051 --warmup_duration=15 --duration=30 --channels=1 --outstanding_rpcs=16 --streaming_rpcs"
```

Use `--help` to show description of all options.

More scenarios can be tested with the `akka.grpc.benchmarks.driver.LoadWorker`. See `LoadWorkerTest`.

## Visualizing the Latency Distribution

The QPS client comes with the option `--save_histogram=FILE`, if set it serializes the histogram to `FILE` which
can then be used with a plotter to visualize the latency distribution. The histogram is stored in the file format
of [HdrHistogram](http://hdrhistogram.org/). That way it can be plotted using a browser based tool like
http://hdrhistogram.github.io/HdrHistogram/plotFiles.html. Upload the generated file and it will generate
a graph for you. It also allows you to plot two or more histograms on the same surface in order two compare latency
distributions.
