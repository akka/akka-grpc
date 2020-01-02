/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package io.grpc.testing.integration2

import io.grpc.testing.integration.TestCases

final case class Settings(
    serverHost: String,
    serverHostOverride: String,
    serverPort: Int,
    testCase: String,
    useTls: Boolean,
    useTestCa: Boolean,
    useAkkaHttp: Boolean,
    defaultServiceAccount: String,
    serviceAccountKeyFile: String,
    oauthScope: String) {
  // some getters for access from java
  def getTestCase = testCase
  def getDefaultServiceAccount = defaultServiceAccount
  def getOauthScope: String = oauthScope
  def getServiceAccountKeyFile: String = serviceAccountKeyFile
}

object Settings {
  def parseArgs(args: Array[String]): Settings = {
    val defaultSettings = Settings(
      serverHost = "127.0.0.1",
      serverHostOverride = null,
      serverPort = 8080,
      testCase = "empty_unary",
      useTls = true,
      useTestCa = false,
      useAkkaHttp = false,
      defaultServiceAccount = null,
      serviceAccountKeyFile = null,
      oauthScope = null)

    def showUsageAndExit() = {
      val validTestCasesHelpText = {
        val builder = new StringBuilder
        for (testCase <- TestCases.values) {
          val strTestcase = testCase.name.toLowerCase
          builder.append("\n      ").append(strTestcase).append(": ").append(testCase.description)
        }
        builder.toString
      }

      println(s"""
             | Usage: [ARGS...]
             |   --server_host=HOST          Server to connect to. Default ${defaultSettings.serverHost}
             |   --server_host_override=HOST Claimed identification expected of server.
             |                               Defaults to server host
             |   --server_port=PORT          Port to connect to. Default ${defaultSettings.serverPort}
             |   --test_case=TESTCASE        Test case to run. Default ${defaultSettings.testCase}
             |     Valid options: $validTestCasesHelpText
             |   --use_tls=true|false        Whether to use TLS. Default ${defaultSettings.useTls}
             |   --use_test_ca=true|false    Whether to trust our fake CA. Requires --use_tls=true
             |                               to have effect. Default ${defaultSettings.useTestCa}
             |   --use_akkaHttp=true|false   Whether to use akka-http instead of Netty. Default ${defaultSettings.useAkkaHttp}
             |   --default_service_account   Email of GCE default service account. Default ${defaultSettings.defaultServiceAccount}
             |   --service_account_key_file  Path to service account json key file. ${defaultSettings.serviceAccountKeyFile}
             |   --oauth_scope               Scope for OAuth tokens. Default ${defaultSettings.oauthScope}
           """.stripMargin)
      System.exit(-1)
    }

    def extractKeyValue(arg: String) = {
      if (!arg.startsWith("--")) {
        System.err.println("All arguments must start with '--': " + arg)
        showUsageAndExit()
      }

      val parts = arg.substring(2).split("=", 2)
      val key = parts(0)
      if ("help" == key) {
        showUsageAndExit()
      }

      if (parts.length != 2) {
        System.err.println("All arguments must be of the form --arg=value")
        showUsageAndExit()
      }
      val value = parts(1)
      (key, value)
    }

    args.foldLeft(defaultSettings) { (settings, arg) =>
      val (key, value) = extractKeyValue(arg)

      key match {
        case "server_host"              => settings.copy(serverHost = value)
        case "server_host_override"     => settings.copy(serverHostOverride = value)
        case "server_port"              => settings.copy(serverPort = value.toInt)
        case "test_case"                => settings.copy(testCase = value)
        case "use_tls"                  => settings.copy(useTls = value.toBoolean)
        case "use_test_ca"              => settings.copy(useTestCa = value.toBoolean)
        case "use_akkaHttp"             => settings.copy(useAkkaHttp = value.toBoolean)
        case "default_service_account"  => settings.copy(defaultServiceAccount = value)
        case "service_account_key_file" => settings.copy(serviceAccountKeyFile = value)
        case "oauth_scope"              => settings.copy(oauthScope = value)
        case _ =>
          System.err.println("Unknown argument: " + key)
          showUsageAndExit()
          settings // not really returning it because exiting, we need it for compile check
      }
    }
  }
}
