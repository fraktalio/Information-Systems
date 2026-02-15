package com.fraktalio

// ###############################################
//                  State Stored
// ###############################################

/**
 * Represents a **state-stored repository** that can fetch and persist state.
 *
 * This interface separates persistence concerns from domain logic.
 *
 * @param C Type of commands
 * @param S Type of state
 */

/**
 * Represents a repository that can fetch and save state for a given command type.
 *
 * @param C Type of commands
 * @param S Type of state
 */
interface IStateRepository<C, S> {

    /**
     * Fetches the current state corresponding to a command.
     *
     * @param command The command identifying which state to fetch
     * @return The current state if it exists, otherwise null
     */
    suspend fun fetchState(command: C): S?

    /**
     * Persists a new state.
     *
     * @param state The state to persist
     * @return The saved state
     */
    suspend fun save(state: S): S

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
    suspend fun process(command: C, system: StateStoredSystem<C, S>): S {
        val currentState = fetchState(command)
        val newState = system(command, currentState)
        return save(newState)
    }
}


/**
 * Handles a command by composing domain logic ([ISystem]) and repository operations ([IStateRepository]).
 */
suspend fun <SYS, C, S, E> SYS.handle(command: C): S
        where SYS : IStateRepository<C, S>,
              SYS : ISystem<C, S, E> =
    process(command, inStateStoredSystem())


// ###############################################
//                  Event-Sourced
// ###############################################

/**
 * Represents a repository that can fetch and persist events for a given command type.
 *
 * @param C Type of commands
 * @param E Type of events
 */
interface IEventRepository<C, E> {

    /**
     * Fetches the current sequence of events corresponding to a command.
     *
     * @param command The command identifying which events to fetch
     * @return A sequence of events, may be empty
     */
    suspend fun fetchEvents(command: C): Sequence<E>

    /**
     * Persists a sequence of events.
     *
     * @param events The sequence of events to persist
     * @return The newly saved sequence of events
     */
    suspend fun save(events: Sequence<E>): Sequence<E>

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
    suspend fun process(command: C, system: EventSourcedSystem<C, E, E>): Sequence<E> {
        val pastEvents = fetchEvents(command)
        val newEvents = system(command, pastEvents)
        return save(newEvents)
    }
}

/**
 * Handles a command by composing domain logic ([ISystem]) and event repository operations ([IEventRepository]).
 */
suspend fun <SYS, C, S, E> SYS.handle(command: C): Sequence<E>
        where SYS : IEventRepository<C, E>,
              SYS : ISystem<C, S, E> =
    process(command, inEventSourcedSystem())
