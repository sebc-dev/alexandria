-- Add version column to sources table for documentation version tagging.
-- Version is nullable: not all sources have explicit version labels.

ALTER TABLE sources ADD COLUMN version TEXT;
