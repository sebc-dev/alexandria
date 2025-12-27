/**
 * Application-wide constants
 * Naming convention: UPPER_SNAKE_CASE
 */

// Vector Search Configuration
export const DEFAULT_TOP_K = 5
export const EMBEDDING_DIMENSIONS = 1536 // OpenAI text-embedding-3-small
export const MAX_QUERY_LENGTH = 8000 // Characters

// Embedding Model
export const EMBEDDING_MODEL = 'text-embedding-3-small'

// Database
export const MAX_POOL_SIZE = 10
export const CONNECTION_TIMEOUT_MS = 5000

// Logging
export const LOG_FORMAT = 'json'
export const LOG_TIMESTAMP_FORMAT = 'ISO8601'
