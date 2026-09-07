package com.example.selfdisciplinepoc01.time

/**
 * Singleton holder for [BusinessDayProvider] adhering to 04:00 canonical boundary.
 */
object BusinessDayProviderHolder {
    @Volatile
    var instance: BusinessDayProvider = BusinessDayProviderImpl(
        boundaryHour = BusinessDayProviderImpl.CANONICAL_BOUNDARY_HOUR,
        boundaryMinute = BusinessDayProviderImpl.CANONICAL_BOUNDARY_MINUTE
    )
}
