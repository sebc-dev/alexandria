# Health Check State Machine (F23)

**Statut:** ✅ VALIDÉ ET ENRICHI (janvier 2026)

## Machine d'états XState v5

XState v5 avec pattern `fromPromise` est recommandé pour les opérations async de 30-60 secondes. Avantages clés: gestion correcte du cycle de vie des Promises, annulation automatique à la sortie d'état, retry logic native avec guards.

```
                    ┌─────────────┐
        startup     │   initial   │
           ┌───────>│  (no model) │
           │        └──────┬──────┘
           │               │ LOAD event
           │               v
           │        ┌─────────────┐
           │        │   loading   │──── timeout ────> error
           │        │  (invoke)   │     (180s cold/60s warm)
           │        └──────┬──────┘
           │               │ onDone
           │               v
           │        ┌─────────────┐
           │        │   loaded    │<─── RETRY (guard) ─┐
           │        │   (final)   │                    │
           │        └──────┬──────┘                    │
           │               │ inference error           │
           │               v                           │
           │        ┌─────────────┐                    │
           └────────│    error    │────────────────────┘
             RESET  │  (circuit)  │
                    └─────────────┘
```

## Transitions

| From | To | Trigger | Condition |
|------|-----|---------|-----------|
| initial | loading | `LOAD` event | Premier appel embed() |
| loading | loaded | `onDone` (invoke) | Pipeline créé avec succès |
| loading | error | `onError` / timeout | Timeout ou exception |
| loaded | error | Erreur d'inférence | OOM, corruption, etc. |
| error | loading | `RETRY` event | `retryCount < 3` (guard) |
| error | initial | `RESET` event | Reset complet du circuit |

## Configuration Timeouts - Two-Tier Strategy

**⚠️ CORRECTION CRITIQUE:** 60 secondes est insuffisant pour cold start.

| Scénario | Temps estimé | Timeout approprié |
|----------|--------------|-------------------|
| Warm cache (modèle local) | 1-5 secondes | 60s ✅ |
| Fast network (100+ Mbps) + init | 15-25 secondes | 60s ✅ |
| Average network (25 Mbps) + init | 40-60 secondes | ⚠️ Borderline |
| Slow network (10 Mbps) + init | 100+ secondes | ❌ Échec avec 60s |
| ONNX initialization only (cached) | 10-15 secondes | 60s ✅ |

**Configuration recommandée:**

```typescript
const COLD_START_TIMEOUT = 180_000;  // 3 minutes pour premier téléchargement
const WARM_CACHE_TIMEOUT = 60_000;   // 1 minute pour modèle en cache

const timeout = modelCacheExists() ? WARM_CACHE_TIMEOUT : COLD_START_TIMEOUT;
```

**Note:** Issue connue dans @huggingface/transformers v3.0.0-alpha.5 causait re-téléchargement à chaque run. Toujours configurer `env.cacheDir` explicitement.

## Implémentation XState v5

```typescript
import { setup, fromPromise, assign } from 'xstate';

const modelLoaderMachine = setup({
  types: {
    context: {} as {
      model: any;
      error: unknown;
      retryCount: number;
      loadedAt?: string;
    },
    events: {} as
      | { type: 'LOAD' }
      | { type: 'RETRY' }
      | { type: 'RESET' },
  },
  actors: {
    loadModel: fromPromise<any, void>(async ({ signal }) => {
      // AbortSignal pour cleanup si l'état change pendant le chargement
      return await pipeline('feature-extraction', 'Xenova/multilingual-e5-small', {
        revision: 'main',
        dtype: 'q8',  // Quantifié pour efficacité mémoire
      });
    }),
  },
  guards: {
    canRetry: ({ context }) => context.retryCount < 3,
  },
}).createMachine({
  id: 'modelLoader',
  initial: 'initial',
  context: {
    model: undefined,
    error: undefined,
    retryCount: 0,
    loadedAt: undefined,
  },
  states: {
    initial: {
      on: { LOAD: 'loading' }
    },
    loading: {
      invoke: {
        src: 'loadModel',
        onDone: {
          target: 'loaded',
          actions: assign({
            model: ({ event }) => event.output,
            loadedAt: () => new Date().toISOString(),
            retryCount: 0,  // Reset on success
          })
        },
        onError: {
          target: 'error',
          actions: assign({
            error: ({ event }) => event.error,
            retryCount: ({ context }) => context.retryCount + 1,
          })
        },
      },
    },
    loaded: {
      type: 'final',
    },
    error: {
      on: {
        RETRY: {
          target: 'loading',
          guard: 'canRetry',
        },
        RESET: {
          target: 'initial',
          actions: assign({
            retryCount: 0,
            error: undefined,
            model: undefined,
          }),
        },
      },
    },
  },
});
```

**⚠️ Anti-patterns à éviter:**

- Ne jamais utiliser async actions pour le chargement de modèle (fire-and-forget)
- Toujours inclure `onError` sur les invokes
- Tracker `retryCount` pour éviter les boucles infinies

## Three-Probe Health Check Strategy

Pattern Kubernetes pour services avec temps de warm-up:

### 1. Liveness Probe (`/health/live`)

Retourne 200 immédiatement si le serveur HTTP répond. Ne bloque JAMAIS sur le chargement du modèle.

```typescript
app.get('/health/live', (req, res) => {
  res.status(200).json({ status: 'alive', timestamp: new Date().toISOString() });
});
```

### 2. Readiness Probe (`/health/ready`)

Retourne 200 uniquement quand le modèle est chargé et l'inférence possible. Retourne 503 pendant l'initialisation.

```typescript
interface HealthResponse {
  status: 'loading' | 'ready' | 'error';
  timestamp: string;
  progress?: { stage: string; percent?: number };
  model?: { name: string; loadedAt: string };
  error?: string;
}

app.get('/health/ready', (req, res) => {
  const state = machineSnapshot.value;

  if (state === 'loaded') {
    res.status(200).json({
      status: 'ready',
      timestamp: new Date().toISOString(),
      model: {
        name: 'multilingual-e5-small',
        loadedAt: machineSnapshot.context.loadedAt,
      },
    });
  } else if (state === 'loading') {
    res.status(503).json({
      status: 'loading',
      timestamp: new Date().toISOString(),
      progress: { stage: 'initializing_onnx' },
    });
  } else {
    res.status(503).json({
      status: 'error',
      timestamp: new Date().toISOString(),
      error: machineSnapshot.context.error?.message,
    });
  }
});
```

### 3. Startup Probe (`/health/startup`)

Protège contre les redémarrages prématurés pendant le chargement de 30-180 secondes. Essentiel pour ce use case.

**Configuration Kubernetes recommandée:**

```yaml
startupProbe:
  httpGet:
    path: /health/startup
    port: 3000
  periodSeconds: 30
  failureThreshold: 12  # 6 minutes max startup time

livenessProbe:
  httpGet:
    path: /health/live
    port: 3000
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /health/ready
    port: 3000
  periodSeconds: 5
  failureThreshold: 1
```

## Circuit Breaker pour Error Recovery

**Ne pas utiliser retry automatique pour toutes les erreurs.** Catégoriser et traiter différemment:

| Type d'erreur | Action | Retry auto? |
|---------------|--------|-------------|
| Network timeout | Backoff exponentiel | ✅ Oui (3 tentatives) |
| Model busy/temporary | Court délai retry | ✅ Oui |
| Out of Memory | Recreation de session | ❌ Non (reset manuel) |
| Model corruption | Reset complet vers initial | ❌ Non |

**Circuit breaker pattern:**

- S'ouvre après **5 échecs consécutifs** en 60 secondes
- Entre en état half-open après 30 secondes pour probe de recovery

```typescript
class InferenceCircuitBreaker {
  private state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';
  private failureCount = 0;
  private lastFailure = 0;
  private readonly failureThreshold = 5;
  private readonly failureWindow = 60_000;  // 60 seconds
  private readonly resetTimeout = 30_000;   // 30 seconds

  async execute<T>(
    operation: () => Promise<T>,
    fallback: () => T
  ): Promise<T> {
    if (this.state === 'OPEN') {
      if (Date.now() - this.lastFailure > this.resetTimeout) {
        this.state = 'HALF_OPEN';
      } else {
        return fallback();
      }
    }

    try {
      const result = await operation();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  private onSuccess(): void {
    this.failureCount = 0;
    this.state = 'CLOSED';
  }

  private onFailure(): void {
    this.failureCount++;
    this.lastFailure = Date.now();

    if (this.failureCount >= this.failureThreshold) {
      this.state = 'OPEN';
    }
  }
}
```

## Gestion Mémoire et Tensors

**CRITIQUE pour OOM recovery:** ONNX Runtime a une fuite mémoire documentée lors de création/release répétée de sessions (Issue #25325: 325MB → 9.12GB après 100 cycles).

**Règles:**

1. **Toujours réutiliser les sessions** plutôt que recréer
2. **Recreation atomique:** créer la nouvelle session AVANT de disposer l'ancienne
3. **Disposal explicite de tous les tensors:**

```typescript
async function getEmbedding(
  session: ort.InferenceSession,
  text: string
): Promise<number[]> {
  const inputTensor = new ort.Tensor('float32', prepareInput(text), [1, tokenLength]);

  try {
    const results = await session.run({ input: inputTensor });
    const embeddings = Array.from(results.embeddings.data as Float32Array);

    // CRITIQUE: Disposer tous les tensors de sortie
    for (const key in results) {
      results[key].dispose();
    }
    return embeddings;
  } finally {
    inputTensor.dispose();  // Toujours disposer les inputs
  }
}
```

4. **Monitoring mémoire:** Utiliser `process.memoryUsage()` et trigger recreation de session si heap > **85% de la mémoire disponible**

## Compatibilité Bun

**⚠️ Issues connues affectant le stack:**

| Issue | Impact | Solution |
|-------|--------|----------|
| Bus error ARM64 macOS (Bun #3574) | Crash au démarrage sur Darwin ARM64 | Utiliser onnxruntime-web (WASM) |
| NAPI hot reload crashes | Corrigé dans **Bun 1.3.5** (décembre 2025) | Minimum Bun 1.3.5 |
| Windows file path encoding (Bun 1.2.5) | Chemins modèle corrompus | WSL ou Bun 1.2.4 |

**Configuration session recommandée pour stabilité:**

```typescript
const sessionOptions: ort.InferenceSession.SessionOptions = {
  enableCpuMemArena: false,     // Réduit sévérité fuite mémoire
  enableMemPattern: false,       // Gestion mémoire supplémentaire
  graphOptimizationLevel: 'all',
  executionMode: 'sequential',
  intraOpNumThreads: 1,          // Limite threads pour stabilité
};
```

**Alternative production:** Pour déploiements critiques, considérer:

- Inférence ONNX dans **Node.js** plutôt que Bun
- **onnxruntime-web avec WebAssembly** comme alternative portable sans modules natifs

## Checklist d'Implémentation

- [ ] **State machine**: XState v5 avec `fromPromise` actors
- [ ] **Timeouts**: 180s cold start / 60s warm cache, `env.cacheDir` configuré
- [ ] **Health checks**: Three-endpoint pattern (live/ready/startup), 503 pendant loading
- [ ] **Error recovery**: Circuit breaker (5 échecs/60s), backoff exponentiel pour erreurs transitoires
- [ ] **Mémoire**: Disposal explicite tensors, réutilisation sessions, seuil 85% heap
- [ ] **Bun version**: Minimum 1.3.5 pour stabilité NAPI
- [ ] **@huggingface/transformers**: v3.x stable, `dtype: 'q8'`, `env.cacheDir` explicite
