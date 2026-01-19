/**
 * Infrastructure layer: External implementations and integrations.
 *
 * <p>This layer implements interfaces defined in the core layer and handles
 * all external communication - databases, APIs, file systems, etc.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>Repository implementations (PostgreSQL, pgvector, Apache AGE)</li>
 *   <li>Embedding model clients (LangChain4j AllMiniLmL6V2)</li>
 *   <li>External API clients</li>
 *   <li>Configuration classes for external services</li>
 * </ul>
 *
 * <p>Dependencies flow inward: infra depends on core, never the reverse.</p>
 */
package fr.kalifazzia.alexandria.infra;
