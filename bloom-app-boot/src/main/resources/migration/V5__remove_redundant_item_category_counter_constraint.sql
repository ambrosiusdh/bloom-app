-- A counter has exactly one row per item category. The single-column uniqueness
-- constraint is the conflict target used by the atomic sequence allocator.
-- The composite constraint is redundant and can race with that conflict target
-- during concurrent first allocation for a category.
ALTER TABLE item_category_counters
    DROP CONSTRAINT uq_item_category_counters_category_sequence;
