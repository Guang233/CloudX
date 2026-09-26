package com.guang.cloudx.logic.model

import java.math.BigInteger

/** Numeric version ordering; a local Git hash is a build identifier, not a prerelease. */
class AppVersion private constructor(
    private val numbers: List<BigInteger>,
    private val prerelease: List<String>,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val difference =
                (numbers.getOrNull(i) ?: BigInteger.ZERO)
                    .compareTo(other.numbers.getOrNull(i) ?: BigInteger.ZERO)
            if (difference != 0) return difference
        }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[i]
            val right = other.prerelease[i]
            val leftNumber = left.toBigIntegerOrNull()
            val rightNumber = right.toBigIntegerOrNull()
            val difference =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> left.compareTo(right)
                }
            if (difference != 0) return difference
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern =
            Regex("^[vV]?(\\d+(?:\\.\\d+){1,3})(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        private val gitHash = Regex("[0-9a-fA-F]{7,40}")

        fun parse(
            value: String,
            localBuild: Boolean = false,
        ): AppVersion? {
            if (value.length > 256) return null
            val match = pattern.matchEntire(value.trim()) ?: return null
            val suffix = match.groupValues[2]
            val isBuildSuffix = localBuild && (gitHash.matches(suffix) || suffix == "unknown")
            return AppVersion(
                numbers = match.groupValues[1].split('.').map { it.toBigInteger() },
                prerelease = if (suffix.isEmpty() || isBuildSuffix) emptyList() else suffix.split('.'),
            )
        }
    }
}
