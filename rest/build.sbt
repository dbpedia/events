name := """DBpedia Events REST"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "University Leipzig, AKSW Maven2 Repository Internal" at "http://maven.aksw.org/repository/internal",
  "University Leipzig, AKSW Maven2 Repository Snapshots" at "http://maven.aksw.org/repository/snapshots"
)

val appDependencies = Seq(
  cache,
  javaWs,
  "joda-time" % "joda-time" % "2.9.3",
  "commons-configuration" % "commons-configuration" % "1.10",
  "org.aksw.jena-sparql-api" % "jena-sparql-api-core" % "2.12.1-6",
  "org.aksw.jena-sparql-api" % "jena-sparql-api-cache-h2" % "2.12.1-6"
)


lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .settings(
    libraryDependencies ++= appDependencies
  )

