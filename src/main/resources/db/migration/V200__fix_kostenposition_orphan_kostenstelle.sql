-- Fix: Remove orphaned kostenposition rows where kostenstelle_id
-- references IDs that don't exist in miete_kostenstelle.
-- This allows Hibernate to add the FK constraint on schema update.

DELETE FROM kostenposition
WHERE kostenstelle_id IS NOT NULL
  AND kostenstelle_id NOT IN (SELECT id FROM miete_kostenstelle);
