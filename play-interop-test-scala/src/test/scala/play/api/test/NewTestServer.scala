/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

// TODO: Merge this functionality into TestServer itself
final case class NewTestServer(
  testServer: TestServer,
  endpoints: ServerEndpoints,
  stopServer: AutoCloseable)
