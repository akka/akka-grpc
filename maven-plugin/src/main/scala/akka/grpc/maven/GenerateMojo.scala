/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import javax.inject.Inject
import org.sonatype.plexus.build.incremental.BuildContext

class GenerateMojo @Inject() (buildContext: BuildContext) extends AbstractGenerateMojo(buildContext) {
  override def addGeneratedSourceRoot(generatedSourcesDir: String): Unit = {
    project.addCompileSourceRoot(generatedSourcesDir)
  }
}
