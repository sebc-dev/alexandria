---
title: Sample Documentation
category: testing
tags:
  - sample
  - test
---

# Sample Documentation

This is a sample markdown document for testing the ingestion pipeline.

## Introduction

The ingestion pipeline processes markdown files and extracts their content for semantic search.
It supports YAML frontmatter for metadata extraction.

## Features

- **Markdown Parsing**: Supports standard CommonMark syntax
- **Frontmatter Extraction**: Extracts title, category, and tags from YAML frontmatter
- **Hierarchical Chunking**: Splits content into parent and child chunks for optimal retrieval
- **Embedding Generation**: Creates 384-dimensional vectors for semantic similarity search

## Usage

To use the ingestion pipeline:

1. Place your markdown files in a directory
2. Call `ingestDirectory(path)` on the IngestionService
3. Files will be parsed, chunked, embedded, and stored in the database

## Conclusion

The ingestion pipeline provides a complete solution for indexing technical documentation.
