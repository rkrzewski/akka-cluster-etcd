lazy val commonSettings = Seq(
    version := "0.1.0",
    organization := "pl.caltha",
    scalaVersion := "2.11.7"
)

val akkaVersion = "2.3.11"
val akkaStreamsVersion = "1.0"
val scalaTestVersion = "2.2.1"

lazy val client = project. 
    in(file("etcd-client")). 
    settings(commonSettings ++ Seq(
        name := "etcd-client",
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamsVersion,
            "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamsVersion,
            "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
        )
    ))

import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm    

lazy val discovery = project. 
    in(file("etcd-discovery")).
    dependsOn(client). 
    settings(commonSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
        name := "akka-cluster-discovery-etcd",
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-actor" % akkaVersion,
            "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
            "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test",
            "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
        ),
        // make sure that MultiJvm test are compiled by the default test compilation
	    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
	    // disable parallel tests
	    parallelExecution in Test := false,
	    // make sure that MultiJvm tests are executed by the default test target, 
	    // and combine the results from ordinary test and multi-jvm tests
	    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
	      case (testResults, multiNodeResults)  =>
	        val overall =
	          if (testResults.overall.id < multiNodeResults.overall.id)
	            multiNodeResults.overall
	          else
	            testResults.overall
	        Tests.Output(overall,
	          testResults.events ++ multiNodeResults.events,
	          testResults.summaries ++ multiNodeResults.summaries)
	    }
	)).
	configs(MultiJvm)
