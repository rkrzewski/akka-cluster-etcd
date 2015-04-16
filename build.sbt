lazy val commonSettings = Seq(
    version := "0.1.0",
    scalaVersion := "2.11.6"
)

lazy val client = project. 
    in(file("etcd-client")). 
    settings(commonSettings).
    settings(Seq(
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-http-experimental" % "1.0-M5",
            "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0-M5",
            "org.scalatest" %% "scalatest" % "2.2.1" % "test"
        )
    ))

lazy val discovery = project. 
    in(file("etcd-discovery")).
    dependsOn(client). 
    settings(commonSettings).
    settings(Seq(
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-actor" % "2.3.9"
        )
    ))
