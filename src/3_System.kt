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
interface ISystem<in Command, State, Event> : IDynamicSystem<Command, State, Event, Event> {

    /**
     * Converts this system into a [StateStoredSystem].
     *
     * This handles:
     * - Initial state if no persisted state exists
     * - Folding events (via `decide` and `evolve`) to produce the resulting state
     *
     * @return A StateStoredSystem function `(Command, State?) -> State`
     */
    fun inStateStoredSystem(): StateStoredSystem<Command, State> =
        { command, state ->
            val current = state ?: initialState()
            decide(command, current).fold(current) { acc, event -> evolve(acc, event) }
        }

}

data class System<Command, State, Event>(
    override val decide: (Command, State) -> Sequence<Event>,
    override val evolve: (State, Event) -> State,
    override val initialState: () -> State
) : ISystem<Command, State, Event>


// ###########################################################################
// ## Functor mappings (covariant / contravariant transformations) ###########
// ###########################################################################

/**
 * **Functor over Command** — transforms the input command type of a [System].
 *
 * @param f A mapping function from `Command2` to `Command`.
 * @return A new system that interprets [Command2] commands using [f].
 */
inline fun <Command, Command2, State, Event> System<Command, State, Event>.mapCommand(
    crossinline f: (Command2) -> Command
): System<Command2, State, Event> = GeneralSystem(decide, evolve, initialState).mapCommand(f).asSystem()


/**
 * **Difunctor over Event** — transforms both the input and output event types of a [System].
 *
 * @param fl A function mapping external `Event2` into the system’s internal `Event` (contravariant).
 * @param fr A function mapping internal `Event` into external `Event2` (covariant).
 * @return A new system whose events are represented as [Event2].
 */

inline fun <Command, State, Event, Event2> System<Command, State, Event>.mapEvent(
    crossinline fl: (Event2) -> Event,
    crossinline fr: (Event) -> Event2
): System<Command, State, Event2> = GeneralSystem(decide, evolve, initialState).mapEvent(fl, fr).asSystem()


/**
 * **Difunctor over State** — transforms both the input and output state types of a [System].
 *
 * @param fl A mapping from external `State2` to internal `State` (contravariant).
 * @param fr A mapping from internal `State` to external `State2` (covariant).
 * @return A new system operating over transformed state type [State2].
 */

inline fun <Command, State, State2, Event> System<Command, State, Event>.mapState(
    crossinline fl: (State2) -> State,
    crossinline fr: (State) -> State2
): System<Command, State2, Event> = GeneralSystem(decide, evolve, initialState).mapState(fl, fr).asSystem()


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
    GeneralSystem(decide, evolve, initialState)
        .combine(GeneralSystem(other.decide, other.evolve, other.initialState))
        .asSystem()


fun main() {
    println("Learn more at: https://fmodel.fraktalio.com")
}


