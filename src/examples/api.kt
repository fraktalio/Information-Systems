package com.fraktalio.examples

// ############ Counter System #########

// -------------------------------------
//              COMMANDS
// -------------------------------------

// Sealed hierarchy clearly define SUM/OR relationship of your Data/API.
// CounterCommand is a SUM of all IncrementCounterCommand(s) and DecrementCounterCommand(s)
sealed interface CounterCommand

// ### Increment Counter SubSystem ###
sealed interface IncrementCounterCommand : CounterCommand {
    data class Increment(val value: Int) : IncrementCounterCommand
}

// ### Decrement Counter SubSystem ###
sealed interface DecrementCounterCommand : CounterCommand {
    data class Decrement(val value: Int) : DecrementCounterCommand
}

// -------------------------------------
//               EVENTS
// -------------------------------------

// Sealed hierarchy clearly define SUM/OR relationship of your Data/API.
// CounterCommand is a SUM of all IncrementCounterEvent(s) and DecrementCounterEvent(s)
sealed interface CounterEvent

// ### Increment Counter SubSystem ###
sealed interface IncrementCounterEvent : CounterEvent {
    data class Incremented(val value: Int, val newIncValue: Int) : IncrementCounterEvent
}

// ### Decrement Counter SubSystem ###
sealed interface DecrementCounterEvent : CounterEvent {
    data class Decremented(val value: Int, val newDecValue: Int) : DecrementCounterEvent
}

// -------------------------------------
//                STATE
// -------------------------------------
data class CounterState(
    val incValue: Int = 0,
    val decValue: Int = 0
) {
    val balance: Int
        get() = incValue - decValue

    operator fun plus(value: Int): CounterState =
        copy(incValue = incValue + value)

    operator fun minus(value: Int): CounterState =
        copy(decValue = decValue + value)

    override fun toString(): String =
        "CounterState(inc=$incValue, dec=$decValue, balance=$balance)"
}

// ### Increment Counter SubSystem ###
@JvmInline
value class IncrementCounterState(val value: Int) {

    operator fun plus(other: Int): IncrementCounterState {
        return IncrementCounterState(this.value + other)
    }

    override fun toString(): String = value.toString()
}

// ### Decrement Counter SubSystem ###
@JvmInline
value class DecrementCounterState(val value: Int) {

    operator fun minus(other: Int): DecrementCounterState {
        return DecrementCounterState(this.value + other)
    }

    override fun toString(): String = value.toString()
}
