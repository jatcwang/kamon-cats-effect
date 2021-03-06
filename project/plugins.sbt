addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.13")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
// Need to publish locally to include a fix jcmd detection is
//addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.8-SNAPSHOT")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.7")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.3")
addSbtPlugin("com.47deg" % "sbt-microsites" % "1.2.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5")
