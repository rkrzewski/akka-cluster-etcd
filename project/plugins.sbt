addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.4")

resolvers += "Typesafe Bintray repository" at "http://dl.bintray.com/typesafe/maven-releases/"
resolvers += Resolver.url("shaggyyeti.github.io repository", url("http://shaggyyeti.github.io/releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.2")
addSbtPlugin("default" % "sbt-sass" % "0.1.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.7")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
