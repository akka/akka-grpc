Scripted test projects can be loaded externally by starting the `sbt` with the following parameter:

```
sbt -Dproject.version=`git describe --tags | sed -e "s/v\(.*\)-\([0-9][0-9]*\).*/\\1-\\2-/"``git rev-parse HEAD | head -c8`
```

You will have to have the akka-grpc project modules published locally for this to work. All of the required modules are automatically published manually whenever you run `scripted` task from the main build. You can also publish by running `publishLocal`. 
