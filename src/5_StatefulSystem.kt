package com.fraktalio

// ###############################################
//                  Transactional
// ###############################################

/**
 * Represents a resource that can execute a block of work within a single transaction,
 * managing the transaction lifecycle (begin, commit, rollback).
 *
 * Shared by [IStateRepository] and [IEventRepository] - both need identical transaction
 * lifecycle management, only the persisted shape (state vs. events) differs.
 *
 * The locking strategy is left to the implementor:
 * - **Pessimistic:** the fetch operation acquires a lock within the transaction (e.g., `SELECT ... FOR UPDATE`), held until commit
 * - **Optimistic:** the fetch operation reads a version, the save operation checks it hasn't changed (e.g., `UPDATE ... WHERE version = ?`)
 *
 * Example (JDBC):
 * ```kotlin
 * override suspend fun <T> executeInTransaction(block: suspend (Connection) -> T): T =
 *     dataSource.connection.use { conn ->
 *         conn.autoCommit = false
 *         try { block(conn).also { conn.commit() } }
 *         catch (e: Exception) { conn.rollback(); throw e }
 *     }
 * ```
 *
 * Example (Spring Data R2DBC, `Txn = Unit` since the connection is propagated implicitly
 * via Reactor's `Context` rather than passed around explicitly):
 * ```kotlin
 * override suspend fun <T> executeInTransaction(block: suspend (Unit) -> T): T =
 *     txOperator.executeAndAwait { block(Unit) }
 * ```
 * `executeAndAwait` (from `org.springframework.transaction.reactive`) is the coroutine bridge for
 * `TransactionalOperator` - no manual begin/commit/rollback or connection handle needed, since the
 * reactive transaction manager coordinates through Reactor `Context` propagation.
 *
 * @param Txn Type of transaction handle (e.g., JDBC Connection, Mongo ClientSession, or Unit for in-memory)
 */
interface ITransactional<Txn> {

    /**
     * Executes a block within a transaction.
     *
     * @param block The suspending function to execute within the transaction
     * @return The result of the block
     */
    suspend fun <T> executeInTransaction(block: suspend (Txn) -> T): T
}

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
interface IStateRepository<Command, CommandMetadata, State, StateMetadata, Txn> : ITransactional<Txn> {

    /**
     * Fetches the current state with its metadata corresponding to a command.
     *
     * @param command The command with metadata identifying which state to fetch
     * @param txn The transaction handle to use for this operation, supplied as a context parameter
     * @return The current state with metadata if it exists, otherwise null
     */
    context(txn: Txn)
    suspend fun fetchState(command: Pair<Command, CommandMetadata>): Pair<State, StateMetadata>?

    /**
     * Persists a new state with metadata.
     *
     * @param state The state paired with command metadata to persist
     * @param txn The transaction handle to use for this operation, supplied as a context parameter
     * @return The saved state with its generated metadata (e.g., new version, correlation ID)
     */
    context(txn: Txn)
    suspend fun save(state: Pair<State, CommandMetadata>): Pair<State, StateMetadata>

    /**
     * Handles a command using a given state-stored system.
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
    context(system: StateStoredSystem<Command, State>)
    suspend fun handle(
        command: Pair<Command, CommandMetadata>
    ): Pair<State, StateMetadata> = executeInTransaction { txn ->
        context(txn) {
            val currentState: Pair<State, StateMetadata>? = fetchState(command)
            val newState: State = system(command.first, currentState?.first)
            save(Pair(newState, command.second))
        }
    }
}


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
 *
 * Full example (Spring Data R2DBC, `Txn = Unit` since the connection is propagated implicitly
 * via Reactor's `Context` rather than passed around explicitly - see [ITransactional] for the
 * general shape of `executeInTransaction`):
 * ```kotlin
 * class R2dbcEventRepository(
 *     private val client: DatabaseClient,
 *     private val txOperator: TransactionalOperator
 * ) : IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata, Unit> {
 *
 *     override suspend fun <T> executeInTransaction(block: suspend (Unit) -> T): T =
 *         txOperator.executeAndAwait { block(Unit) }
 *
 *     context(txn: Unit)
 *     override suspend fun fetchEvents(command: Pair<Command, CommandMetadata>): List<Pair<InEvent, InEventMetadata>> =
 *         client.sql("SELECT * FROM events WHERE stream_id = :id ORDER BY sequence_number")
 *             .bind("id", command.first)
 *             .map { row, _ -> row.toInEvent() }
 *             .flow()
 *             .toList()
 *
 *     context(txn: Unit)
 *     override suspend fun save(
 *         events: List<OutEvent>,
 *         commandMetadata: CommandMetadata
 *     ): List<Pair<OutEvent, OutEventMetadata>> =
 *         events.map { event ->
 *             client.sql("INSERT INTO events (stream_id, payload, ...) VALUES (:id, :payload, ...)")
 *                 .bind("id", event.streamId)
 *                 .bind("payload", event.toPayload())
 *                 .map { row, _ -> event to row.toOutEventMetadata() }
 *                 .awaitOne()
 *         }
 * }
 * ```
 * `executeAndAwait` (from `org.springframework.transaction.reactive`) is the coroutine bridge for
 * `TransactionalOperator` - no manual begin/commit/rollback or connection handle needed, since the
 * reactive transaction manager and `DatabaseClient` coordinate through Reactor `Context` propagation.
 */
interface IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata, Txn> :
    ITransactional<Txn> {

    /**
     * Fetches the current sequence of events with metadata corresponding to a command.
     *
     * @param command The command with metadata identifying which events to fetch
     * @param txn The transaction handle to use for this operation, supplied as a context parameter
     * @return A sequence of events paired with their metadata, may be empty
     */
    context(txn: Txn)
    suspend fun fetchEvents(command: Pair<Command, CommandMetadata>): List<Pair<InEvent, InEventMetadata>>

    /**
     * Persists a sequence of events with metadata.
     *
     * @param events The sequence of domain events to persist
     * @param commandMetadata The command metadata to attach to persisted events
     * @param txn The transaction handle to use for this operation, supplied as a context parameter
     * @return The newly saved sequence of events with their generated metadata
     */
    context(txn: Txn)
    suspend fun save(
        events: List<OutEvent>,
        commandMetadata: CommandMetadata
    ): List<Pair<OutEvent, OutEventMetadata>>

    /**
     * Handles a command using a given event-sourced system.
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
    context(system: EventSourcedSystem<Command, InEvent, OutEvent>)
    suspend fun handle(
        command: Pair<Command, CommandMetadata>
    ): List<Pair<OutEvent, OutEventMetadata>> = executeInTransaction { txn ->
        context(txn) {
            val pastEvents = fetchEvents(command).map { it.first }
            val newEvents = system(command.first, pastEvents)
            save(newEvents, command.second)
        }
    }
}
