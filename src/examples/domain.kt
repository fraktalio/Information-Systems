package com.fraktalio.examples

import com.fraktalio.System
import com.fraktalio.combine
import com.fraktalio.mapState

// ### Counter System ###
val counterSystem =
    System<CounterCommand?, CounterState, CounterEvent?>(
        decide = { cmd, state ->
            when (cmd) {
                is IncrementCounterCommand.Increment -> sequenceOf(IncrementCounterEvent.Incremented(cmd.value, state.incValue + cmd.value))
                is DecrementCounterCommand.Decrement -> sequenceOf(DecrementCounterEvent.Decremented(cmd.value, state.decValue + cmd.value))
                null -> emptySequence()
            }
        },
        evolve = { state, event ->
            when (event) {
                is IncrementCounterEvent.Incremented -> CounterState(event.newIncValue, state.decValue)
                is DecrementCounterEvent.Decremented -> CounterState(state.incValue, event.newDecValue)
                null -> state
            }
        },
        initialState = { CounterState(0) }
    )


// ###############################################################################################
// ################################# Divide & Conquer ############################################
// ###############################################################################################

// ### Increment Counter SubSystem ###
private val incrementCounterSubSystem =
    System<IncrementCounterCommand?, IncrementCounterState, IncrementCounterEvent?>(
        decide = { cmd, state ->
            when (cmd) {
                is IncrementCounterCommand.Increment -> sequenceOf(IncrementCounterEvent.Incremented(cmd.value, state.value + cmd.value))
                null -> emptySequence()
            }
        },
        evolve = { state, event ->
            when (event) {
                is IncrementCounterEvent.Incremented -> IncrementCounterState(event.newIncValue)
                null -> state
            }
        },
        initialState = { IncrementCounterState(0) }
    )


// ### Decrement Counter SubSystem ###
private val decrementCounterSubSystem =
    System<DecrementCounterCommand?, DecrementCounterState, DecrementCounterEvent?>(
        decide = { cmd, state ->
            when (cmd) {
                is DecrementCounterCommand.Decrement -> sequenceOf(DecrementCounterEvent.Decremented(cmd.value,state.value + cmd.value))
                null -> emptySequence()
            }
        },
        evolve = { state, event ->
            when (event) {
                is DecrementCounterEvent.Decremented -> DecrementCounterState(event.newDecValue)
                null -> state
            }
        },
        initialState = { DecrementCounterState(0) }
    )


// Exactly the same type as the `counterSystem`, just combined out of two smaller capabilities.
val counterSystemCombined: System<CounterCommand?, CounterState, CounterEvent?> = incrementCounterSubSystem
    .combine(decrementCounterSubSystem)
    .mapState(
        { counterState ->
            Pair(
                IncrementCounterState(counterState.incValue),
                DecrementCounterState(counterState.decValue)
            )
        },
        { pair -> CounterState(pair.first.value, pair.second.value) })

