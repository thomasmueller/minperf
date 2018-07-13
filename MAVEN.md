Please ensure that unit tests actually pass.

```
   mvn clean install -DskipTests
   java -cp target/minperf-1.0-SNAPSHOT-jar-with-dependencies.jar org.minperf.bloom.JmhBench
```
