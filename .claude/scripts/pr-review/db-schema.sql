-- PR Review SQLite Schema
-- Location: ~/.local/share/alexandria/pr-reviews.db

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

-- Core PR table
CREATE TABLE IF NOT EXISTS prs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pr_number INTEGER NOT NULL,
    repo TEXT NOT NULL DEFAULT 'sebc-dev/alexandria',
    title TEXT,
    body TEXT,
    branch TEXT,
    base_branch TEXT DEFAULT 'main',
    author TEXT,
    state TEXT CHECK(state IN ('open', 'closed', 'merged')),
    created_at TEXT,
    updated_at TEXT,
    last_synced_at TEXT,
    review_status TEXT CHECK(review_status IN ('pending', 'in_progress', 'completed', 'abandoned')) DEFAULT 'pending',
    UNIQUE(repo, pr_number)
);

-- Comments from ALL users (not just CodeRabbit)
CREATE TABLE IF NOT EXISTS comments (
    id TEXT PRIMARY KEY,                    -- GitHub comment ID (string for review_body synthetic IDs)
    pr_id INTEGER NOT NULL REFERENCES prs(id) ON DELETE CASCADE,
    github_url TEXT,
    node_id TEXT,                           -- GitHub GraphQL node_id (for resolving threads)
    source TEXT CHECK(source IN ('inline', 'review_body', 'issue_comment')) NOT NULL,
    review_id TEXT,                         -- Parent review ID if from review_body
    user_login TEXT NOT NULL,               -- coderabbitai[bot], human reviewer, etc.
    user_type TEXT,                         -- Bot, User, etc.
    is_bot INTEGER DEFAULT 0,               -- 1 if from a bot
    file_path TEXT,
    line_number INTEGER,
    side TEXT,                              -- LEFT or RIGHT (for diff comments)
    diff_hunk TEXT,
    body TEXT NOT NULL,
    in_reply_to_id TEXT,                    -- For threaded replies
    created_at TEXT,
    updated_at TEXT,
    -- CodeRabbit-specific parsed fields
    cr_severity TEXT,                       -- critical, major, minor, trivial (from emoji/text parsing)
    cr_category TEXT,                       -- potential-issue, nitpick, suggestion, etc.
    cr_type TEXT,                           -- security, bug, performance, style, best-practice, documentation
    cr_has_suggestion INTEGER DEFAULT 0,    -- Has committable suggestion block
    cr_addressed_sha TEXT,                  -- Commit SHA if addressed by CodeRabbit
    cr_addressed_at TEXT,                   -- When CodeRabbit marked as addressed
    -- Sync tracking
    fetched_at TEXT,
    raw_json TEXT                           -- Original JSON for debugging
);

-- Analysis results (Claude decisions)
CREATE TABLE IF NOT EXISTS analyses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comment_id TEXT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    decision TEXT CHECK(decision IN ('ACCEPT', 'REJECT', 'DISCUSS', 'DEFER', 'DUPLICATE', 'SKIP')) NOT NULL,
    confidence REAL CHECK(confidence BETWEEN 0.0 AND 1.0),
    type TEXT,                              -- bug, security, performance, style, best-practice, documentation
    criticality TEXT CHECK(criticality IN ('BLOCKING', 'IMPORTANT', 'MINOR', 'COSMETIC')),
    effort TEXT CHECK(effort IN ('TRIVIAL', 'LOW', 'MEDIUM', 'HIGH')),
    regression_risk INTEGER DEFAULT 0,
    summary TEXT,
    rationale TEXT,
    code_suggestion TEXT,
    research_needed TEXT,
    duplicate_of TEXT,                      -- Reference to original comment if DUPLICATE
    analyzed_at TEXT,
    analyzer_model TEXT DEFAULT 'sonnet',
    -- Correction tracking
    applied_at TEXT,                        -- When correction was applied (for ACCEPT)
    -- Thread resolution tracking
    resolved_at TEXT,                       -- When thread was resolved on GitHub
    resolved_by TEXT,                       -- Who resolved it (user or 'auto')
    UNIQUE(comment_id)
);

-- Our replies posted to GitHub
CREATE TABLE IF NOT EXISTS replies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comment_id TEXT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    github_reply_id TEXT,                   -- NULL until posted
    body TEXT NOT NULL,
    template_type TEXT,                     -- accept, reject, defer, discuss, duplicate
    status TEXT CHECK(status IN ('draft', 'pending', 'posted', 'failed')) DEFAULT 'draft',
    posted_at TEXT,
    error_message TEXT,
    dry_run INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now'))
);

-- Deferred items for backlog tracking
CREATE TABLE IF NOT EXISTS deferred_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comment_id TEXT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    title TEXT,
    description TEXT,
    action_required TEXT,
    target_phase TEXT,
    -- Backlog analysis results
    backlog_status TEXT CHECK(backlog_status IN ('needs_analysis', 'already_planned', 'tracked_beads', 'tracked_github', 'needs_issue')),
    found_in_type TEXT,                     -- phase_doc, beads_issue, github_issue, none
    found_in_reference TEXT,                -- Path or issue ID
    found_in_excerpt TEXT,                  -- Relevant text from reference
    -- Resolution tracking
    resolution_status TEXT CHECK(resolution_status IN ('pending', 'issue_created', 'resolved', 'wont_fix')),
    github_issue_number INTEGER,
    github_issue_url TEXT,
    beads_issue_id TEXT,
    resolved_at TEXT,
    analyzed_at TEXT,
    UNIQUE(comment_id)
);

-- GitHub workflow runs
CREATE TABLE IF NOT EXISTS workflow_runs (
    id INTEGER PRIMARY KEY,                 -- GitHub run ID
    pr_id INTEGER NOT NULL REFERENCES prs(id) ON DELETE CASCADE,
    workflow_name TEXT,
    workflow_id INTEGER,
    head_sha TEXT,
    head_branch TEXT,
    event TEXT,                             -- pull_request, push, etc.
    status TEXT,                            -- queued, in_progress, completed
    conclusion TEXT,                        -- success, failure, cancelled, skipped, neutral
    run_number INTEGER,
    run_attempt INTEGER,
    started_at TEXT,
    completed_at TEXT,
    html_url TEXT,
    fetched_at TEXT
);

-- Check runs (more granular than workflow runs)
CREATE TABLE IF NOT EXISTS check_runs (
    id INTEGER PRIMARY KEY,                 -- GitHub check run ID
    pr_id INTEGER NOT NULL REFERENCES prs(id) ON DELETE CASCADE,
    workflow_run_id INTEGER REFERENCES workflow_runs(id) ON DELETE SET NULL,
    name TEXT,
    head_sha TEXT,
    status TEXT,                            -- queued, in_progress, completed
    conclusion TEXT,                        -- success, failure, cancelled, skipped, neutral, action_required
    details_url TEXT,
    output_title TEXT,
    output_summary TEXT,
    output_text TEXT,
    annotations_count INTEGER DEFAULT 0,
    started_at TEXT,
    completed_at TEXT,
    fetched_at TEXT
);

-- Event log for audit trail
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pr_id INTEGER REFERENCES prs(id) ON DELETE SET NULL,
    comment_id TEXT REFERENCES comments(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,               -- fetch, analyze, reply, sync, error, init
    event_subtype TEXT,                     -- More specific: fetch_pr, fetch_comments, analyze_comment, etc.
    event_data TEXT,                        -- JSON blob with details
    error_message TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);

-- Schema version for migrations
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT DEFAULT (datetime('now')),
    description TEXT
);

-- Insert initial version
INSERT OR IGNORE INTO schema_version (version, description) VALUES (1, 'Initial schema');

-- Version 2: Add node_id for GraphQL thread resolution
-- Note: Migration handled by db-init.sh for existing databases
INSERT OR IGNORE INTO schema_version (version, description) VALUES (2, 'Add node_id for GraphQL thread resolution');

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_comments_pr ON comments(pr_id);
CREATE INDEX IF NOT EXISTS idx_comments_user ON comments(user_login);
CREATE INDEX IF NOT EXISTS idx_comments_source ON comments(source);
CREATE INDEX IF NOT EXISTS idx_comments_file ON comments(file_path);
CREATE INDEX IF NOT EXISTS idx_analyses_decision ON analyses(decision);
CREATE INDEX IF NOT EXISTS idx_analyses_comment ON analyses(comment_id);
CREATE INDEX IF NOT EXISTS idx_replies_status ON replies(status);
CREATE INDEX IF NOT EXISTS idx_replies_comment ON replies(comment_id);
CREATE INDEX IF NOT EXISTS idx_events_pr ON events(pr_id);
CREATE INDEX IF NOT EXISTS idx_events_type ON events(event_type);
CREATE INDEX IF NOT EXISTS idx_workflow_runs_pr ON workflow_runs(pr_id);
CREATE INDEX IF NOT EXISTS idx_check_runs_pr ON check_runs(pr_id);
CREATE INDEX IF NOT EXISTS idx_deferred_status ON deferred_items(backlog_status);

-- Views for common queries

-- Pending comments (no analysis yet)
CREATE VIEW IF NOT EXISTS v_pending_comments AS
SELECT c.*, p.pr_number, p.repo
FROM comments c
JOIN prs p ON c.pr_id = p.id
LEFT JOIN analyses a ON c.id = a.comment_id
WHERE a.id IS NULL;

-- Comments with analysis
CREATE VIEW IF NOT EXISTS v_analyzed_comments AS
SELECT c.*, a.decision, a.confidence, a.criticality, a.effort, a.summary, a.rationale,
       p.pr_number, p.repo
FROM comments c
JOIN prs p ON c.pr_id = p.id
JOIN analyses a ON c.id = a.comment_id;

-- Pending replies (drafted but not posted)
CREATE VIEW IF NOT EXISTS v_pending_replies AS
SELECT r.*, c.file_path, c.line_number, c.user_login, a.decision,
       p.pr_number, p.repo
FROM replies r
JOIN comments c ON r.comment_id = c.id
JOIN prs p ON c.pr_id = p.id
LEFT JOIN analyses a ON c.id = a.comment_id
WHERE r.status IN ('draft', 'pending');

-- PR summary stats
CREATE VIEW IF NOT EXISTS v_pr_summary AS
SELECT
    p.id, p.pr_number, p.repo, p.title, p.state, p.review_status,
    COUNT(DISTINCT c.id) as total_comments,
    COUNT(DISTINCT CASE WHEN c.is_bot = 1 THEN c.id END) as bot_comments,
    COUNT(DISTINCT CASE WHEN c.user_login LIKE '%coderabbit%' THEN c.id END) as coderabbit_comments,
    COUNT(DISTINCT a.comment_id) as analyzed_comments,
    COUNT(DISTINCT CASE WHEN a.decision = 'ACCEPT' THEN a.comment_id END) as accepted,
    COUNT(DISTINCT CASE WHEN a.decision = 'REJECT' THEN a.comment_id END) as rejected,
    COUNT(DISTINCT CASE WHEN a.decision = 'DISCUSS' THEN a.comment_id END) as discussed,
    COUNT(DISTINCT CASE WHEN a.decision = 'DEFER' THEN a.comment_id END) as deferred,
    COUNT(DISTINCT CASE WHEN r.status = 'posted' THEN r.comment_id END) as replied,
    p.last_synced_at
FROM prs p
LEFT JOIN comments c ON c.pr_id = p.id
LEFT JOIN analyses a ON a.comment_id = c.id
LEFT JOIN replies r ON r.comment_id = c.id
GROUP BY p.id;
