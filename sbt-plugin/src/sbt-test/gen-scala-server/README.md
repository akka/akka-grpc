Scripted test projects can be loaded externally by starting the `sbt` with the following parameter:

```
sbt -Dproject.version=$(git describe --always --abbrev=8)
```
