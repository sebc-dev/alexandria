--liquibase formatted sql

--changeset alexandria:001-extensions
--comment: Enable pgvector and Apache AGE extensions, create graph

-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable Apache AGE extension for graph operations
CREATE EXTENSION IF NOT EXISTS age;

-- Load AGE into current session
-- Note: This is required for each connection, but we do it here for initial setup
LOAD 'age';

-- Set search path to include ag_catalog for graph operations
SET search_path = ag_catalog, "$user", public;

-- Create the main graph for document relationships
SELECT create_graph('alexandria');

--rollback SELECT drop_graph('alexandria', true);
--rollback DROP EXTENSION IF EXISTS age;
--rollback DROP EXTENSION IF EXISTS vector;
