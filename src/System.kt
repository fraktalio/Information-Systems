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
typealias StateStoredSystem<Command, State> = (Command, State) -> State


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
 * @param Event   A recorded, immutable state transition.
 */
typealias EventSourcedSystem<Command, Event> = (Command, Sequence<Event>) -> Sequence<Event>


/**
 * A **generalized algebraic model** that unifies *state-stored* and *event-sourced*
 * systems under a single abstraction.
 *
 * A system is characterized by three pure functions:
 *
 * 1. **decide** — Determines which events should occur, given a command and current state.
 * 2. **evolve** — Evolves the state by applying a single event.
 * 3. **initialState** — Provides the system’s starting state.
 *
 * Together, they form a reusable and composable description of a domain system.
 *
 * Formally:
 * ```
 * decide : Command × State → Sequence<Event>
 * evolve : State × Event → State
 * initialState : () → State
 * ```
 *
 * @param Command The type representing commands (intent to change state).
 * @param State   The type representing the system’s current state.
 * @param Event   The type representing domain events (immutable facts).
 */
interface ISystem<in Command, State, Event> {
    val decide: (Command, State) -> Sequence<Event>
    val evolve: (State, Event) -> State
    val initialState: () -> State
}

data class System<Command, State, Event>(
    override val decide: (Command, State) -> Sequence<Event>,
    override val evolve: (State, Event) -> State,
    override val initialState: () -> State
) : ISystem<Command, State, Event>


// ---------------------------------------------------------------------------
// Conversion functions: State Stored VS Event Sourced system
// ---------------------------------------------------------------------------

/**
 * Converts a general [System] into a traditional [StateStoredSystem].
 *
 * @return A [StateStoredSystem] view of this [System].
 */
fun <Command, State, Event> ISystem<Command, State, Event>.asStateStoredSystem():
        StateStoredSystem<Command, State> =
    { command, state ->
        val start = state ?: initialState()
        decide(command, start).fold(start) { acc, event -> evolve(acc, event) }
    }


/**
 * Converts a general [System] into an [EventSourcedSystem].
 *
 * @return An [EventSourcedSystem] view of this [System].
 */
fun <Command, State, Event> ISystem<Command, State, Event>.asEventSourcedSystem():
        EventSourcedSystem<Command, Event> =
    { command, events ->
        decide(command, events.fold(initialState()) { acc, event -> evolve(acc, event) })
    }


// ###########################################################################
// ## Functor mappings (covariant / contravariant transformations) ###########
// ###########################################################################

/**
 * **Functor over Command** — transforms the input command type of a [System].
 *
 * This allows adapting an existing system that handles commands of type [Command]
 * to one that accepts commands of a new type [Command2].
 *
 * Mathematically, this is a **contravariant functor** in the command position:
 * ```
 * mapCommand : (Command2 → Command) → System<Command, State, Event> → System<Command2, State, Event>
 * ```
 *
 * Example:
 * ```kotlin
 * val coreSystem: System<String, Int, String> = ...
 * val prefixed: System<Pair<String, Int>, Int, String> =
 *     coreSystem.mapCommand { (prefix, cmd) -> "$prefix-$cmd" }
 * ```
 *
 * @param f A mapping function from `Command2` to `Command`.
 * @return A new system that interprets [Command2] commands using [f].
 */
inline fun <Command, Command2, State, Event> System<Command, State, Event>.mapCommand(
    crossinline f: (Command2) -> Command
): System<Command2, State, Event> = System(
    decide = { command2, state -> decide(f(command2), state) },
    evolve = evolve,
    initialState = initialState
)

// Potentially, we can use a more general `_System` to delegate.

//inline fun <Command, Command2, State, Event> System<Command, State, Event>.mapCommand(
//    crossinline f: (Command2) -> Command
//): System<Command2, State, Event> = _System(decide, evolve, initialState)._mapCommand(f).asSystem()


/**
 * **Difunctor over Event** — transforms both the input and output event types of a [System].
 *
 * Because the system both *consumes* and *produces* events, this gives rise to **profunctor**
 * behavior — contravariant in input events (through `evolve`) and covariant in output events
 * (through `decide`).
 *
 * Formally:
 * ```
 * mapEvent : (Event2 → Event, Event → Event2)
 *           → System<Command, State, Event>
 *           → System<Command, State, Event2>
 * ```
 *
 * Intuitively:
 * - `fl` (left map) reinterprets external events before they're applied.
 * - `fr` (right map) converts internal events before they're emitted.
 *
 * Example:
 * ```kotlin
 * val systemA: System<String, Int, DomainEvent> = ...
 * val systemB: System<String, Int, JsonEvent> =
 *     systemA.mapEvent(
 *         fl = { json -> json.decode<DomainEvent>() },
 *         fr = { event -> event.encodeAsJson() }
 *     )
 * ```
 *
 * @param fl A function mapping external `Event2` into the system’s internal `Event` (contravariant).
 * @param fr A function mapping internal `Event` into external `Event2` (covariant).
 * @return A new system whose events are represented as [Event2].
 */
inline fun <Command, State, Event, Event2> System<Command, State, Event>.mapEvent(
    crossinline fl: (Event2) -> Event,
    crossinline fr: (Event) -> Event2
): System<Command, State, Event2> = System(
    decide = { command, state -> decide(command, state).map { fr(it) } },
    evolve = { state, event2 -> evolve(state, fl(event2)) },
    initialState = initialState
)

// Potentially, we can use a more general `_System` to delegate.

//inline fun <Command, State, Event, Event2> System<Command, State, Event>.mapEvent(
//    crossinline fl: (Event2) -> Event,
//    crossinline fr: (Event) -> Event2
//): System<Command, State, Event2> = _System(decide, evolve, initialState)._mapEvent(fl, fr).asSystem()


/**
 * **Difunctor over State** — transforms both the input and output state types of a [System].
 *
 * Similar to [mapEvent], this function operates as a **profunctor** over state:
 * - Contravariant in the input state (when deciding or evolving).
 * - Covariant in the output state (when producing new states).
 *
 * This allows reinterpreting the system’s internal state structure or representation
 * without changing its observable behavior.
 *
 * Formally:
 * ```
 * mapState : (State2 → State, State → State2)
 *           → System<Command, State, Event>
 *           → System<Command, State2, Event>
 * ```
 *
 * Example:
 * ```kotlin
 * val system: System<String, UserState, UserEvent> = ...
 * val jsonBased: System<String, JsonNode, UserEvent> =
 *     system.mapState(
 *         fl = { json -> json.toUserState() },
 *         fr = { state -> state.toJson() }
 *     )
 * ```
 *
 * @param fl A mapping from external `State2` to internal `State` (contravariant).
 * @param fr A mapping from internal `State` to external `State2` (covariant).
 * @return A new system operating over transformed state type [State2].
 */
inline fun <Command, State, State2, Event> System<Command, State, Event>.mapState(
    crossinline fl: (State2) -> State,
    crossinline fr: (State) -> State2
): System<Command, State2, Event> = System(
    decide = { command, state2 -> decide(command, fl(state2)) },
    evolve = { state2, event -> fr(evolve(fl(state2), event)) },
    initialState = { fr(initialState()) }
)

// Potentially, we can use a more general `_System` to delegate.

//inline fun <Command, State, State2, Event> System<Command, State, Event>.mapState(
//    crossinline fl: (State2) -> State,
//    crossinline fr: (State) -> State2
//): System<Command, State2, Event> = _System(decide, evolve, initialState)._mapState(fl, fr).asSystem()


// ---------------------------------------------------------------------------
// Composition
// ---------------------------------------------------------------------------

/**
 * Combines two [System] instances into a **product system** that processes
 * both sub-systems in parallel.
 *
 * The resulting system:
 *  - Accepts commands that are subtypes of [Command_SUPER]. Command_SUPER = Command1 + Command2
 *  - Evolves states as a [Pair] of the two sub-states. Pair<State1, State2> = State1 * State2
 *  - Emits events that are subtypes of [Event_SUPER]. Event_SUPER = Event1 + Event2
 *
 * This corresponds to the **monoidal product** of two systems.
 *
 * Formally:
 * ```
 * combine : System<Command1, State1, Event1> × System<Command2, State2, Event2> → System<Command1+Command2, (State1×State2), Event1+Event2>
 * ```
 *
 * @receiver The left-hand [System].
 * @param other Another [System] to combine with this one.
 * @return A new [System] that runs both systems in parallel.
 */
inline infix fun <
        reified Command1 : Command_SUPER,
        reified Command2 : Command_SUPER,
        reified Event1 : Event_SUPER,
        reified Event2 : Event_SUPER,
        Command_SUPER,
        State1,
        Event_SUPER,
        State2,
        >
        System<Command1?, State1, Event1?>.combine(
    other: System<Command2?, State2, Event2?>
): System<Command_SUPER?, Pair<State1, State2>, Event_SUPER?> =
    _System(decide, evolve, initialState)
        ._combine(_System(other.decide, other.evolve, other.initialState))
        .asSystem()


fun main() {
    println("Learn more at: https://fmodel.fraktalio.com")
}


