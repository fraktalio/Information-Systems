package com.fraktalio.examples

import com.fraktalio.*

/**
 * Specification tests for [counterSystem] and [counterSystemCombined].
 *
 * For simplicity, run via `main()` without additional test framework dependencies.
 */
fun main() {
    // ## Event-Sourced: counterSystem

    counterSystem.givenEvents(
        emptyList(),
        { whenCommand(IncrementCounterCommand.Increment(5)) }
    ) thenEvents listOf(IncrementCounterEvent.Incremented(5, 5))

    counterSystem.givenEvents(
        listOf(IncrementCounterEvent.Incremented(3, 3)),
        { whenCommand(IncrementCounterCommand.Increment(2)) }
    ) thenEvents listOf(IncrementCounterEvent.Incremented(2, 5))

    counterSystem.givenEvents(
        emptyList(),
        { whenCommand(DecrementCounterCommand.Decrement(4)) }
    ) thenEvents listOf(DecrementCounterEvent.Decremented(4, 4))

    counterSystem.givenEvents(
        emptyList(),
        { whenCommand(null) }
    ) thenEvents emptyList()

    // ## State-Stored: counterSystem

    counterSystem.givenState(
        CounterState(0, 0),
        { whenCommand(IncrementCounterCommand.Increment(5)) }
    ) thenState CounterState(incValue = 5, decValue = 0)

    counterSystem.givenState(
        CounterState(10, 3),
        { whenCommand(DecrementCounterCommand.Decrement(2)) }
    ) thenState CounterState(incValue = 10, decValue = 5)

    // ## Event-Sourced: counterSystemCombined

    counterSystemCombined.givenEvents(
        emptyList(),
        { whenCommand(IncrementCounterCommand.Increment(7)) }
    ) thenEvents listOf(IncrementCounterEvent.Incremented(7, 7))

    counterSystemCombined.givenEvents(
        listOf(DecrementCounterEvent.Decremented(3, 3)),
        { whenCommand(DecrementCounterCommand.Decrement(2)) }
    ) thenEvents listOf(DecrementCounterEvent.Decremented(2, 5))

    // ## State-Stored: counterSystemCombined

    counterSystemCombined.givenState(
        CounterState(5, 2),
        { whenCommand(IncrementCounterCommand.Increment(3)) }
    ) thenState CounterState(incValue = 8, decValue = 2)

    println("All tests passed!")
}
