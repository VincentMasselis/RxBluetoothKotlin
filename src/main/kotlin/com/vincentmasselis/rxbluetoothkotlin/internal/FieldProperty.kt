package com.vincentmasselis.rxbluetoothkotlin.internal

import kotlin.reflect.KProperty

//Copied from : https://github.com/h0tk3y/kotlin-fun/blob/master/src/main/kotlin/com/github/h0tk3y/kotlinFun/FieldProperty.kt

/**
 * Provides property delegation which behaves as if each [R] instance had a backing field of type [T] for that property.
 * Delegation can be defined at top level or inside a class, which will mean that the delegation is scoped to
 * instances of the class -- separate instances will see separate values of the delegated property.
 *
 * This implementation is not thread-safe. Use [SynchronizedFieldProperty] for thread-safe delegation.
 *
 * This delegate does not allow `null` values, use [NullableFieldProperty] for a nullable equivalent.
 *
 * If the delegated property of an [R] instance is accessed but has not been initialized, [initializer] is called to
 * provide the initial value. The default [initializer] throws [IllegalStateException].
 */
class FieldProperty<R, T : Any>(val initializer: R.() -> T = { throw IllegalStateException("Not initialized.") }) {
    private val map = WeakIdentityHashMap<R, T>()

    operator fun getValue(thisRef: R, property: KProperty<*>): T =
        map[thisRef] ?: setValue(thisRef, property, initializer(thisRef))

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T): T {
        map[thisRef] = value
        return value
    }
}

/**
 * Provides property delegation which behaves as if each [R] instance had a backing field of type [T] for that property.
 * Delegation can be defined at top level or inside a class, which will mean that the delegation is scoped to
 * instances of the class -- separate instances will see separate values of the delegated property.
 *
 * This implementation is not thread-safe. Use [SynchronizedNullableFieldProperty] for thread-safe delegation.
 *
 * This delegate allows `null` values.
 *
 * If the delegated property of an [R] instance is accessed but has not been initialized, [initializer] is called to
 * provide the initial value. The default [initializer] returns `null`.
 */
class NullableFieldProperty<R, T>(val initializer: R.() -> T? = { null }) {
    private val map = WeakIdentityHashMap<R, T>()

    operator fun getValue(thisRef: R, property: KProperty<*>): T? =
        if (thisRef in map) map[thisRef] else setValue(thisRef, property, initializer(thisRef))

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T?): T? {
        map[thisRef] = value
        return value
    }
}

/**
 * Provides property delegation which behaves as if each [R] instance had a backing field of type [T] for that property.
 * Delegation can be defined at top level or inside a class, which will mean that the delegation is scoped to
 * instances of the class -- separate instances will see separate values of the delegated property.
 *
 * This implementation is thread-safe.
 *
 * This delegate does not allow `null` values, use [SynchronizedNullableFieldProperty] for a nullable equivalent.
 *
 * If the delegated property of an [R] instance is accessed but has not been initialized, [initializer] is called to
 * provide the initial value. The default [initializer] throws [IllegalStateException].
 */
class SynchronizedFieldProperty<R, T : Any>(val initializer: R.() -> T = { throw IllegalStateException("Not initialized.") }) {
    private val map = WeakIdentityHashMap<R, T>()

    operator fun getValue(thisRef: R, property: KProperty<*>): T = synchronized(map) {
        map[thisRef] ?: setValue(thisRef, property, initializer(thisRef))
    }

    private fun set(thisRef: R, value: T): T {
        map[thisRef] = value
        return value
    }

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T): T = synchronized(map) { set(thisRef, value) }
}

/**
 * Provides property delegation which behaves as if each [R] instance had a backing field of type [T] for that property.
 * Delegation can be defined at top level or inside a class, which will mean that the delegation is scoped to
 * instances of the class -- separate instances will see separate values of the delegated property.
 *
 * This implementation is thread-safe.
 *
 * This delegate allows `null` values.
 *
 * If the delegated property of an [R] instance is accessed but has not been initialized, [initializer] is called to
 * provide the initial value. The default [initializer] returns `null`.
 */
class SynchronizedNullableFieldProperty<R, T>(val initializer: R.() -> T? = { null }) {
    private val map = WeakIdentityHashMap<R, T>()

    operator fun getValue(thisRef: R, property: KProperty<*>): T? = synchronized(map) {
        if (thisRef in map) map[thisRef] else set(thisRef, initializer(thisRef))
    }

    private fun set(thisRef: R, value: T?): T? {
        map[thisRef] = value
        return value
    }

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T?): T? = synchronized(map) { set(thisRef, value) }
}