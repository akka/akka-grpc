package unit

import akka.grpc.gradle.AkkaGrpcPlugin
import spock.lang.Specification
import spock.lang.Unroll

class BaselineVersionSpec extends Specification {

    @Unroll
    def "should resolve akka-grpc artifacts for plugin version #pluginVersion with #expected"() {
        expect:
        new AkkaGrpcPlugin().getBaselineVersion(pluginVersion) == expected
        where:
        pluginVersion       || expected
        "2.5.12"            || "2.5.12"
        "2.5"               || "2.5"
        "2.5.12-5-77746b54" || "2.5.12-5-77746b54-SNAPSHOT"
        "2.6.0-M1"          || "2.6.0-M1-SNAPSHOT"
    }
}
