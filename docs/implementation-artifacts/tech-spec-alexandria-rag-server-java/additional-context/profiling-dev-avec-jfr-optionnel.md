# Profiling Dev avec JFR (Optionnel)

Java Flight Recorder (JFR) est intégré au JDK 25 - zéro dépendance, overhead <1%, activable à la demande.

**Activation via profil Spring (recommandé):**

```yaml
# application-dev.yml
spring:
  jfr:
    enabled: true  # Spring Boot 3.4+ expose des événements JFR custom
```

**Activation via JVM args (docker-compose ou IDE):**

```bash
# Profiling continu léger (overhead ~1%)
java -XX:StartFlightRecording=filename=alexandria.jfr,dumponexit=true,settings=profile \
     -jar alexandria.jar

# Profiling à la demande (démarrage différé)
java -XX:+FlightRecorder -jar alexandria.jar
# Puis: jcmd <pid> JFR.start name=debug settings=profile duration=60s filename=debug.jfr
```

**Configuration JFR custom pour RAG (optionnel):**

```
# jfr-alexandria.jfc - Activer uniquement les événements pertinents
<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <!-- HTTP/Network - latence Infinity -->
  <event name="jdk.SocketRead"><setting name="enabled">true</setting><setting name="threshold">1 ms</setting></event>
  <event name="jdk.SocketWrite"><setting name="enabled">true</setting><setting name="threshold">1 ms</setting></event>

  <!-- Virtual Threads -->
  <event name="jdk.VirtualThreadStart"><setting name="enabled">true</setting></event>
  <event name="jdk.VirtualThreadEnd"><setting name="enabled">true</setting></event>
  <event name="jdk.VirtualThreadPinned"><setting name="enabled">true</setting><setting name="threshold">20 ms</setting></event>

  <!-- JDBC/PostgreSQL -->
  <event name="jdk.JavaMonitorWait"><setting name="enabled">true</setting><setting name="threshold">10 ms</setting></event>

  <!-- GC (utile pour batch ingestion) -->
  <event name="jdk.GCPhasePause"><setting name="enabled">true</setting></event>
  <event name="jdk.GarbageCollection"><setting name="enabled">true</setting></event>
</configuration>
```

**Analyse des enregistrements:**

```bash
# JDK Mission Control (GUI)
jmc alexandria.jfr

# CLI - dump des événements
jfr print --events jdk.VirtualThreadPinned alexandria.jfr

# CLI - résumé
jfr summary alexandria.jfr
```

**Intérêt pour Alexandria:**
- Détecter les **Virtual Thread pinning** (JEP 491 les élimine mais vérification utile)
- Profiler la **latence réseau** vers Infinity (embedding + reranking)
- Identifier les **pauses GC** pendant l'ingestion de gros batches
- Zero overhead quand désactivé - pas de code à maintenir
