 1. Infrastructure Self-Hosted ⚠️ CRITIQUE

  | Question                                                  | Status          | Impact       |
  |-----------------------------------------------------------|-----------------|--------------|
  | Comment Claude Code se connecte à un serveur MCP distant? | ❓ Non clarifié | Bloquant     |
  | Transport MCP: stdio (local) vs SSE/HTTP (remote)?        | ❓ Non décidé   | Architecture |
  | Reverse proxy (nginx/Caddy/Traefik) + SSL?                | ❓ Non décidé   | Sécurité     |
  | Docker Compose vs install directe?                        | ❓ Non décidé   | Déploiement  |
  | 4 cores / 8GB RAM suffisant pour PostgreSQL + MCP server? | ❓ Non validé   | Performance  |

  Recherche suggérée: "MCP server remote deployment over HTTPS" / "Claude Code connect to remote MCP server"

  ---
  2. RunPod/Infinity Deployment ⚠️ IMPORTANT

  | Question                                                                | Status                            | Impact          |
  |-------------------------------------------------------------------------|-----------------------------------|-----------------|
  | Image officielle runpod/worker-infinity-embedding:1.1.4 supporte Qwen3? | ❓ Non vérifié                    | Bloquant si non |
  | Ou custom Docker avec transformers>=4.51.0?                             | ❓ Non décidé                     | Complexité      |
  | 1 endpoint (Infinity multi-model) vs 2 endpoints séparés?               | ❓ Contradictoire dans recherches | Coût/latence    |
  | Network volume pour cache modèles vs baked in Docker?                   | ❓ Non décidé                     | Cold start      |

  Recherche suggérée: Vérifier si runpod/worker-infinity-embedding:1.1.4 inclut transformers>=4.51.0

  ---
  3. Stratégie de Chunking ⚠️ IMPORTANT

  | Question                                                                                         | Status          | Impact         |
  |--------------------------------------------------------------------------------------------------|-----------------|----------------|
  | Chunking algorithmique (RecursiveCharacterSplitter) ou LLM externe?                              | ❓ Non décidé   | Qualité        |
  | L'ancien spec mentionnait "chunking pré-calculé par LLM externe (Gemini CLI)" - toujours valide? | ❓ Non clarifié | Flow ingestion |
  | Qui fournit les chunks? User via JSON ou système automatique?                                    | ❓ Non décidé   | UX CLI         |
  | Comment extraire les headings pour metadata?                                                     | ❓ Non défini   | Implémentation |

  Recherche suggérée: Comparaison chunking LLM vs algorithmique pour doc technique

  ---
  4. Sécurité & Accès ⚠️ IMPORTANT

  | Question                                                      | Status        | Impact   |
  |---------------------------------------------------------------|---------------|----------|
  | Serveur exposé sur internet sans auth - acceptable?           | ❓ Non validé | Sécurité |
  | API key pour protéger les endpoints MCP?                      | ❓ Non défini | Sécurité |
  | Rate limiting?                                                | ❓ Non défini | Abus     |
  | Stockage sécurisé des secrets (RUNPOD_API_KEY, DATABASE_URL)? | ❓ Non défini | Sécurité |

  ---
  5. Resilience & Fallback 🟡 MOYEN

  | Question                                    | Status                      | Impact        |
  |---------------------------------------------|-----------------------------|---------------|
  | Que faire si RunPod est down?               | ❓ Non défini               | Disponibilité |
  | Fallback embedding-only si reranker échoue? | ✅ Mentionné dans recherche | -             |
  | Retry logic avec exponential backoff?       | ❓ Non spécifié             | Robustesse    |
  | Circuit breaker pattern?                    | ❓ Non défini               | Stabilité     |

  ---
  6. Cold Start UX 🟡 MOYEN

  | Question                                       | Status        | Impact  |
  |------------------------------------------------|---------------|---------|
  | 10-20s cold start acceptable pour Claude Code? | ❓ Assumé OK  | UX      |
  | Mécanisme de warm-up (ping périodique)?        | ❓ Non défini | Latence |
  | Feedback utilisateur pendant cold start?       | ❓ Non défini | UX      |

  ---
  7. Ops & Maintenance 🟡 MOYEN

  | Question                                            | Status        | Impact      |
  |-----------------------------------------------------|---------------|-------------|
  | Backup PostgreSQL strategy?                         | ❓ Non défini | Data safety |
  | Monitoring des coûts RunPod?                        | ❓ Non défini | Budget      |
  | Alerting (serveur down, erreurs)?                   | ❓ Non défini | Ops         |
  | Logs: où vont-ils? (stdout, file, service externe)? | ❓ Non défini | Debug       |

  ---
  8. Développement Local 🟡 MOYEN

  | Question                                   | Status        | Impact         |
  |--------------------------------------------|---------------|----------------|
  | Comment tester sans RunPod (mock server)?  | ❓ Non défini | Dev experience |
  | Docker Compose local avec Infinity?        | ❓ Non défini | Dev setup      |
  | Fallback local pour embeddings (CPU/ONNX)? | ❓ Non défini | Dev offline    |

  ---
  📋 Recherches Prioritaires Suggérées

  P0 - Bloquantes

  1. MCP Remote Access: Comment Claude Code se connecte à un MCP distant? (SSE? Tunnel? Proxy?)
  2. RunPod Infinity + Qwen3: L'image officielle supporte-t-elle Qwen3 ou faut-il custom?

  P1 - Importantes

  3. Chunking Strategy: LLM-based vs algorithmic - quelle approche pour ton use case?
  4. Self-hosted Security: Auth pattern pour MCP exposé sur internet?

  P2 - Nice to have

  5. Local Dev Setup: Mock Infinity ou Infinity local pour dev?
  6. Backup/Recovery: Stratégie PostgreSQL pour self-hosted?