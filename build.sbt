lazy val commonSettings = Seq(
    version := "0.1.0",
    scalaVersion := "2.11.6"
)

val akkaVersion = "2.3.10"
val akkaStreamsVersion = "1.0-RC1"
val scalaTestVersion = "2.2.1"

lazy val client = project. 
    in(file("etcd-client")). 
    settings(commonSettings).
    settings(Seq(
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-http-scala-experimental" % akkaStreamsVersion,
            "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamsVersion,
            "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
        )
    ))

lazy val discovery = project. 
    in(file("etcd-discovery")).
    dependsOn(client). 
    settings(commonSettings).
    settings(Seq(
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-actor" % akkaVersion
        )
    ))
