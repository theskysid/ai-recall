package com.theskysid.echobackend.memory.entity;

/**
 * Lifecycle of a stored memory item.
 *
 *   CURRENT    — stands as written; retrieved normally
 *   SUPERSEDED — a later decision replaced it; demoted or excluded in retrieval
 *   UNRESOLVED — it conflicts with another item and neither side won; still
 *                valid information, so it is NOT demoted, only flagged
 *
 * UNRESOLVED exists so that a same-topic clash which is not a clean reversal
 * stops being silently filed as two unrelated active decisions.
 */
public enum MemoryStatus {
    CURRENT,
    SUPERSEDED,
    UNRESOLVED
}
