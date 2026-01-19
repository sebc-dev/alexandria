/**
 * API layer: Entry points for external consumers.
 *
 * <p>This layer handles incoming requests and translates them to core service calls.
 * It manages input validation, request/response transformation, and protocol handling.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>MCP (Model Context Protocol) handlers for Claude Code integration</li>
 *   <li>REST controllers for HTTP API access</li>
 *   <li>CLI command handlers for manual operations</li>
 *   <li>Request/Response DTOs</li>
 * </ul>
 *
 * <p>The API layer depends on core for business logic but not on infra directly.</p>
 */
package fr.kalifazzia.alexandria.api;
