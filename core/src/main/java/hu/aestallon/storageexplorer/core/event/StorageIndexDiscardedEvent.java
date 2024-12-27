package hu.aestallon.storageexplorer.core.event;

import hu.aestallon.storageexplorer.core.model.instance.StorageInstance;

/**
 * The user wishes to discard a StorageIndex, relinquishing all resources allocated for it.
 *
 * <p>
 * This service will invoke all injected views to respond, by dropping the affected tree node; by
 * clearing, closing and dropping references of the graph view, by closing inspector views.
 *
 * <p>
 * View layer factories and domain services must listen to this event separately, and responding
 * with dropping any and all references to StorageEntries of the discarded {@code StorageIndex}, the
 * index itself, its corresponding application context, and so on.
 *
 * <p>
 * References shouldn't be set to {@code null} manually! That just hampers the garbage collector in
 * its efforts to determine whether an object is actually reachable or not.
 */
public record StorageIndexDiscardedEvent(StorageInstance storageInstance) {}
