scalaVersion := "2.11.7"

import com.typesafe.sbt.SbtScalariform.ScalariformKeys

val scalariformPrefs = {
  import java.util.Properties
  import scalariform.formatter.preferences._
  val props = new Properties()
  IO.load(props, file("scalariform.properties"))
  PreferencesImporterExporter.getPreferences(props)
}

lazy val commonSettings = Seq(
    version := "0.1.0",
    organization := "pl.caltha",
    scalaVersion := "2.11.7",
    ScalariformKeys.preferences := scalariformPrefs
)

val akkaVersion = "2.4.2"
val scalaTestVersion = "2.2.5"
val mocitoVersion = "1.10.19"

lazy val client = project.
    in(file("etcd-client")).
    settings(commonSettings ++ Seq(
        name := "etcd-client",
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-actor" % akkaVersion,
            "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
            "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
            "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
            "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
            "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
            "org.mockito" % "mockito-core" % mocitoVersion % "test"
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
            "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
            "org.mockito" % "mockito-core" % mocitoVersion % "test"
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

lazy val clusterMonitor = project.
    in(file("examples/cluster-monitor")).
    dependsOn(discovery).
    settings(commonSettings ++ Seq(
            name := "cluster-monitor",
            mainClass in Compile := Some("akka.Main"),
            javaOptions in Universal ++= Seq(
                "pl.caltha.akka.cluster.monitor.Main"
            ),
            dockerBaseImage := "java:8-jre",
            packageName in Docker := "caltha/akka-cluster-etcd/monitor",
            version in Docker := "latest",
            // frontend
            WebKeys.packagePrefix in Assets := "public/",
            (managedClasspath in Runtime) += (packageBin in Assets).value,
            (managedClasspath in Test) += (packageBin in Assets).value,
            libraryDependencies ++= Seq(
                "org.webjars" % "requirejs" % "2.1.20",
                "org.webjars" % "angularjs" % "1.4.8",
                "org.webjars" % "angular-material" % "1.0.0",
                "org.webjars.bower" % "roboto-fontface" % "0.4.3",
                "org.webjars.bower" % "material-design-iconic-font" % "2.1.1",
                "org.webjars" % "lodash" % "3.10.1"
            ),
            // pipelineStages := Seq(rjs, digest, gzip),
            RjsKeys.webJarCdns := Map.empty,
            RjsKeys.buildProfile := WebJs.JS.Object(
                "skipDirOptimize" -> true,
                "paths" -> WebJs.JS.Object(
                    "angular" -> "lib/angularjs/angular",
                    "angular-animate" -> "lib/angularjs/angular-animate",
                    "angular-aria" -> "lib/angularjs/angular-aria",
                    "angular-material" -> "lib/angular-material/angular-material",
                    "lodash" -> "lib/lodash/lodash"
                ),
                "shim" -> WebJs.JS.Object(
                    "angular" -> WebJs.JS.Object(
                        "exports" -> "angular"
                    ),
                    "angular-material" -> WebJs.JS.Object(
                        "deps" -> WebJs.JS.Array(
                            "angular",
                            "angular-animate",
                            "angular-aria"
                        )
                    ),
                    "angular-ui-router" -> WebJs.JS.Object(
                        "deps" -> WebJs.JS.Array("angular")
                    ),
                    "lodash" -> WebJs.JS.Object(
                        "exports" -> "_"
                    )
                )
            )
        ) ++ Revolver.settings ++ Seq(
            Revolver.reStartArgs := Seq("pl.caltha.akka.cluster.monitor.frontend.Static")
        )
    ).
    enablePlugins(JavaAppPackaging, DockerPlugin, SbtWeb)
