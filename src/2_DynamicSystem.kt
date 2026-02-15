package com.fraktalio

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
): DynamicSystem<Command2, State, InEvent, OutEvent> = GeneralSystem(decide, evolve, initialState).mapCommand(f).asDynamicSystem()


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
): DynamicSystem<Command, State2, InEvent, OutEvent> = GeneralSystem(decide, evolve, initialState).mapState(fl, fr).asDynamicSystem()

