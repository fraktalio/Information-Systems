package com.fraktalio.examples

import com.fraktalio.EventSourcedSystem
import com.fraktalio.asEventSourcedSystem

fun main() {
    println("=== Counter System - Event Sourced scenario ===\n")

    // Observe the API of the Event Sourced System! No State, only Command(s) and Event(s)
    val system: EventSourcedSystem<CounterCommand?, CounterEvent?> =
        counterSystemCombined.asEventSourcedSystem()

    // Start with an empty event log
    var allEvents: List<CounterEvent?> = emptyList()
    println("Initial Event Log: $allEvents\n")

    val commands: List<CounterCommand?> = listOf(
        IncrementCounterCommand.Increment(1),
        IncrementCounterCommand.Increment(1),
        IncrementCounterCommand.Increment(1),
        DecrementCounterCommand.Decrement(1),
        null,
        DecrementCounterCommand.Decrement(1),
    )

    for (cmd in commands) {
        // Generate new events from the current command and full event history
        val newEvents = system(cmd, allEvents.asSequence()).toList()

        // Append new events to the history log
        allEvents += newEvents

        // Print only the emitted events
        println("Command $cmd -> Emitted Events: $newEvents")
    }

    println("\n=== Final Event Log ===")
    println(allEvents)
}
