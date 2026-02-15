package com.fraktalio

// ###########################################################################
// ########### Information Systems encoded in Kotlin's type system ###########
// #### (encoding information systems as composable algebraic structures) ####
// ###########################################################################

/**
 * Represents a **traditional (state-stored) information system**.
 *
 * This model stores only the *current state* of the system. When a command
 * (an intention to modify state) is applied, it directly produces a new state.
 *
 * Formally:
 * ```
 * Command × State → State
 * ```
 *
 * @param Command The intent to modify the system.
 * @param State   The current system state.
 */
typealias StateStoredSystem<Command, State> = (Command, State?) -> State


/**
 * Represents an **event-sourced information system**.
 *
 * In this model, state is not stored directly but reconstructed from
 * a *sequence of past events*. Commands produce new events, which are
 * appended immutably to the event log.
 *
 * Formally:
 * ```
 * Command × Sequence<Event> → Sequence<Event>
 * ```
 *
 * @param Command The intent to modify the system.
 * @param InEvent   A recorded, immutable state transition.
 * @param OutEvent  A new recorded, immutable state transition.
 */
typealias EventSourcedSystem<Command, InEvent, OutEvent> = (Command, Sequence<InEvent>) -> Sequence<OutEvent>


/**
 * A **generalized model** that captures all systems.
 *
 * A system is defined by three pure functions:
 * 1. [decide]: Determines which events should occur given a command and current state.
 * 2. [evolve]: Evolves the state by applying an event.
 * 3. [initialState]: Returns the system’s initial state.
 *
 * Together, these form a reusable and composable algebra for information systems.
 *
 * @param Command The intent to modify the system.
 * @param InState The current system state / input state.
 * @param OutState The new system state / output state
 * @param InEvent   The current state transition / input event(s).
 * @param OutEvent   The new recorded state transition / output event(s).
 */
interface IGeneralSystem<in Command, in InState, out OutState, in InEvent, out OutEvent> {
    val decide: (Command, InState) -> Sequence<OutEvent>
    val evolve: (InState, InEvent) -> OutState
    val initialState: () -> OutState
}

data class GeneralSystem<in Command, in InState, out OutState, in InEvent, out OutEvent>(
    override val decide: (Command, InState) -> Sequence<OutEvent>,
    override val evolve: (InState, InEvent) -> OutState,
    override val initialState: () -> OutState,
) : IGeneralSystem<Command, InState, OutState, InEvent, OutEvent>

/**
 * Our `4` param `System` type is just a specialization of a more general `5` param `GeneralSystem` type
 *
 * ```
 * InState == OutState == State
 * InEvent != OutEvent
 * ```
 */
fun <Command, State, InEvent, OutEvent> GeneralSystem<Command, State, State, InEvent, OutEvent>.asDynamicSystem(): DynamicSystem<Command, State, InEvent, OutEvent> =
    DynamicSystem(this.decide, this.evolve, this.initialState)


/**
 * Our `3` param `System` type is just a specialization of a more general `5` param `GeneralSystem` type
 *
 * ```
 * InState == OutState == State
 * InEvent == OutEvent == Event
 * ```
 */
fun <Command, State, Event> GeneralSystem<Command, State, State, Event, Event>.asSystem(): System<Command, State, Event> =
    System(this.decide, this.evolve, this.initialState)


// #############################################################
// ## Functorial and Applicative Transformations              ##
// #############################################################
// These combinators provide categorical structure over `GeneralSystem`:
//   - `_mapCommand`: contravariant mapping over commands
//   - `_mapEvent`:    profunctor mapping over events
//   - `_mapState`:    profunctor mapping over states
//   - `applyState`:   applicative function application on state
//   - `productOnState`: monoidal pairing of systems
// #############################################################

/**
 * Transforms the **command type** of this system.
 * A **contravariant functor** over the command type.
 *
 * @param f A mapping function from the new command type [Command2] to the original command type [Command].
 * @return A new `GeneralSystem` that applies [f] to its commands before delegating to the original system.
 */
inline fun <Command, Command2, InState, OutState, InEvent, OutEvent>
        GeneralSystem<Command, InState, OutState, InEvent, OutEvent>.mapCommand(
    crossinline f: (Command2) -> Command
): GeneralSystem<Command2, InState, OutState, InEvent, OutEvent> =
    GeneralSystem(
        decide = { command2, state -> decide(f(command2), state) },
        evolve = evolve,
        initialState = initialState
    )


/**
 * Transforms the **event types** (input and output) of this system.
 * A **Profunctor dimap** over the event parameters:
 *
 * - Contravariant in [InEvent] (consumed during `evolve`)
 * - Covariant in [OutEvent] (produced by `decide`)
 *
 * @param fl Maps external input events → internal input events.
 * @param fr Maps internal output events → external output events.
 * @return A new `GeneralSystem` that transforms events through [fl] and [fr].
 */
inline fun <Command, InState, OutState, InEvent, OutEvent, InEvent2, OutEvent2>
        GeneralSystem<Command, InState, OutState, InEvent, OutEvent>.mapEvent(
    crossinline fl: (InEvent2) -> InEvent,
    crossinline fr: (OutEvent) -> OutEvent2
): GeneralSystem<Command, InState, OutState, InEvent2, OutEvent2> =
    GeneralSystem(
        decide = { command, state -> decide(command, state).map { fr(it) } },
        evolve = { state, event2 -> evolve(state, fl(event2)) },
        initialState = initialState
    )


/**
 * Transforms the **state representation** of this system.
 * A **Profunctor dimap**, but over state types:
 *
 * - Contravariant in [InState] (consumed during `decide` and `evolve`)
 * - Covariant in [OutState] (produced by `evolve` and `initialState`)
 *
 * @param fl Maps external input state → internal input state.
 * @param fr Maps internal output state → external output state.
 * @return A new `GeneralSystem` that wraps the internal state transformation logic.
 */
inline fun <Command, InState, OutState, InEvent, OutEvent, InState2, OutState2>
        GeneralSystem<Command, InState, OutState, InEvent, OutEvent>.mapState(
    crossinline fl: (InState2) -> InState,
    crossinline fr: (OutState) -> OutState2
): GeneralSystem<Command, InState2, OutState2, InEvent, OutEvent> =
    GeneralSystem(
        decide = { command, state2 -> decide(command, fl(state2)) },
        evolve = { state2, event -> fr(evolve(fl(state2), event)) },
        initialState = { fr(initialState()) }
    )


/**
 * Applies a system that produces a **function-valued state** to another system.
 * An **Applicative `ap`** operation lifted over `GeneralSystem`.
 *
 * @param ff A system that produces a state-transforming function.
 * @return A new `GeneralSystem` combining both systems applicatively.
 */
fun <Command, InState, OutState, InEvent, OutEvent, OutState2>
        GeneralSystem<Command, InState, OutState, InEvent, OutEvent>.applyState(
    ff: GeneralSystem<Command, InState, (OutState) -> OutState2, InEvent, OutEvent>
): GeneralSystem<Command, InState, OutState2, InEvent, OutEvent> =
    GeneralSystem(
        decide = { c, si -> sequenceOf(ff.decide(c, si), decide(c, si)).flatten() },
        evolve = { si, ei -> ff.evolve(si, ei)(evolve(si, ei)) },
        initialState = { ff.initialState()(initialState()) }
    )


/**
 * Combines two systems that share the same input state type,
 * producing a new system whose state is a **pair** of their outputs.
 *
 * Algebraically, this corresponds to the **Applicative product** (or `liftA2`)
 *
 * @param fb Another system operating over the same input state.
 * @return A new `GeneralSystem` that evolves both systems in parallel and returns a pair of states.
 */
fun <Command, InState, OutState, InEvent, OutEvent, OutState2>
        GeneralSystem<Command, InState, OutState, InEvent, OutEvent>.productOnState(
    other: GeneralSystem<Command, InState, OutState2, InEvent, OutEvent>
): GeneralSystem<Command, InState, Pair<OutState, OutState2>, InEvent, OutEvent> =
    applyState(
        other.mapState({ it }) { b: OutState2 -> { a: OutState -> Pair(a, b) } }
    )


/**
 * Combines two systems that may operate on different command, state, and event types
 * into a single system that can handle the **union** of their command and event domains.
 *
 * @return A new `GeneralSystem` that runs both systems side by side.
 */
inline infix fun <
        reified C : C_SUPER,
        reified C2 : C_SUPER,
        reified InEvent : InEvent_SUPER,
        reified InEvent2 : InEvent_SUPER,
        reified OutEvent : OutEvent_SUPER,
        reified OutEvent2 : OutEvent_SUPER,
        C_SUPER,
        InState,
        OutState,
        InEvent_SUPER,
        OutEvent_SUPER,
        InState2,
        OutState2
        >
        GeneralSystem<C?, InState, OutState, InEvent?, OutEvent?>.combine(
    other: GeneralSystem<C2?, InState2, OutState2, InEvent2?, OutEvent2?>
): GeneralSystem<
        C_SUPER?,
        Pair<InState, InState2>,
        Pair<OutState, OutState2>,
        InEvent_SUPER,
        OutEvent_SUPER?
        > {

    val systemA = this
        .mapCommand<C?, C_SUPER?, InState, OutState, InEvent?, OutEvent?> { it as? C }
        .mapState<C_SUPER?, InState, OutState, InEvent?, OutEvent?, Pair<InState, InState2>, OutState>(
            { it.first },
            { it }
        )
        .mapEvent<C_SUPER?, Pair<InState, InState2>, OutState, InEvent?, OutEvent?, InEvent_SUPER, OutEvent_SUPER?>(
            { it as? InEvent },
            { it }
        )

    val systemB = other
        .mapCommand<C2?, C_SUPER?, InState2, OutState2, InEvent2?, OutEvent2?> { it as? C2 }
        .mapState<C_SUPER?, InState2, OutState2, InEvent2?, OutEvent2?, Pair<InState, InState2>, OutState2>(
            { it.second },
            { it }
        )
        .mapEvent<C_SUPER?, Pair<InState, InState2>, OutState2, InEvent2?, OutEvent2?, InEvent_SUPER, OutEvent_SUPER?>(
            { it as? InEvent2 },
            { it }
        )

    return systemA.productOnState(systemB)
}

// ####################################################################################
// ##################################### EXAMPLES #####################################
// ####################################################################################

// ################################# Increment system #################################
private sealed interface IncrementCounterCommand : CounterCommand {
    data object Increment : IncrementCounterCommand
}

private sealed interface IncrementCounterEvent : CounterEvent {
    data object Incremented : IncrementCounterEvent
}

@JvmInline
private value class IncrementCounterState(val value: Int) {

    operator fun plus(other: Int): IncrementCounterState {
        return IncrementCounterState(this.value + other)
    }

    override fun toString(): String = value.toString()
}

private val incrementCounterSystem =
    GeneralSystem<IncrementCounterCommand?, IncrementCounterState, IncrementCounterState, IncrementCounterEvent?, IncrementCounterEvent?>(
        decide = { cmd, _ ->
            when (cmd) {
                IncrementCounterCommand.Increment -> sequenceOf(IncrementCounterEvent.Incremented)
                null -> emptySequence() // ignore unrelated commands
            }
        },
        evolve = { state, event ->
            when (event) {
                IncrementCounterEvent.Incremented -> state + 1
                null -> state // ignore unrelated events
            }
        },
        initialState = { IncrementCounterState(0) }
    )


// ################################# Decrement system #################################

private sealed interface DecrementCounterCommand : CounterCommand {
    data object Decrement : DecrementCounterCommand
}

private sealed interface DecrementCounterEvent : CounterEvent {
    data object Decremented : DecrementCounterEvent
}

@JvmInline
private value class DecrementCounterState(val value: Int) {

    operator fun minus(other: Int): DecrementCounterState {
        return DecrementCounterState(this.value - other)
    }

    override fun toString(): String = value.toString()
}

private val decrementCounterSystem =
    GeneralSystem<DecrementCounterCommand?, DecrementCounterState, DecrementCounterState, DecrementCounterEvent?, DecrementCounterEvent?>(
        decide = { cmd, _ ->
            when (cmd) {
                DecrementCounterCommand.Decrement -> sequenceOf(DecrementCounterEvent.Decremented)
                null -> emptySequence() // ignore unrelated commands
            }
        },
        evolve = { state, event ->
            when (event) {
                DecrementCounterEvent.Decremented -> state - 1
                null -> state // ignore unrelated events
            }
        },
        initialState = { DecrementCounterState(0) }
    )


// ################################# Empty/Identity system #################################

/**
 * A trivial / no-op system that does nothing.
 *
 * This `GeneralSystem` serves as the **identity element** for system combination,
 * making `GeneralSystem` a **Monoid** under the `_combine` operation.
 *
 * Type parameters:
 * - Command / Event: `Nothing?` — this system never consumes or emits anything.
 * - State: `Unit` — carries no information.
 *
 * Behavior:
 * 1. `decide`: always returns an empty sequence; produces no events.
 * 2. `evolve`: always returns `Unit`; state never changes.
 * 3. `initialState`: returns `Unit`.
 *
 * Monoid laws satisfied:
 *
 * 1. **Left identity:**
 *    `emptySystem._combine(s) == s`
 *    Combining any system `s` with `emptySystem` on the left leaves `s` unchanged.
 *
 * 2. **Right identity:**
 *    `s._combine(emptySystem) == s`
 *    Combining any system `s` with `emptySystem` on the right leaves `s` unchanged.
 *
 * 3. **Associativity:**
 *    `(a._combine(b))._combine(c) == a._combine(b._combine(c))`
 *    Combining multiple systems is associative.
 *
 * Use cases:
 * - Acts as a neutral element for `_combine`.
 * - Useful in testing or as a placeholder system.
 * - Proves that `GeneralSystem` with `_combine` and `emptySystem` forms a Monoid.
 */
private val emptySystem = GeneralSystem<Nothing?, Unit, Unit, Nothing?, Nothing?>(
    decide = { _, _ -> emptySequence() },
    evolve = { _, _ -> Unit },
    initialState = { Unit }
)

// ################################# Combined (Counter) system #################################
private sealed interface CounterCommand
private sealed interface CounterEvent

private data class CounterState(val value: Int) {

    operator fun plus(other: Int): CounterState {
        return CounterState(this.value + other)
    }

    operator fun minus(other: Int): CounterState {
        return CounterState(this.value - other)
    }

    override fun toString(): String = value.toString()
}


private val counterSystem: GeneralSystem<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?> =
    incrementCounterSystem
        .combine(decrementCounterSystem) // GeneralSystem<CounterCommand?, Pair<IncrementCounterState, DecrementCounterState>, Pair<IncrementCounterState, DecrementCounterState>, CounterEvent?, CounterEvent?>
        .mapState(
            { counterState -> Pair(IncrementCounterState(counterState.value), DecrementCounterState(0)) },
            { pair -> CounterState(pair.first.value + pair.second.value) }) // GeneralSystem<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?>


/**
 * Monoid laws satisfied:
 *
 * 1. **Left identity:**
 *    `emptySystem._combine(s) == s`
 *    Combining any system `s` with `emptySystem` on the left leaves `s` unchanged.
 *
 * 2. **Right identity:**
 *    `s._combine(emptySystem) == s`
 *    Combining any system `s` with `emptySystem` on the right leaves `s` unchanged.
 *
 *
 */
private val counterSystem1: GeneralSystem<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?> =
    counterSystem
        .combine(emptySystem) // GeneralSystem<CounterCommand?, Pair<CounterState, Unit>, Pair<CounterState, Unit>, CounterEvent?, CounterEvent?>
        .mapState(
            { counterState -> Pair(CounterState(counterState.value), Unit) },
            { pair -> pair.first }) // GeneralSystem<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?>


fun main() {
    println("=== Fraktalio Counter Systems Demo ===\n")

    // ----------------------- Increment Counter Demo -----------------------
    var incState = incrementCounterSystem.initialState()
    println("Increment Counter Initial State: $incState")

    val incCommands: List<IncrementCounterCommand?> = listOf(
        IncrementCounterCommand.Increment,
        IncrementCounterCommand.Increment,
        null
    )

    for (cmd in incCommands) {
        val events = incrementCounterSystem.decide(cmd, incState)
        for (event in events) {
            incState = incrementCounterSystem.evolve(incState, event)
        }
        println("After Increment Command $cmd -> State: $incState")
    }

    println("\n----------------------- Decrement Counter Demo -----------------------")
    var decState = decrementCounterSystem.initialState()
    println("Decrement Counter Initial State: $decState")

    val decCommands: List<DecrementCounterCommand?> = listOf(
        DecrementCounterCommand.Decrement,
        DecrementCounterCommand.Decrement,
        null
    )

    for (cmd in decCommands) {
        val events = decrementCounterSystem.decide(cmd, decState)
        for (event in events) {
            decState = decrementCounterSystem.evolve(decState, event)
        }
        println("After Decrement Command $cmd -> State: $decState")
    }

    println("\n----------------------- Combined Counter Demo -----------------------")
    var combinedState = counterSystem1.initialState()
    println("Combined Counter Initial State: $combinedState")

    val combinedCommands: List<CounterCommand?> = listOf(
        IncrementCounterCommand.Increment,
        IncrementCounterCommand.Increment,
        DecrementCounterCommand.Decrement,
        IncrementCounterCommand.Increment,
        null
    )

    for (cmd in combinedCommands) {
        val events = counterSystem1.decide(cmd, combinedState)
        for (event in events) {
            combinedState = counterSystem1.evolve(combinedState, event)
        }
        println("After Command $cmd -> State: $combinedState")
    }

    println("\n=== Demo Complete ===")
}
