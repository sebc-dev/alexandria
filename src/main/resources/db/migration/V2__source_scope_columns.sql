-- Add scope configuration columns to sources table for URL filtering during crawl.
-- allow_patterns / block_patterns: comma-separated glob patterns (e.g. "/docs/**,/api/**")
-- max_depth: NULL = unlimited depth
-- max_pages: safety limit, default 500
-- llms_txt_url: optional manual override for llms.txt location

ALTER TABLE sources ADD COLUMN allow_patterns TEXT;
ALTER TABLE sources ADD COLUMN block_patterns TEXT;
ALTER TABLE sources ADD COLUMN max_depth INTEGER;
ALTER TABLE sources ADD COLUMN max_pages INTEGER DEFAULT 500;
ALTER TABLE sources ADD COLUMN llms_txt_url TEXT;
