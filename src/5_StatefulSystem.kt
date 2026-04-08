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
 * @param Txn Type of transaction handle (e.g., JDBC Connection, Mongo ClientSession, or Unit for in-memory)
 */
interface IStateRepository<Command, CommandMetadata, State, StateMetadata, Txn> {

    /**
     * Fetches the current state with its metadata corresponding to a command.
     *
     * @param command The command with metadata identifying which state to fetch
     * @param txn The transaction handle to use for this operation
     * @return The current state with metadata if it exists, otherwise null
     */
    suspend fun fetchState(command: Pair<Command, CommandMetadata>, txn: Txn): Pair<State, StateMetadata>?

    /**
     * Persists a new state with metadata.
     *
     * @param state The state paired with command metadata to persist
     * @param txn The transaction handle to use for this operation
     * @return The saved state with its generated metadata (e.g., new version, correlation ID)
     */
    suspend fun save(state: Pair<State, CommandMetadata>, txn: Txn): Pair<State, StateMetadata>

    /**
     * Executes a block within a transaction, managing the transaction lifecycle (begin, commit, rollback).
     *
     * The locking strategy is left to the implementor:
     * - **Pessimistic:** [fetchState] acquires a lock within the transaction (e.g., `SELECT ... FOR UPDATE`), held until commit
     * - **Optimistic:** [fetchState] reads a version, [save] checks it hasn't changed (e.g., `UPDATE ... WHERE version = ?`)
     *
     * Example (JDBC):
     * ```kotlin
     * override suspend fun <T> inTransaction(block: suspend (Connection) -> T): T =
     *     dataSource.connection.use { conn ->
     *         conn.autoCommit = false
     *         try { block(conn).also { conn.commit() } }
     *         catch (e: Exception) { conn.rollback(); throw e }
     *     }
     * ```
     *
     * @param block The suspending function to execute within the transaction
     * @return The result of the block
     */
    suspend fun <T> inTransaction(block: suspend (Txn) -> T): T

    /**
     * Processes a command using a given state-stored system.
     *
     * This method orchestrates the flow within a single transaction:
     * 1. Fetch current state (with metadata) from storage
     * 2. Extract pure domain state and pass to domain system (metadata-free)
     * 3. Domain system produces new state (pure domain logic)
     * 4. Attach command metadata and persist
     *
     * Benefits:
     * - **Atomicity:** The fetch → compute → save sequence is guaranteed to run within a single transaction
     * - **Domain purity:** Domain logic remains unaware of infrastructure concerns
     *
     * @param command The command with metadata to execute
     * @param system The state-stored system to apply (pure domain logic)
     * @return The newly persisted state with metadata
     */
    suspend fun process(
        command: Pair<Command, CommandMetadata>,
        system: StateStoredSystem<Command, State>
    ): Pair<State, StateMetadata> = inTransaction { txn ->
        val currentState: Pair<State, StateMetadata>? = fetchState(command, txn)
        val newState: State = system(command.first, currentState?.first)
        save(Pair(newState, command.second), txn)
    }
}


/**
 * Handles a command by composing domain logic ([ISystem]) and repository operations ([IStateRepository]).
 *
 * This extension function demonstrates **composition through multiple type constraints**:
 * - `SYSTEM : IStateRepository` provides persistence capabilities (fetch, save)
 * - `SYSTEM : ISystem` provides domain logic (decide, evolve, initialState)
 *
 * The composition happens at the type level: any type implementing both interfaces
 * automatically gains the `handle` capability. The function extracts the pure domain
 * system via `inStateStoredSystem()` and passes it to the repository's `process` method.
 *
 * Metadata flows through the infrastructure layer but never enters the domain system,
 * maintaining separation of concerns.
 *
 * @param command The command paired with metadata (e.g., user context, correlation ID)
 * @return The resulting state paired with metadata (e.g., version, timestamp)
 */
suspend fun <SYSTEM, Command, State, Event, CommandMetadata, StateMetadata, Txn> SYSTEM.handle(command: Pair<Command, CommandMetadata>): Pair<State, StateMetadata>
        where SYSTEM : IStateRepository<Command, CommandMetadata, State, StateMetadata, Txn>,
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
 * @param Txn Type of transaction handle (e.g., JDBC Connection, Mongo ClientSession, or Unit for in-memory)
 */
interface IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata, Txn> {

    /**
     * Fetches the current sequence of events with metadata corresponding to a command.
     *
     * @param command The command with metadata identifying which events to fetch
     * @param txn The transaction handle to use for this operation
     * @return A sequence of events paired with their metadata, may be empty
     */
    suspend fun fetchEvents(command: Pair<Command, CommandMetadata>, txn: Txn): Sequence<Pair<InEvent, InEventMetadata>>

    /**
     * Persists a sequence of events with metadata.
     *
     * @param events The sequence of domain events to persist
     * @param commandMetadata The command metadata to attach to persisted events
     * @param txn The transaction handle to use for this operation
     * @return The newly saved sequence of events with their generated metadata
     */
    suspend fun save(
        events: Sequence<OutEvent>,
        commandMetadata: CommandMetadata,
        txn: Txn
    ): Sequence<Pair<OutEvent, OutEventMetadata>>

    /**
     * Executes a block within a transaction, managing the transaction lifecycle (begin, commit, rollback).
     *
     * The locking strategy is left to the implementor:
     * - **Pessimistic:** [fetchEvents] acquires a lock within the transaction (e.g., `SELECT ... FOR UPDATE`), held until commit
     * - **Optimistic:** [fetchEvents] reads a version, [save] checks it hasn't changed (e.g., append with expected version)
     *
     * Example (JDBC):
     * ```kotlin
     * override suspend fun <T> inTransaction(block: suspend (Connection) -> T): T =
     *     dataSource.connection.use { conn ->
     *         conn.autoCommit = false
     *         try { block(conn).also { conn.commit() } }
     *         catch (e: Exception) { conn.rollback(); throw e }
     *     }
     * ```
     *
     * @param block The suspending function to execute within the transaction
     * @return The result of the block
     */
    suspend fun <T> inTransaction(block: suspend (Txn) -> T): T

    /**
     * Processes a command using a given event-sourced system.
     *
     * This method orchestrates the flow within a single transaction:
     * 1. Fetch past events (with metadata) from event store
     * 2. Extract pure domain events and pass to domain system (metadata-free)
     * 3. Domain system produces new events (pure domain logic)
     * 4. Attach command metadata and persist events
     *
     * Benefits:
     * - **Atomicity:** The fetch → compute → save sequence is guaranteed to run within a single transaction
     * - **Domain purity:** Domain logic remains unaware of infrastructure concerns
     *
     * @param command The command with metadata to execute
     * @param system The event-sourced system that produces events from a command and past events
     * @return The newly persisted sequence of events with metadata
     */
    suspend fun process(
        command: Pair<Command, CommandMetadata>,
        system: EventSourcedSystem<Command, InEvent, OutEvent>
    ): Sequence<Pair<OutEvent, OutEventMetadata>> = inTransaction { txn ->
        val pastEvents = fetchEvents(command, txn).map { it.first }
        val newEvents = system(command.first, pastEvents)
        save(newEvents, command.second, txn)
    }
}

/**
 * Handles a command by composing domain logic ([ISystem]) and event repository operations ([IEventRepository]).
 *
 * This extension function demonstrates **composition through multiple type constraints**:
 * - `SYSTEM : IEventRepository` provides event persistence capabilities (fetchEvents, save)
 * - `SYSTEM : ISystem` provides domain logic (decide, evolve, initialState)
 *
 * The composition happens at the type level: any type implementing both interfaces
 * automatically gains the `handle` capability. The function extracts the pure domain
 * system via `inEventSourcedSystem()` and passes it to the repository's `process` method.
 *
 * Metadata flows through the infrastructure layer but never enters the domain system,
 * maintaining separation of concerns.
 *
 * @param command The command paired with metadata (e.g., user context, correlation ID)
 * @return The resulting sequence of events paired with metadata (e.g., sequence numbers, timestamps)
 */
suspend fun <SYSTEM, Command, State, Event, CommandMetadata, EventMetadata, Txn> SYSTEM.handle(command: Pair<Command, CommandMetadata>): Sequence<Pair<Event, EventMetadata>>
        where SYSTEM : IEventRepository<Command, CommandMetadata, Event, EventMetadata, Event, EventMetadata, Txn>,
              SYSTEM : ISystem<Command, State, Event> =
    process(command, inEventSourcedSystem())

/**
 * Handles a command by composing domain logic ([IDynamicSystem]) and event repository operations ([IEventRepository]).
 *
 * This extension function demonstrates **composition through multiple type constraints**:
 * - `SYSTEM : IEventRepository` provides event persistence capabilities (fetchEvents, save)
 * - `SYSTEM : IDynamicSystem` provides domain logic (decide, evolve, initialState)
 *
 * The composition happens at the type level: any type implementing both interfaces
 * automatically gains the `handle` capability. Supports dynamic systems where input and
 * output event types differ (InEvent ≠ OutEvent).
 *
 * The function extracts the pure domain system via `inEventSourcedSystem()` and passes it
 * to the repository's `process` method. Metadata flows through the infrastructure layer
 * but never enters the domain system, maintaining separation of concerns.
 *
 * @param command The command paired with metadata (e.g., user context, correlation ID)
 * @return The resulting sequence of events paired with metadata (e.g., sequence numbers, timestamps)
 */
suspend fun <SYSTEM, Command, State, InEvent, OutEvent, CommandMetadata, InEventMetadata, OutEventMetadata, Txn> SYSTEM.handle(
    command: Pair<Command, CommandMetadata>
): Sequence<Pair<OutEvent, OutEventMetadata>>
        where SYSTEM : IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata, Txn>,
              SYSTEM : IDynamicSystem<Command, State, InEvent, OutEvent> =
    process(command, inEventSourcedSystem())