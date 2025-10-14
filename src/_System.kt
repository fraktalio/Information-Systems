package com.fraktalio

/**
 * A **generalized model** that captures both state-stored and event-sourced systems.
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
@PublishedApi
internal data class _System<in Command, in InState, out OutState, in InEvent, out OutEvent>(
    val decide: (Command, InState) -> Sequence<OutEvent>,
    val evolve: (InState, InEvent) -> OutState,
    val initialState: () -> OutState,
)

/**
 * Our `3` param `System` type is just a specialization of a more general `5` param `_System` type
 *
 * ```
 * InState == OutState == State
 * InEvent == OutEvent == Event
 * ```
 */
@PublishedApi
internal fun <Command, State, Event> _System<Command, State, State, Event, Event>.asSystem(): System<Command, State, Event> =
    System(this.decide, this.evolve, this.initialState)


// #############################################################
// ## Functorial and Applicative Transformations              ##
// #############################################################
// These combinators provide categorical structure over `_System`:
//   - `_mapCommand`: contravariant mapping over commands
//   - `_mapEvent`:    profunctor mapping over events
//   - `_mapState`:    profunctor mapping over states
//   - `applyState`:   applicative function application on state
//   - `productOnState`: monoidal pairing of systems
// #############################################################

/**
 * Transforms the **command type** of this system.
 *
 * Enables adapting a system that handles commands of type [Command]
 * to one that accepts commands of type [Command2].
 *
 * Conceptually, this is a **contravariant functor** over the command type:
 *
 * ```
 * fmapCommand :: (Command2 -> Command) -> System<Command, ...> -> System<Command2, ...>
 * ```
 *
 * @param f A mapping function from the new command type [Command2] to the
 *          original command type [Command].
 * @return A new `_System` that applies [f] to its commands before delegating to the original system.
 */
@PublishedApi
internal inline fun <Command, Command2, InState, OutState, InEvent, OutEvent>
        _System<Command, InState, OutState, InEvent, OutEvent>._mapCommand(
    crossinline f: (Command2) -> Command
): _System<Command2, InState, OutState, InEvent, OutEvent> =
    _System(
        decide = { command2, state -> decide(f(command2), state) },
        evolve = evolve,
        initialState = initialState
    )


/**
 * Transforms the **event types** (input and output) of this system.
 *
 * This allows interoperability between systems that consume or emit
 * different event representations by providing conversion functions.
 *
 * Algebraically, this is a **Profunctor dimap** over the event parameters:
 *
 * ```
 * dimapEvent :: (InEvent2 -> InEvent)
 *            -> (OutEvent -> OutEvent2)
 *            -> System<..., InEvent, OutEvent>
 *            -> System<..., InEvent2, OutEvent2>
 * ```
 *
 * - Contravariant in [InEvent] (consumed during `evolve`)
 * - Covariant in [OutEvent] (produced by `decide`)
 *
 * @param fl Maps external input events → internal input events.
 * @param fr Maps internal output events → external output events.
 * @return A new `_System` that transforms events through [fl] and [fr].
 */
@PublishedApi
internal inline fun <Command, InState, OutState, InEvent, OutEvent, InEvent2, OutEvent2>
        _System<Command, InState, OutState, InEvent, OutEvent>._mapEvent(
    crossinline fl: (InEvent2) -> InEvent,
    crossinline fr: (OutEvent) -> OutEvent2
): _System<Command, InState, OutState, InEvent2, OutEvent2> =
    _System(
        decide = { command, state -> decide(command, state).map { fr(it) } },
        evolve = { state, event2 -> evolve(state, fl(event2)) },
        initialState = initialState
    )


/**
 * Transforms the **state representation** of this system.
 *
 * Useful for adapting a system to a different internal model of state
 * while preserving its overall semantics.
 *
 * Algebraically, this is also a **Profunctor dimap**, but over state types:
 *
 * ```
 * dimapState :: (InState2 -> InState)
 *            -> (OutState -> OutState2)
 *            -> System<InState, OutState>
 *            -> System<InState2, OutState2>
 * ```
 *
 * - Contravariant in [InState] (consumed during `decide` and `evolve`)
 * - Covariant in [OutState] (produced by `evolve` and `initialState`)
 *
 * @param fl Maps external input state → internal input state.
 * @param fr Maps internal output state → external output state.
 * @return A new `_System` that wraps the internal state transformation logic.
 */
@PublishedApi
internal inline fun <Command, InState, OutState, InEvent, OutEvent, InState2, OutState2>
        _System<Command, InState, OutState, InEvent, OutEvent>._mapState(
    crossinline fl: (InState2) -> InState,
    crossinline fr: (OutState) -> OutState2
): _System<Command, InState2, OutState2, InEvent, OutEvent> =
    _System(
        decide = { command, state2 -> decide(command, fl(state2)) },
        evolve = { state2, event -> fr(evolve(fl(state2), event)) },
        initialState = { fr(initialState()) }
    )


/**
 * Applies a system that produces a **function-valued state** to another system.
 *
 * This is the **Applicative `ap`** operation lifted over `_System`:
 *
 * ```
 * ap :: System<(A -> B)> -> System<A> -> System<B>
 * ```
 *
 * - Both systems share the same command, state, and event types.
 * - The first system (`ff`) evolves to produce a function `(OutputState) -> OutputState2`.
 * - The second system evolves to produce a value of type `OutputState`.
 * - The result applies the function to the value, yielding `OutputState2`.
 *
 * @param ff A system that produces a state-transforming function.
 * @return A new `_System` combining both systems applicatively.
 */
@PublishedApi
internal fun <Command, InState, OutState, InEvent, OutEvent, OutState2>
        _System<Command, InState, OutState, InEvent, OutEvent>._applyState(
    ff: _System<Command, InState, (OutState) -> OutState2, InEvent, OutEvent>
): _System<Command, InState, OutState2, InEvent, OutEvent> =
    _System(
        decide = { c, si -> sequenceOf(ff.decide(c, si), decide(c, si)).flatten() },
        evolve = { si, ei -> ff.evolve(si, ei)(evolve(si, ei)) },
        initialState = { ff.initialState()(initialState()) }
    )


/**
 * Combines two systems that share the same input state type,
 * producing a new system whose state is a **pair** of their outputs.
 *
 * Algebraically, this corresponds to the **Applicative product** (or `liftA2`)
 * that pairs results from two independent computations:
 *
 * ```
 * product :: System<A> -> System<B> -> System<(A, B)>
 * ```
 *
 * Implemented via `applyState` and a lifted pairing function.
 *
 * @param fb Another system operating over the same input state.
 * @return A new `_System` that evolves both systems in parallel and returns a pair of states.
 */
@PublishedApi
internal fun <Command, InState, OutState, InEvent, OutEvent, OutState2>
        _System<Command, InState, OutState, InEvent, OutEvent>._productOnState(
    other: _System<Command, InState, OutState2, InEvent, OutEvent>
): _System<Command, InState, Pair<OutState, OutState2>, InEvent, OutEvent> =
    _applyState(
        other._mapState({ it }) { b: OutState2 -> { a: OutState -> Pair(a, b) } }
    )


/**
 * Combines two systems that may operate on different command, state, and event types
 * into a single system that can handle the **union** of their command and event domains.
 *
 * This function enables **parallel composition** of heterogeneous systems that share
 * similar structural semantics but differ in domain types.
 *
 * ```
 * combine ::
 *   System<C, InState, OutState, InEvent, OutEvent>
 *   -> System<C2, InState2, OutState2, InEvent2, OutEvent2>
 *   -> System<C_SUPER, (InState, InState2), (OutState, OutState2), InEvent_SUPER, OutEvent_SUPER>
 * ```
 *
 * Each component system:
 * - Receives only commands and events of its own subtype.
 * - Evolves its portion of the overall state independently.
 * - Emits its own events (upcast to the supertype).
 *
 * The combined system merges their outputs into a single paired state and shared event stream.
 *
 * @return A new `_System` that runs both systems side by side.
 */
@PublishedApi
internal inline infix fun <
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
        _System<C?, InState, OutState, InEvent?, OutEvent?>._combine(
    other: _System<C2?, InState2, OutState2, InEvent2?, OutEvent2?>
): _System<
        C_SUPER?,
        Pair<InState, InState2>,
        Pair<OutState, OutState2>,
        InEvent_SUPER,
        OutEvent_SUPER?
        > {

    val systemA = this
        ._mapCommand<C?, C_SUPER?, InState, OutState, InEvent?, OutEvent?> { it as? C }
        ._mapState<C_SUPER?, InState, OutState, InEvent?, OutEvent?, Pair<InState, InState2>, OutState>(
            { it.first },
            { it }
        )
        ._mapEvent<C_SUPER?, Pair<InState, InState2>, OutState, InEvent?, OutEvent?, InEvent_SUPER, OutEvent_SUPER?>(
            { it as? InEvent },
            { it }
        )

    val systemB = other
        ._mapCommand<C2?, C_SUPER?, InState2, OutState2, InEvent2?, OutEvent2?> { it as? C2 }
        ._mapState<C_SUPER?, InState2, OutState2, InEvent2?, OutEvent2?, Pair<InState, InState2>, OutState2>(
            { it.second },
            { it }
        )
        ._mapEvent<C_SUPER?, Pair<InState, InState2>, OutState2, InEvent2?, OutEvent2?, InEvent_SUPER, OutEvent_SUPER?>(
            { it as? InEvent2 },
            { it }
        )

    return systemA._productOnState(systemB)
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
    _System<IncrementCounterCommand?, IncrementCounterState, IncrementCounterState, IncrementCounterEvent?, IncrementCounterEvent?>(
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
    _System<DecrementCounterCommand?, DecrementCounterState, DecrementCounterState, DecrementCounterEvent?, DecrementCounterEvent?>(
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
 * This `_System` serves as the **identity element** for system combination,
 * making `_System` a **Monoid** under the `_combine` operation.
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
 * - Proves that `_System` with `_combine` and `emptySystem` forms a Monoid.
 */
private val emptySystem = _System<Nothing?, Unit, Unit, Nothing?, Nothing?>(
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

//private val counterSystem = _System<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?>(
//    decide = { cmd, _ ->
//        when (cmd) {
//            IncrementCounterCommand.Increment -> sequenceOf(IncrementCounterEvent.Incremented)
//            DecrementCounterCommand.Decrement -> sequenceOf(DecrementCounterEvent.Decremented)
//            null -> emptySequence() // ignore unrelated commands
//        }
//    },
//    evolve = { state, event ->
//        when (event) {
//            IncrementCounterEvent.Incremented -> state + 1
//            DecrementCounterEvent.Decremented -> state - 1
//            null -> state // ignore unrelated events
//        }
//    },
//    initialState = { CounterState(0) }
//)

private val counterSystem: _System<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?> =
    incrementCounterSystem
        ._combine(decrementCounterSystem) // _System<CounterCommand?, Pair<IncrementCounterState, DecrementCounterState>, Pair<IncrementCounterState, DecrementCounterState>, CounterEvent?, CounterEvent?>
        ._mapState(
            { counterState -> Pair(IncrementCounterState(counterState.value), DecrementCounterState(0)) },
            { pair -> CounterState(pair.first.value + pair.second.value) }) // _System<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?>


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
private val counterSystem1: _System<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?> =
    counterSystem
        ._combine(emptySystem) // _System<CounterCommand?, Pair<CounterState, Unit>, Pair<CounterState, Unit>, CounterEvent?, CounterEvent?>
        ._mapState(
            { counterState -> Pair(CounterState(counterState.value), Unit) },
            { pair -> pair.first }) // _System<CounterCommand?, CounterState, CounterState, CounterEvent?, CounterEvent?>


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
