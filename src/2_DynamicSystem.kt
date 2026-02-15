package com.fraktalio

/**
 * Represents a dynamic system that generalizes the behavior of an event-sourced system.
 *
 * It allows reconstructing the current state from a sequence of events and producing new events in response to commands.
 *
 * @param Command The intent to modify the system.
 * @param State The type representing the system's current state.
 * @param InEvent The type representing input events used to reconstruct or evolve the system state.
 * @param OutEvent The type representing new events generated in response to commands.
 */
interface IDynamicSystem<in Command, State, in InEvent, out OutEvent> :
    IGeneralSystem<Command, State, State, InEvent, OutEvent> {

    /**
     * Converts this system into an [EventSourcedSystem].
     *
     * This handles reconstructing state from a sequence of events and producing
     * new events in response to a command.
     *
     * @return An EventSourcedSystem function `(Command, Sequence<InEvent>) -> Sequence<OutEvent>`
     */
    fun inEventSourcedSystem(): EventSourcedSystem<Command, InEvent, OutEvent> =
        { command, events ->
            decide(command, events.fold(initialState()) { acc, event -> evolve(acc, event) })
        }
}

data class DynamicSystem<Command, State, InEvent, OutEvent>(
    override val decide: (Command, State) -> Sequence<OutEvent>,
    override val evolve: (State, InEvent) -> State,
    override val initialState: () -> State
) : IDynamicSystem<Command, State, InEvent, OutEvent>


// ###########################################################################
// ## Functor mappings (covariant / contravariant transformations) ###########
// ###########################################################################

/**
 * **Functor over Command** — transforms the input command type of a [DynamicSystem].
 *
 * @param f A mapping function from `Command2` to `Command`.
 */
inline fun <Command, Command2, State, InEvent, OutEvent> DynamicSystem<Command, State, InEvent, OutEvent>.mapCommand(
    crossinline f: (Command2) -> Command
): DynamicSystem<Command2, State, InEvent, OutEvent> =
    GeneralSystem(decide, evolve, initialState).mapCommand(f).asDynamicSystem()


/**
 * **Difunctor over Event** — transforms both the input and output event types of a [DynamicSystem].
 *
 * @param fl A function mapping external `InEvent2` into the system’s internal `InEvent` (contravariant).
 * @param fr A function mapping internal `OutEvent` into external `OutEvent2` (covariant).
 */
inline fun <Command, State, InEvent, OutEvent, InEvent2, OutEvent2>
        DynamicSystem<Command, State, InEvent, OutEvent>.mapEvent(
    crossinline fl: (InEvent2) -> InEvent,
    crossinline fr: (OutEvent) -> OutEvent2
): DynamicSystem<Command, State, InEvent2, OutEvent2> =
    GeneralSystem(decide, evolve, initialState).mapEvent(fl, fr).asDynamicSystem()

/**
 * **Difunctor over State** — transforms both the input and output state types of a [DynamicSystem].
 *
 * @param fl A mapping from external `State2` to internal `State` (contravariant).
 * @param fr A mapping from internal `State` to external `State2` (covariant).
 */

inline fun <Command, State, State2, InEvent, OutEvent> DynamicSystem<Command, State, InEvent, OutEvent>.mapState(
    crossinline fl: (State2) -> State,
    crossinline fr: (State) -> State2
): DynamicSystem<Command, State2, InEvent, OutEvent> =
    GeneralSystem(decide, evolve, initialState).mapState(fl, fr).asDynamicSystem()


/**
 * Combines two [DynamicSystem] instances into a **product system** that processes
 * both sub-systems in parallel.
 *
 *
 * Formally:
 * ```
 * combine : DynamicSystem<Command1, State1, Event1> × DynamicSystem<Command2, State2, Event2> → DynamicSystem<Command1+Command2, (State1×State2), Event1+Event2>
 * ```
 *
 * @receiver The left-hand [DynamicSystem].
 * @param other Another [DynamicSystem] to combine with this one.
 * @return A new [DynamicSystem] that runs both systems in parallel.
 */
inline infix fun <
        reified C : C_SUPER,
        reified C2 : C_SUPER,
        reified InEvent : InEvent_SUPER,
        reified InEvent2 : InEvent_SUPER,
        reified OutEvent : OutEvent_SUPER,
        reified OutEvent2 : OutEvent_SUPER,
        C_SUPER,
        State,
        InEvent_SUPER,
        OutEvent_SUPER,
        State2,
        >
        DynamicSystem<C?, State, InEvent?, OutEvent?>.combine(
    other: DynamicSystem<C2?, State2, InEvent2?, OutEvent2?>
): DynamicSystem<
        C_SUPER?,
        Pair<State, State2>,
        InEvent_SUPER,
        OutEvent_SUPER?
        > =
    GeneralSystem(decide, evolve, initialState)
        .combine(GeneralSystem(other.decide, other.evolve, other.initialState))
        .asDynamicSystem()
