# Akka gRPC Microbenchmarks

This subproject contains some microbenchmarks parts of Akka gRPC.

You can run them on the `sbt` prompt like:

   project benchmarks
   jmh:run -i 3 -wi 3 -f 1 .*HandlerProcessingBenchmark

Use 'jmh:run -h' to get an overview of the available options.