package helper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.scala.ScalaPlugin

class ScalaWrapperPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply ScalaPlugin
    }
}
