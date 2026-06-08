/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import javax.inject.Inject
import org.apache.maven.repository.RepositorySystem
import org.sonatype.plexus.build.incremental.BuildContext

class GenerateMojo @Inject() (buildContext: BuildContext, repositorySystem: RepositorySystem)
    extends AbstractGenerateMojo(buildContext, repositorySystem) {
  override def addGeneratedSourceRoot(generatedSourcesDir: String): Unit = {
    project.addCompileSourceRoot(generatedSourcesDir)
  }
}
