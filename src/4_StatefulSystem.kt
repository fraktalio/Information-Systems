package com.fraktalio

// ###############################################
//                  State Stored
// ###############################################

/**
 * Represents a **state-stored repository** that can fetch and persist state with metadata.
 *
 * This interface separates persistence concerns from domain logic while introducing
 * metadata at the infrastructure/application level. Metadata never leaks into the domain
 * layer ([IGeneralSystem], [IDynamicSystem], [ISystem]) - it exists only at the boundaries
 * where systems interact with the outside world.
 *
 * Metadata use cases:
 * - Versioning and optimistic locking
 * - Correlation IDs for distributed tracing
 *
 * @param Command Type of domain commands
 * @param CommandMetadata Type of metadata accompanying commands (e.g., correlation ID)
 * @param State Type of domain state
 * @param StateMetadata Type of metadata accompanying state (e.g., version, correlation ID, last modified timestamp)
 */
interface IStateRepository<Command, CommandMetadata, State, StateMetadata> {

    /**
     * Fetches the current state with its metadata corresponding to a command.
     *
     * @param command The command with metadata identifying which state to fetch
     * @return The current state with metadata if it exists, otherwise null
     */
    suspend fun fetchState(command: Pair<Command, CommandMetadata>): Pair<State, StateMetadata>?

    /**
     * Persists a new state with metadata.
     *
     * @param state The state paired with command metadata to persist
     * @return The saved state with its generated metadata (e.g., new version, correlation ID)
     */
    suspend fun save(state: Pair<State, CommandMetadata>): Pair<State, StateMetadata>

    /**
     * Processes a command using a given state-stored system.
     *
     * This method orchestrates the flow:
     * 1. Fetch current state (with metadata) from storage
     * 2. Extract pure domain state and pass to domain system (metadata-free)
     * 3. Domain system produces new state (pure domain logic)
     * 4. Attach command metadata and persist
     *
     * Benefits:
     * - **Atomicity / transactional semantics:** The fetch → compute → save sequence can
     *   be treated as a single logical operation if the repository supports transactions.
     * - **Domain purity:** Domain logic remains unaware of infrastructure concerns
     *
     * @param command The command with metadata to execute
     * @param system The state-stored system to apply (pure domain logic)
     * @return The newly persisted state with metadata
     */
    suspend fun process(
        command: Pair<Command, CommandMetadata>,
        system: StateStoredSystem<Command, State>
    ): Pair<State, StateMetadata> {
        val currentState: Pair<State, StateMetadata>? = fetchState(command)
        val newState: State = system(command.first, currentState?.first)
        return save(Pair(newState, command.second))
    }
}


/**
 * Handles a command by composing domain logic ([ISystem]) and repository operations ([IStateRepository]).
 *
 * This extension function provides a convenient way to process commands with metadata while
 * keeping the domain layer pure. The metadata flows through the infrastructure layer but
 * never enters the domain system.
 *
 * @param command The command paired with metadata (e.g., user context, correlation ID)
 * @return The resulting state paired with metadata (e.g., version, timestamp)
 */
suspend fun <SYSTEM, Command, State, Event, CommandMetadata, StateMetadata> SYSTEM.handle(command: Pair<Command, CommandMetadata>): Pair<State, StateMetadata>
        where SYSTEM : IStateRepository<Command, CommandMetadata, State, StateMetadata>,
              SYSTEM : ISystem<Command, State, Event> =
    process(command, inStateStoredSystem())


// ###############################################
//                  Event-Sourced
// ###############################################

/**
 * Represents a repository that can fetch and persist events with metadata.
 *
 * This interface separates persistence concerns from domain logic while introducing
 * metadata at the infrastructure/application level. Metadata never leaks into the domain
 * layer ([IGeneralSystem], [IDynamicSystem], [ISystem]) - it exists only at the boundaries
 * where systems interact with the outside world.
 *
 * Metadata use cases:
 * - Event versioning and schema evolution
 * - Causation and correlation IDs for event tracing
 * - Event store positions for subscriptions
 *
 * @param Command Type of domain commands
 * @param CommandMetadata Type of metadata accompanying commands (e.g., user ID, correlation ID)
 * @param InEvent Type of input events (read from event store)
 * @param InEventMetadata Type of metadata accompanying input events (e.g., position, timestamp)
 * @param OutEvent Type of output events (produced by domain logic)
 * @param OutEventMetadata Type of metadata accompanying output events (e.g., sequence number, timestamp)
 */
interface IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata> {

    /**
     * Fetches the current sequence of events with metadata corresponding to a command.
     *
     * @param command The command with metadata identifying which events to fetch
     * @return A sequence of events paired with their metadata, may be empty
     */
    suspend fun fetchEvents(command: Pair<Command, CommandMetadata>): Sequence<Pair<InEvent, InEventMetadata>>

    /**
     * Persists a sequence of events with metadata.
     *
     * @param events The sequence of domain events to persist
     * @param commandMetadata The command metadata to attach to persisted events
     * @return The newly saved sequence of events with their generated metadata
     */
    suspend fun save(
        events: Sequence<OutEvent>,
        commandMetadata: CommandMetadata
    ): Sequence<Pair<OutEvent, OutEventMetadata>>

    /**
     * Processes a command using a given event-sourced system.
     *
     * This method orchestrates the flow:
     * 1. Fetch past events (with metadata) from event store
     * 2. Extract pure domain events and pass to domain system (metadata-free)
     * 3. Domain system produces new events (pure domain logic)
     * 4. Attach command metadata and persist events
     *
     * Benefits:
     * - **Atomicity / transactional semantics:** The fetch → compute → save sequence can
     *   be treated as a single logical operation if the repository supports transactions.
     * - **Domain purity:** Domain logic remains unaware of infrastructure concerns
     *
     * @param command The command with metadata to execute
     * @param system The event-sourced system that produces events from a command and past events
     * @return The newly persisted sequence of events with metadata
     */
    suspend fun process(
        command: Pair<Command, CommandMetadata>,
        system: EventSourcedSystem<Command, InEvent, OutEvent>
    ): Sequence<Pair<OutEvent, OutEventMetadata>> {
        val pastEvents = fetchEvents(command).map { it.first }
        val newEvents = system(command.first, pastEvents)
        return save(newEvents, command.second)
    }
}

/**
 * Handles a command by composing domain logic ([ISystem]) and event repository operations ([IEventRepository]).
 *
 * This extension function provides a convenient way to process commands with metadata while
 * keeping the domain layer pure. The metadata flows through the infrastructure layer but
 * never enters the domain system.
 *
 * @param command The command paired with metadata (e.g., user context, correlation ID)
 * @return The resulting sequence of events paired with metadata (e.g., sequence numbers, timestamps)
 */
suspend fun <SYSTEM, Command, State, Event, CommandMetadata, EventMetadata> SYSTEM.handle(command: Pair<Command, CommandMetadata>): Sequence<Pair<Event, EventMetadata>>
        where SYSTEM : IEventRepository<Command, CommandMetadata, Event, EventMetadata, Event, EventMetadata>,
              SYSTEM : ISystem<Command, State, Event> =
    process(command, inEventSourcedSystem())

/**
 * Handles a command by composing domain logic ([IDynamicSystem]) and event repository operations ([IEventRepository]).
 *
 * This extension function provides a convenient way to process commands with metadata while
 * keeping the domain layer pure. Supports dynamic systems where input and output event types differ.
 *
 * @param command The command paired with metadata (e.g., user context, correlation ID)
 * @return The resulting sequence of events paired with metadata (e.g., sequence numbers, timestamps)
 */
suspend fun <SYSTEM, Command, State, InEvent, OutEvent, CommandMetadata, InEventMetadata, OutEventMetadata> SYSTEM.handle(
    command: Pair<Command, CommandMetadata>
): Sequence<Pair<OutEvent, OutEventMetadata>>
        where SYSTEM : IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata>,
              SYSTEM : IDynamicSystem<Command, State, InEvent, OutEvent> =
    process(command, inEventSourcedSystem())