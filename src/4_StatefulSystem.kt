package com.fraktalio

// ###############################################
//                  State Stored
// ###############################################

/**
 * Represents a **state-stored repository** that can fetch and persist state.
 *
 * This interface separates persistence concerns from domain logic.
 *
 * @param Command Type of commands
 * @param State Type of state
 */
interface IStateRepository<Command, State> {

    /**
     * Fetches the current state corresponding to a command.
     *
     * @param command The command identifying which state to fetch
     * @return The current state if it exists, otherwise null
     */
    suspend fun fetchState(command: Command): State?

    /**
     * Persists a new state.
     *
     * @param state The state to persist
     * @return The saved state
     */
    suspend fun save(state: State): State

    /**
     * Processes a command using a given state-stored system.
     * Benefits:
     * - **Atomicity / transactional semantics:** The fetch → compute → save sequence can
     *   be treated as a single logical operation if the repository supports transactions.
     *
     * @param command The command to execute
     * @param system The state-stored system to apply
     * @return The newly persisted state
     */
    suspend fun process(command: Command, system: StateStoredSystem<Command, State>): State {
        val currentState = fetchState(command)
        val newState = system(command, currentState)
        return save(newState)
    }
}


/**
 * Handles a command by composing domain logic ([ISystem]) and repository operations ([IStateRepository]).
 */
suspend fun <SYSTEM, Command, State, Event> SYSTEM.handle(command: Command): State
        where SYSTEM : IStateRepository<Command, State>,
              SYSTEM : ISystem<Command, State, Event> =
    process(command, inStateStoredSystem())


// ###############################################
//                  Event-Sourced
// ###############################################

/**
 * Represents a repository that can fetch and persist events for a given command type.
 *
 * @param Command Type of commands
 * @param InEvent Type of input events
 * @param OutEvent Type of output events
 */
interface IEventRepository<Command, InEvent, OutEvent> {

    /**
     * Fetches the current sequence of events corresponding to a command.
     *
     * @param command The command identifying which events to fetch
     * @return A sequence of events, may be empty
     */
    suspend fun fetchEvents(command: Command): Sequence<InEvent>

    /**
     * Persists a sequence of events.
     *
     * @param events The sequence of events to persist
     * @return The newly saved sequence of events
     */
    suspend fun save(events: Sequence<OutEvent>): Sequence<OutEvent>

    /**
     * Processes a command using a given event-sourced system.
     * Benefits:
     * - **Atomicity / transactional semantics:** The fetch → compute → save sequence can
     *   be treated as a single logical operation if the repository supports transactions.
     *
     * @param command The command to execute
     * @param system The event-sourced system that produces events from a command and past events
     * @return The newly persisted sequence of events
     */
    suspend fun process(command: Command, system: EventSourcedSystem<Command, InEvent, OutEvent>): Sequence<OutEvent> {
        val pastEvents = fetchEvents(command)
        val newEvents = system(command, pastEvents)
        return save(newEvents)
    }
}

/**
 * Handles a command by composing domain logic ([ISystem]) and event repository operations ([IEventRepository]).
 */
suspend fun <SYSTEM, Command, State, Event> SYSTEM.handle(command: Command): Sequence<Event>
        where SYSTEM : IEventRepository<Command, Event, Event>,
              SYSTEM : ISystem<Command, State, Event> =
    process(command, inEventSourcedSystem())

/**
 * Handles a command by composing domain logic ([IDynamicSystem]) and event repository operations ([IEventRepository]).
 */
suspend fun <SYSTEM, Command, State, InEvent, OutEvent> SYSTEM.handle(command: Command): Sequence<OutEvent>
        where SYSTEM : IEventRepository<Command, InEvent, OutEvent>,
              SYSTEM : IDynamicSystem<Command, State, InEvent, OutEvent> =
    process(command, inEventSourcedSystem())