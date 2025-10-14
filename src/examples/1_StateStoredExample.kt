package com.fraktalio.examples

import com.fraktalio.StateStoredSystem
import com.fraktalio.asStateStoredSystem


fun main() {
    println("=== Counter System - State Stored scenario ===\n")

    // Observe the API of the State Stored System! No Events, only Command(s) and State(s)
    val system: StateStoredSystem<CounterCommand?, CounterState> = counterSystemCombined.asStateStoredSystem()

    // ----------------------- Increment & Decrement Counter Demo -----------------------
    var incState = counterSystemCombined.initialState()
    println("Counter Initial State: $incState")

    val incCommands: List<CounterCommand?> = listOf(
        IncrementCounterCommand.Increment(1),
        IncrementCounterCommand.Increment(1),
        IncrementCounterCommand.Increment(1),
        DecrementCounterCommand.Decrement(1),
        null,
        DecrementCounterCommand.Decrement(1),
    )

    for (cmd in incCommands) {
        incState = system.invoke(cmd, incState)
        println("After Increment Command $cmd -> State: $incState")
    }
}