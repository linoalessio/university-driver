package de.lino.thma.domain;

import com.google.common.collect.Maps;
import de.lino.database.DatabaseRepository;
import de.lino.database.DatabaseRepositoryRegistry;
import de.lino.database.configuration.Credentials;
import de.lino.database.json.JsonDocument;
import de.lino.database.provider.DatabaseProvider;
import de.lino.database.provider.DatabaseSection;
import de.lino.database.provider.DatabaseType;
import de.lino.database.provider.entity.DatabaseEntry;
import de.lino.thma.utility.Constraints;
import de.lino.thma.utility.Serialized;
import de.lino.thma.utility.task.MultiTaskingFactory;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central, in-memory registry of the application's domain {@link Serialized entities},
 * grouped by {@link EntityType}, and owner of the SQLite {@link DatabaseProvider} they
 * are persisted through.
 *
 * <p>This class is a singleton; obtain the shared instance via {@link #getInstance()}.
 */
@Getter
public class EntityFactory {

    /**
     * Registered entities, grouped by {@link EntityType}.
     *
     * <p>Each per-type list is a {@link CopyOnWriteArrayList}: entities are looked up
     * via {@link #getEntities(EntityType)} and {@link #findEntity(EntityType, Object)}
     * far more often than they are registered or removed, and a copy-on-write list
     * lets those lookups proceed without any locking or contention, at the cost of
     * copying the backing array on writes.
     */
    private final Map<EntityType, List<Serialized>> entities;

    /**
     * Credentials used to connect to the local SQLite database, read from
     * {@code config/credentials.json} against the {@code config/database} directory.
     */
    private final Credentials credentials;

    /**
     * Provider used to persist and load entities from the local SQLite database.
     */
    private final DatabaseProvider databaseProvider;

    /**
     * Constructs the entity registry and registers the local SQLite
     * {@link DatabaseProvider} that backs it. Reserved for {@link #getInstance()},
     * since registering the same provider more than once is not a supported use case.
     *
     * <p>{@link Credentials}'s own constructor already creates
     * {@link Constraints#CONFIGURATION_PATH} and writes a default {@code credentials.json}
     * the first time it doesn't exist, and registering the {@link DatabaseProvider} below
     * already creates its {@code database} directory the same way - so on a completely
     * fresh install, the only thing missing afterward is each {@link EntityType}'s own
     * database section directory, which is otherwise only created lazily on that type's
     * first {@link #syncToDatabase(EntityType, Serialized...)}. Creating every section up
     * front here means a fresh checkout never needs any manual directory setup at all.
     */
    private EntityFactory() {

        new DatabaseRepositoryRegistry(false);
        this.entities = Maps.newConcurrentMap();

        this.credentials = new Credentials(
                Constraints.CONFIGURATION_PATH.resolve("credentials.json")
                , Constraints.CONFIGURATION_PATH.resolve("database")
        );

        this.databaseProvider = DatabaseRepository.getInstance().registerDatabaseProvider(0, DatabaseType.JSON, this.credentials);

        for (final EntityType type : EntityType.values()) this.databaseProvider.createSection(type.getDatabaseSectionTag());

    }

    /**
     * Registers one or more entities under the given {@link EntityType}, making them
     * visible to {@link #getEntities(EntityType)} and {@link #findEntity(EntityType, Object)}.
     *
     * <p>All of {@code entities} are added in a single bulk operation rather than one
     * at a time, since each write to the underlying {@link CopyOnWriteArrayList}
     * copies its backing array.
     *
     * @param type the type to register {@code entities} under
     * @param entities the entities to register
     * @throws NullPointerException if {@code type}, {@code entities}, or any element of {@code entities} is {@code null}
     */
    public void registerEntitiesInCache(final EntityType type, final Serialized... entities) {

        Objects.requireNonNull(entities, "@EntityFactory.registerEntitiesInCache: Entities cannot be null");
        Arrays.stream(entities).forEach(entity -> Objects.requireNonNull(entity, "@EntityFactory.registerEntitiesInCache: Entity cannot be null"));

        this.entities
                .computeIfAbsent(
                        Objects.requireNonNull(type, "@EntityFactory.registerEntitiesInCache: EntityType cannot be null"),
                        key -> new CopyOnWriteArrayList<>()
                )
                .addAll(Arrays.asList(entities));

    }

    /**
     * Removes one or more previously {@link #registerEntitiesInCache(EntityType, Serialized...)
     * registered} entities.
     *
     * <p>All of {@code entities} are removed in a single bulk operation rather than
     * one at a time, since each write to the underlying {@link CopyOnWriteArrayList}
     * copies its backing array.
     *
     * @param type the type {@code entities} were registered under
     * @param entities the entities to remove
     * @return {@code true} if at least one of {@code entities} was registered under {@code type} and has been removed, {@code false} otherwise
     * @throws NullPointerException if {@code type}, {@code entities}, or any element of {@code entities} is {@code null}
     */
    public boolean removeEntitiesFromCache(final EntityType type, final Serialized... entities) {

        Objects.requireNonNull(entities, "@EntityFactory.removeEntitiesFromCache: Entities cannot be null");
        Arrays.stream(entities).forEach(entity -> Objects.requireNonNull(entity, "@EntityFactory.removeEntitiesFromCache: Entity cannot be null"));

        final List<Serialized> registered = this.entities.get(
                Objects.requireNonNull(type, "@EntityFactory.removeEntitiesFromCache: EntityType cannot be null")
        );

        return registered != null && registered.removeAll(Arrays.asList(entities));

    }

    /**
     * Returns every entity registered under the given {@link EntityType}, in
     * registration order.
     *
     * @param type the type to look up
     * @param <T> the expected concrete type of the registered entities, inferred from the caller
     * @return an unmodifiable view of the registered entities, or an empty list if none are registered
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <T extends Serialized> List<T> getEntities(final EntityType type) {

        final List<Serialized> registered = this.entities.get(
                Objects.requireNonNull(type, "@EntityFactory.getEntities: EntityType cannot be null")
        );

        return registered == null ? List.of() : uncheckedCast(Collections.unmodifiableList(registered));

    }

    /**
     * Finds the entity registered under the given {@link EntityType} whose
     * {@link Serialized#hasKey(String)} matches {@code key}, e.g. by primary key or
     * another unique attribute.
     *
     * @param type the type to search
     * @param key the key to look for, such as a primary key
     * @param <T> the expected concrete type of the matching entity, inferred from the caller
     * @return the matching entity, or an empty {@link Optional} if none is registered under {@code type} with that key
     * @throws NullPointerException if {@code type} or {@code key} is {@code null}
     */
    public <T extends Serialized> Optional<T> findEntity(final EntityType type, final Object key) {

        Objects.requireNonNull(key, "@EntityFactory.findEntity: key cannot be null");

        return this.getEntities(type).stream()
                .filter(entity -> entity.hasKey(key.toString()))
                .findFirst()
                .map(EntityFactory::uncheckedCast);

    }

    /**
     * Persists one or more entities to the local SQLite database as a whole, storing
     * each one's full state as a single {@link JsonDocument}.
     *
     * <p>Each entity is written under its {@link Serialized#primaryKey()} in the
     * database section named after {@code type}, alongside its concrete class name so
     * {@link #syncFromDatabase(EntityType)} can later reconstruct the exact same type.
     * Whether an entity is a new insert or an update of an existing row is resolved
     * per entity.
     *
     * <p>Every entity is synced through {@link MultiTaskingFactory#getInstance()} on
     * its own virtual thread, so writing several entities does not block on their
     * database round trips one after another.
     *
     * @param type the type to store {@code entities} under
     * @param entities the entities to persist
     * @throws NullPointerException if {@code type}, {@code entities}, or any element of {@code entities} is {@code null}
     */
    public void syncToDatabase(final EntityType type, final Serialized... entities) {

        Objects.requireNonNull(entities, "@EntityFactory.syncToDatabase: Entities cannot be null");
        Objects.requireNonNull(type, "@EntityFactory.syncToDatabase: EntityType cannot be null");

        final DatabaseSection section = this.databaseProvider.createSection(type.getDatabaseSectionTag());
        final MultiTaskingFactory multiTasking = MultiTaskingFactory.getInstance();

        final List<CompletableFuture<?>> writes = new ArrayList<>();

        for (final Serialized entity : entities) {

            Objects.requireNonNull(entity, "@EntityFactory.syncToDatabase: Entity cannot be null");

            final DatabaseEntry entry = new DatabaseEntry(
                    entity.primaryKey(),
                    new JsonDocument().append("data", entity)
            );

            writes.add(multiTasking.runAsync(() -> {
                if (section.exists(entry.getId())) section.update(entry);
                else section.insert(entry);
            }));

        }

        CompletableFuture.allOf(writes.toArray(CompletableFuture<?>[]::new)).join();

    }

    /**
     * Deletes one or more previously {@link #syncToDatabase(EntityType, Serialized...)
     * persisted} entities from the local SQLite database, by {@link Serialized#primaryKey()}.
     * Entities never persisted in the first place are silently skipped.
     *
     * <p>Every entity is deleted through {@link MultiTaskingFactory#getInstance()} on
     * its own virtual thread, for the same reason {@link #syncToDatabase(EntityType, Serialized...)}
     * does.
     *
     * <p>This only removes {@code entities} from the database; callers must also call
     * {@link #removeEntitiesFromCache(EntityType, Serialized...)} to remove them from
     * the in-memory cache.
     *
     * @param type the type {@code entities} were persisted under
     * @param entities the entities to delete
     * @throws NullPointerException if {@code type}, {@code entities}, or any element of {@code entities} is {@code null}
     */
    public void deleteFromDatabase(final EntityType type, final Serialized... entities) {

        Objects.requireNonNull(entities, "@EntityFactory.deleteFromDatabase: Entities cannot be null");
        Objects.requireNonNull(type, "@EntityFactory.deleteFromDatabase: EntityType cannot be null");

        final DatabaseSection section = this.databaseProvider.createSection(type.getDatabaseSectionTag());
        final MultiTaskingFactory multiTasking = MultiTaskingFactory.getInstance();

        final List<CompletableFuture<?>> deletes = new ArrayList<>();

        for (final Serialized entity : entities) {

            Objects.requireNonNull(entity, "@EntityFactory.deleteFromDatabase: Entity cannot be null");

            deletes.add(multiTasking.runAsync(() -> {
                if (section.exists(entity.primaryKey())) section.delete(entity.primaryKey());
            }));

        }

        CompletableFuture.allOf(deletes.toArray(CompletableFuture<?>[]::new)).join();

    }

    /**
     * Deletes a previously {@link #syncToDatabase(EntityType, Serialized...) persisted}
     * entity from the local SQLite database by an explicit primary key, rather than one
     * derived from a live entity's current {@link Serialized#primaryKey()} - needed when
     * an entity's own primary key is itself mutable (e.g. {@link de.lino.thma.domain.entity.semester.Semester#getName()}):
     * once renamed in memory, the entity's own {@code primaryKey()} already reflects the
     * new name, so the stale row under its old key must be deleted by that old key
     * explicitly, or it would be left behind as an orphaned duplicate. A key never
     * persisted in the first place is silently skipped.
     *
     * @param type the type the entity was persisted under
     * @param key the entity's old primary key to delete
     * @throws NullPointerException if {@code type} or {@code key} is {@code null}
     */
    public void deleteFromDatabase(final EntityType type, final String key) {

        Objects.requireNonNull(type, "@EntityFactory.deleteFromDatabase: EntityType cannot be null");
        Objects.requireNonNull(key, "@EntityFactory.deleteFromDatabase: key cannot be null");

        final DatabaseSection section = this.databaseProvider.createSection(type.getDatabaseSectionTag());
        if (section.exists(key)) section.delete(key);

    }

    /**
     * Persists every currently registered entity to the local SQLite database, across
     * every {@link EntityType}, using only what is already in the in-memory cache
     * (see {@link #getEntities(EntityType)}) - no entities need to be passed in.
     *
     * <p>Every {@link EntityType} is synced concurrently, on top of the per-entity
     * concurrency {@link #syncToDatabase(EntityType, Serialized...)} already provides
     * within each type.
     *
     * @see #syncToDatabase(EntityType, Serialized...)
     */
    public void syncToDatabase() {

        final MultiTaskingFactory multiTasking = MultiTaskingFactory.getInstance();
        final List<CompletableFuture<?>> syncs = new ArrayList<>();

        for (final EntityType type : EntityType.values()) {
            syncs.add(multiTasking.runAsync(() -> this.syncToDatabase(type, this.getEntities(type).toArray(Serialized[]::new))));
        }

        CompletableFuture.allOf(syncs.toArray(CompletableFuture<?>[]::new)).join();

    }

    /**
     * Replaces the in-memory entities registered under the given {@link EntityType}
     * with the entities currently stored for it in the local SQLite database,
     * reconstructing each one's exact original class from the class name stored
     * alongside it by {@link #syncToDatabase(EntityType, Serialized...)}.
     *
     * <p>The existing per-type cache is cleared and refilled entry by entry, rather
     * than being replaced with a freshly assembled list.
     *
     * @param type the type to reload
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalStateException if a stored entity's class can no longer be found
     */
    public void syncFromDatabase(final EntityType type) {

        Objects.requireNonNull(type, "@EntityFactory.syncFromDatabase: EntityType cannot be null");

        final List<Serialized> cache = this.entities.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>());
        cache.clear();

        final Optional<DatabaseSection> section = this.databaseProvider.getSection(type.getDatabaseSectionTag());
        if (section.isEmpty()) return;

        for (final DatabaseEntry entry : section.get().getEntries()) {

            final Serialized loaded = uncheckedCast(entry.getDocument().get("data", type.getEntityClass()));
            if (loaded == null) continue;

            cache.add(loaded);

        }

        this.entities.put(type, cache);

    }

    /**
     * Replaces the in-memory entities registered under every {@link EntityType} with
     * whatever is currently stored for each one in the local SQLite database - no
     * type needs to be passed in.
     *
     * <p>Every {@link EntityType} is reloaded concurrently.
     *
     * @throws IllegalStateException if a stored entity's class can no longer be found
     * @see #syncFromDatabase(EntityType)
     */
    public void syncFromDatabase() {

        final MultiTaskingFactory multiTasking = MultiTaskingFactory.getInstance();
        final List<CompletableFuture<?>> syncs = new ArrayList<>();

        for (final EntityType type : EntityType.values()) {
            syncs.add(multiTasking.runAsync(() -> this.syncFromDatabase(type)));
        }

        CompletableFuture.allOf(syncs.toArray(CompletableFuture<?>[]::new)).join();

    }

    /**
     * Gets the shared instance of {@link EntityFactory}.
     *
     * @return the shared {@link EntityFactory} instance
     */
    public static EntityFactory getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Holder for the lazily-initialized singleton instance.
     *
     * <p>Relies on the JVM's class-initialization guarantees to provide thread-safe,
     * lazy initialization without {@code synchronized} or {@code volatile} on the read
     * path (the initialization-on-demand holder idiom).
     */
    private static final class InstanceHolder {
        private static final EntityFactory INSTANCE = new EntityFactory();
    }

    /**
     * Casts {@code object} to {@code T}, unchecked.
     *
     * <p>{@link #getEntities(EntityType)} and {@link #findEntity(EntityType, Object)}
     * infer {@code T} purely from the caller's own assignment context, e.g.
     * {@code List<Exam> exams = entityFactory.getEntities(EntityType.EXAMS);} - callers
     * are trusted to only ever request the concrete type actually registered under a
     * given {@link EntityType}. Since the JVM erases generic type parameters, that
     * trust cannot be verified here; requiring a {@link Class} token to verify it would
     * mean giving up the plain {@code (type)} / {@code (type, key)} call shape these
     * methods are meant to offer. A mismatched {@code T} surfaces as a
     * {@link ClassCastException} at the caller's own assignment, not inside this class.
     * Centralizing the cast here, instead of repeating an unannotated one in each
     * method, keeps every place that trust is extended explicit and in one spot.
     *
     * @param object the value to cast
     * @param <T> the target type
     * @return {@code object}, cast to {@code T}
     */
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(final Object object) {
        return (T) object;
    }

}
