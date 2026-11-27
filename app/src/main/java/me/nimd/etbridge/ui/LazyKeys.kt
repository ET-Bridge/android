package me.nimd.etbridge.ui

/**
 * Pairs each element with a LazyList key that is unique within the list.
 *
 * ETLab ids are not unique: `/surveys`, for example, returns one row per subject/teacher that all
 * share the survey's id, and a duplicate key crashes LazyColumn/LazyRow. Repeats get a suffix
 * ("s-3", "s-3#2", …); the first occurrence keeps its plain key so item animations stay stable.
 */
inline fun <T> List<T>.keyedUniquely(key: (T) -> String): List<Pair<String, T>> {
    val seen = HashMap<String, Int>(size)
    return map { item ->
        val k = key(item)
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        (if (n == 1) k else "$k#$n") to item
    }
}
