# Retry Logic (F22)

**Statut:** ✅ VALIDÉ ET ENRICHI (janvier 2026)

## Configuration Retry Standard

```typescript
const RETRY_CONFIG = {
  maxAttempts: 3,        // Total: 1 initial + 2 retries
  baseDelayMs: 100,      // Premier retry après 100ms
  multiplier: 4,         // Backoff: 100ms, 400ms
  jitterPercent: 20,     // ±20% randomisation
};

// Délais effectifs:
// Attempt 1: immédiat
// Attempt 2: 80-120ms (100ms ± 20%)
// Attempt 3: 320-480ms (400ms ± 20%)
```

## Calcul du Jitter

```typescript
function calculateDelay(attempt: number, config: typeof RETRY_CONFIG): number {
  const baseDelay = config.baseDelayMs * Math.pow(config.multiplier, attempt - 1);
  const jitter = 1 + (Math.random() - 0.5) * 2 * (config.jitterPercent / 100);
  return Math.round(baseDelay * jitter);
}
```

## Catégorisation des Erreurs

**⚠️ IMPORTANT:** Ne pas appliquer retry uniforme à toutes les erreurs. Catégoriser et traiter différemment:

| Type d'erreur | Retry? | Stratégie | Exemple |
|---------------|--------|-----------|---------|
| **Transitoire réseau** | ✅ Oui | Backoff exponentiel (3 tentatives) | Timeout, ECONNRESET |
| **Rate limiting** | ✅ Oui | Respecter Retry-After header | 429 Too Many Requests |
| **Ressource temporaire** | ✅ Oui | Court délai retry | Model busy |
| **Validation client** | ❌ Non | Échec immédiat | 400 Bad Request |
| **Authentification** | ❌ Non | Échec immédiat | 401/403 |
| **Out of Memory** | ❌ Non | Circuit breaker, reset | OOM errors |
| **Corruption données** | ❌ Non | Reset complet | Model corruption |

## Implémentation avec Catégorisation

```typescript
type ErrorCategory = 'transient' | 'rate_limit' | 'client' | 'fatal';

function categorizeError(error: unknown): ErrorCategory {
  if (error instanceof Error) {
    const message = error.message.toLowerCase();

    // Erreurs transitoires - retry avec backoff
    if (
      message.includes('timeout') ||
      message.includes('econnreset') ||
      message.includes('econnrefused') ||
      message.includes('temporary')
    ) {
      return 'transient';
    }

    // Rate limiting - retry avec délai
    if (message.includes('rate') || message.includes('429')) {
      return 'rate_limit';
    }

    // Erreurs fatales - pas de retry
    if (
      message.includes('oom') ||
      message.includes('out of memory') ||
      message.includes('corruption') ||
      message.includes('invalid')
    ) {
      return 'fatal';
    }
  }

  // Erreurs HTTP
  if (typeof error === 'object' && error !== null && 'status' in error) {
    const status = (error as { status: number }).status;
    if (status === 429) return 'rate_limit';
    if (status >= 400 && status < 500) return 'client';
    if (status >= 500) return 'transient';
  }

  return 'transient';  // Défaut: tenter retry
}

async function withRetry<T>(
  operation: () => Promise<T>,
  config = RETRY_CONFIG
): Promise<T> {
  let lastError: Error | undefined;

  for (let attempt = 1; attempt <= config.maxAttempts; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      const category = categorizeError(error);

      // Ne pas retry les erreurs non-transitoires
      if (category === 'client' || category === 'fatal') {
        throw lastError;
      }

      // Dernier attempt - throw
      if (attempt === config.maxAttempts) {
        throw lastError;
      }

      // Calculer délai
      const delay = category === 'rate_limit'
        ? extractRetryAfter(error) ?? calculateDelay(attempt, config)
        : calculateDelay(attempt, config);

      await sleep(delay);
    }
  }

  throw lastError;
}

function extractRetryAfter(error: unknown): number | undefined {
  if (
    typeof error === 'object' &&
    error !== null &&
    'headers' in error &&
    typeof (error as any).headers?.get === 'function'
  ) {
    const retryAfter = (error as any).headers.get('Retry-After');
    if (retryAfter) {
      const seconds = parseInt(retryAfter, 10);
      if (!isNaN(seconds)) return seconds * 1000;
    }
  }
  return undefined;
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
```

## Intégration Circuit Breaker

Pour les opérations d'inférence ML, combiner retry logic avec circuit breaker (voir F23):

```typescript
class RetryWithCircuitBreaker {
  private circuitBreaker: InferenceCircuitBreaker;
  private retryConfig: typeof RETRY_CONFIG;

  constructor(
    circuitBreaker: InferenceCircuitBreaker,
    retryConfig = RETRY_CONFIG
  ) {
    this.circuitBreaker = circuitBreaker;
    this.retryConfig = retryConfig;
  }

  async execute<T>(
    operation: () => Promise<T>,
    fallback: () => T
  ): Promise<T> {
    // Circuit breaker enveloppe retry logic
    return this.circuitBreaker.execute(
      () => withRetry(operation, this.retryConfig),
      fallback
    );
  }
}
```

## Timeouts Spécifiques

| Opération | Timeout | Notes |
|-----------|---------|-------|
| Model loading (cold) | 180s | Premier téléchargement ~100MB |
| Model loading (warm) | 60s | Cache local |
| Inference | 30s | Embedding single chunk |
| Database query | 5s | Connexion pool |
| Database transaction | 30s | Ingestion complète |
| Drain requests (shutdown) | 30s | Graceful shutdown |
| Close DB pool | 5s | Cleanup |

## Tests Recommandés

```typescript
describe('Retry Logic', () => {
  it('should retry transient errors with exponential backoff', async () => {
    let attempts = 0;
    const operation = async () => {
      attempts++;
      if (attempts < 3) throw new Error('ECONNRESET');
      return 'success';
    };

    const result = await withRetry(operation);
    expect(result).toBe('success');
    expect(attempts).toBe(3);
  });

  it('should not retry client errors', async () => {
    let attempts = 0;
    const operation = async () => {
      attempts++;
      throw Object.assign(new Error('Bad Request'), { status: 400 });
    };

    await expect(withRetry(operation)).rejects.toThrow('Bad Request');
    expect(attempts).toBe(1);
  });

  it('should respect Retry-After header', async () => {
    // Test implementation
  });

  it('should apply jitter within bounds', () => {
    const delays: number[] = [];
    for (let i = 0; i < 100; i++) {
      delays.push(calculateDelay(1, RETRY_CONFIG));
    }

    const min = Math.min(...delays);
    const max = Math.max(...delays);

    expect(min).toBeGreaterThanOrEqual(80);  // 100 - 20%
    expect(max).toBeLessThanOrEqual(120);    // 100 + 20%
  });
});
```
