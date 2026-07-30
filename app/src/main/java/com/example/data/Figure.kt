package com.example.data

/**
 * Task 27 (PRODUCT_PLAN.md) — a number that carries its own uncertainty instead of a
 * screen deciding ad hoc whether to trust it. `confident=false` renders with a leading
 * "≈" and is tappable; `reason` is the one-line explanation shown on tap. A figure that
 * is always confident is indistinguishable from a plain Double — this only earns its
 * keep where the underlying data genuinely can be incomplete (see call sites in
 * ZadViewModel.recalculateRemainingBalance).
 */
data class Figure(
    val value: Double,
    val confident: Boolean,
    val reason: String? = null
)
