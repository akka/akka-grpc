package func

import helper.BaseSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class GradleCompatibilitySpec extends BaseSpec {

    BuildResult executeGradleTaskWithVersion(String task, String gradleVersion, boolean shouldFail) {
        def runner = GradleRunner.create().forwardOutput()
            .withProjectDir(projectDir.root)
            .withArguments("--stacktrace", task)
            .withPluginClasspath()
            .withDebug(true)
            .withGradleVersion(gradleVersion)

        if (shouldFail) {
            return runner.buildAndFail()
        } else {
            return runner.build()
        }
    }

    @Unroll
    void 'should succeed for version #gradleVersion greater than 5.6'() {
        given:
        createBuildFolder()
        generateBuildScripts()
        when:
        BuildResult result = executeGradleTaskWithVersion('tasks', gradleVersion, false)
        then:
        result.task(":tasks").outcome == TaskOutcome.SUCCESS
        where:
        gradleVersion << ["5.6", "5.6.4", "6.4.1"]
    }

    @Unroll
    void 'should fail for version #gradleVersion less than 5.6'() {
        given:
        createBuildFolder()
        generateBuildScripts()
        when:
        BuildResult result = executeGradleTaskWithVersion('tasks', gradleVersion, true)
        then:
        result.output.contains("Gradle version is ${gradleVersion}. Minimum supported version is 5.6")
        where:
        gradleVersion << ["5.5", "4.0"]
    }
}
