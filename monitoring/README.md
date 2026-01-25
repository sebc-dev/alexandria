# Monitoring Stack

Stack d'observabilité optionnelle pour Alexandria, activée via le profil `eval`.

## Démarrage

```bash
# Démarrer le monitoring stack
docker compose --profile eval up -d

# Vérifier les services
docker compose --profile eval ps

# Arrêter
docker compose --profile eval down
```

## Services

### VictoriaMetrics

**Rôle:** Stockage de métriques time-series, compatible Prometheus.

**Port:** 8428 (interne uniquement)

**Ce qu'il fait:**
- Scrape les métriques de l'application toutes les 15s via `/actuator/prometheus`
- Stocke les métriques avec rétention de 30 jours
- Fournit une API PromQL pour les requêtes Grafana

**Configuration:** `monitoring/victoriametrics/scrape.yaml`

**Utilisation directe (debug):**
```bash
# Requête PromQL depuis le container
docker exec alexandria-vm wget -qO- 'http://localhost:8428/api/v1/query?query=up'

# Voir les targets scrapés
docker exec alexandria-vm wget -qO- 'http://localhost:8428/targets'
```

---

### Grafana

**Rôle:** Visualisation des métriques et logs, dashboards, alertes.

**Port:** 3000 (exposé)

**Accès:** http://localhost:3000 (ou via Caddy: https://grafana.votre-domaine.fr)
- Login: `admin` / `admin`
- Accès anonyme en lecture activé

**Ce qu'il fait:**
- Affiche le dashboard "Alexandria RAG" avec les métriques clés
- Permet d'explorer les logs via Loki
- Envoie des alertes quand les seuils sont dépassés

**Dashboard Alexandria RAG:**
| Panel | Métrique | Description |
|-------|----------|-------------|
| Search Rate | `alexandria_search_total` | Requêtes de recherche par seconde |
| Search Latency P50/P95/P99 | `alexandria_search_duration_seconds` | Distribution des temps de réponse |
| Ingestion Count | `alexandria_ingestion_total` | Documents ingérés |
| Error Rate | `alexandria_errors_total` | Erreurs par seconde |
| Logs | Loki query | Logs applicatifs en temps réel |

**Alertes configurées:**
- **High Search Latency:** P95 > 2 secondes pendant 5 minutes
- **High Error Rate:** > 0.1 erreurs/seconde pendant 5 minutes

**Configuration:** `monitoring/grafana/provisioning/`

---

### Loki

**Rôle:** Agrégation et stockage des logs.

**Port:** 3100 (interne uniquement)

**Ce qu'il fait:**
- Reçoit les logs des containers via Alloy
- Indexe par labels (container, service, etc.)
- Permet les requêtes LogQL dans Grafana

**Configuration:** `monitoring/loki/loki-config.yaml`

**Requêtes LogQL utiles dans Grafana:**
```logql
# Tous les logs Alexandria
{container="alexandria-app"}

# Erreurs uniquement
{container="alexandria-app"} |= "ERROR"

# Logs de recherche
{container="alexandria-app"} |= "search"

# Logs avec latence > 1s
{container="alexandria-app"} | json | duration > 1s
```

---

### Alloy (Grafana Alloy)

**Rôle:** Collecteur de logs, remplace Promtail.

**Port:** 12345 (health check interne)

**Ce qu'il fait:**
- Découvre automatiquement les containers Docker
- Collecte les logs via le socket Docker
- Ajoute des labels (nom du container, image, etc.)
- Forward les logs vers Loki

**Configuration:** `monitoring/alloy/config.alloy`

**Debug:**
```bash
# Voir l'UI Alloy (si exposé)
# http://localhost:12345

# Vérifier les targets découverts
docker logs alexandria-alloy | grep -i "target"
```

---

## Architecture

```
┌─────────────┐     scrape      ┌──────────────────┐
│ Alexandria  │ ───────────────▶│  VictoriaMetrics │
│    App      │  /actuator/     │    (metrics)     │
└─────────────┘  prometheus     └────────┬─────────┘
       │                                 │
       │ logs                            │ PromQL
       ▼                                 ▼
┌─────────────┐              ┌──────────────────┐
│   Alloy     │ ────────────▶│     Grafana      │
│ (collector) │    Loki      │  (visualization) │
└─────────────┘              └──────────────────┘
       │                                 ▲
       │ push                            │ LogQL
       ▼                                 │
┌─────────────┐                          │
│    Loki     │ ─────────────────────────┘
│   (logs)    │
└─────────────┘
```

## Troubleshooting

### VictoriaMetrics ne scrape pas l'app

```bash
# Vérifier que l'app expose les métriques
curl http://localhost:8080/actuator/prometheus

# Vérifier les logs VM
docker logs alexandria-vm | grep -i error
```

### Pas de logs dans Grafana

```bash
# Vérifier qu'Alloy collecte
docker logs alexandria-alloy

# Vérifier que Loki reçoit
docker logs alexandria-loki | grep -i "ingested"
```

### Grafana ne démarre pas

```bash
# Vérifier les dépendances
docker compose --profile eval ps

# Les datasources sont-ils provisionnés?
docker logs alexandria-grafana | grep -i "provisioning"
```

## Ressources

- [VictoriaMetrics Docs](https://docs.victoriametrics.com/)
- [Grafana Docs](https://grafana.com/docs/grafana/latest/)
- [Loki Docs](https://grafana.com/docs/loki/latest/)
- [Alloy Docs](https://grafana.com/docs/alloy/latest/)
