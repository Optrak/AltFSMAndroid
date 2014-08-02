This project uses Akka Persistence on Android. But fails to serialize joda-time's DateTime.

The project uses
* https://github.com/romix/akka-kryo-serialization (compiled and published locally to get a version that uses Akka 2.3.4)
* https://github.com/magro/kryo-serializers

```scala
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.4",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.clapper" % "grizzled-slf4j_2.11" % "1.0.2",
  aar("org.macroid" %% "macroid" % "2.0.0-M3"),
  "com.android.support" % "support-v4" % "20.0.0",
  "org.scalaz" %% "scalaz-core" % "7.0.6",
  "com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "com.typesafe.akka" % "akka-persistence-experimental_2.11" % "2.3.4",
  "net.danlew" % "android.joda" % "2.3.4",
  //"joda-time" % "joda-time" % "2.4",
  "org.joda" % "joda-convert" % "1.6",
  "de.javakaffee" % "kryo-serializers" % "0.27",
  "com.github.romix.akka" % "akka-kryo-serialization_2.11" % "0.3.2b" // compiled locally to use Akka 2.3.4 and scala 2.11.2
)
```

Currently the app crashes on Akka System startup with an error caused by:
`java.lang.ClassNotFoundException: com.optrak.experiment.SettingTasksFSM.Task`
(That is a Scala case class in this project)

Current application.conf is in
src/main/resources/application.conf
I am including configuration as recommended by akka-persistence, akka-kryo-serialization and kryo-serializers
READMEs:

```
akka.extensions = ["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]
actor.actor.serializers {
  java = "akka.serialization.JavaSerializer"
  bytes = "akka.serialization.ByteArraySerializer"
  kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
}
akka.actor.serialization-bindings {
  "[B" = bytes
  "java.io.Serializable" = java
  "org.joda.time.DateTime" = kryo
  "com.optrak.experiment.SettingTasksFSM.Task" = kryo
}

akka.actor.kryo  {
  type = "graph"
  idstrategy = "incremental"
  serializer-pool-size = 16
  buffer-size = 4096
  use-manifests = false
  compression = off
  implicit-registration-logging = false
  kryo-trace = false

      kryo-reference-map = false

  kryo-custom-serializer-init = "com.optrak.androidExperiment.KryoInit"

      # Define mappings from a fully qualified class name to a numeric id.
      # Smaller ids lead to smaller sizes of serialized representations.
      #
      # This section is mandatory for idstartegy=explciit
      # This section is optional  for idstartegy=incremental
      # This section is ignored   for idstartegy=default
      #
      # The smallest possible id should start at 20 (or even higher), because
      # ids below it are used by Kryo internally e.g. for built-in Java and
      # Scala types
      mappings {
        "org.joda.time.DateTime" = 30
        "com.optrak.experiment.SettingTasksFSM.Task" = 31
      }

      # Define a set of fully qualified class names for
      # classes to be used for serialization.
      # The ids for those classes will be assigned automatically,
      # but respecting the order of declaration in this section
      #
      # This section is optional  for idstartegy=incremental
      # This section is ignored   for idstartegy=default
      # This section is optional  for idstartegy=explicit
      classes = [ 
        "org.joda.time.DateTime",
        "com.optrak.experiment.SettingTasksFSM.Task"     <----- I am not sure what to put in this configuration!
      ]
}
```

(Proguard chokes with repeated `reference.conf` files from several Akka's packages, like the ones from
akka-actor and akka-persistence, among others, therefore I am including most of the contents of the
reference.conf from akka-actor and akka-persistence source repos.)

I am already including this in my build.sbt
```
proguardOptions in Android ++= Seq(
  "-keep class com.romix.akka.serialization.kryo.** { *; }",
  "-keep class com.optrak.androidExperiment.** { *; }",
  "-keep class com.optrak.experiment.** { *; }",
  "-keep class com.optrak.experiment.SettingTasksFSM.Task { *; }"
)
```

The error says:
Caused by: java.lang.ClassNotFoundException: com.optrak.experiment.SettingTasksFSM.Task

I am not sure if name mangling is tricking me. For example, 
what if I should be using something like
`com.optrak.experiment.SettingTasksFSM$.Task$`

somewhere (in the application.conf? in the proguardOptions?)

Please, some help!

