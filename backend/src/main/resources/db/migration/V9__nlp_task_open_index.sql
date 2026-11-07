CREATE INDEX IF NOT EXISTS idx_nlp_task_open
    ON nlp_task (id)
    WHERE status IN ('NEW', 'IN_PROGRESS');