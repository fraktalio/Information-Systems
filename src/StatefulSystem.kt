package com.fraktalio

// 1  ######################## State Stored ############################
interface IStateRepository<C, S> {
    /**
     * Fetch state
     *
     * @receiver Command of type [C]
     * @return the State of type [S] or null
     */
    suspend fun C.fetchState(): S?

    /**
     * Save state
     *
     * @receiver State of type [S]
     * @return newly saved State of type [S]
     */
    suspend fun S.save(): S
}

// Composing Domain (System) and Infrastructure (IStateRepository) into an Application
interface IStatefulStateStoredSystem<C, S, E> : ISystem<C, S, E>, IStateRepository<C, S>

// Constructor-like function - Create compose Stateful System
fun <C, S, E> StatefulStateStoredSystem(
    system: ISystem<C, S, E>,
    stateRepository: IStateRepository<C, S>
): IStatefulStateStoredSystem<C, S, E> =
    object : IStatefulStateStoredSystem<C, S, E>,
        IStateRepository<C, S> by stateRepository,
        ISystem<C, S, E> by system {}

// The API of the application / HANDLE Command
suspend fun <C, S, E> IStatefulStateStoredSystem<C, S, E>.handle(command: C): S =
    asStateStoredSystem().invoke(command, command.fetchState() ?: initialState())


// 2  ######################## Event Sourced ############################
interface IEventRepository<C, E> {
    /**
     * Fetch events
     *
     * @receiver Command of type [C]
     *
     * @return [Sequence] of Events of type [E]
     */
    suspend fun C.fetchEvents(): Sequence<E>

    /**
     * Save events
     *
     * @receiver [Sequence] of Events of type [E]
     * @return newly saved [Sequence] of Events of type [E]
     */
    suspend fun Sequence<E>.save(): Sequence<E>
}

// Composing Domain (System) and Infrastructure (IEventRepository) into an Application
interface IStatefulEventSourcedSystem<C, S, E> : ISystem<C, S, E>, IEventRepository<C, E>

// Constructor-like function - Create compose Stateful System
fun <C, S, E> StatefulEventSourcedSystem(
    system: ISystem<C, S, E>,
    eventRepository: IEventRepository<C, E>
): IStatefulEventSourcedSystem<C, S, E> =
    object : IStatefulEventSourcedSystem<C, S, E>,
        IEventRepository<C, E> by eventRepository,
        ISystem<C, S, E> by system {}

// The API of the application / HANDLE Command
suspend fun <C, S, E> IStatefulEventSourcedSystem<C, S, E>.handle(command: C): Sequence<E> =
    asEventSourcedSystem().invoke(command, command.fetchEvents())