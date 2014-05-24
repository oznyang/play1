package play.db.jpa;

import play.Invoker.InvocationContext;
import play.Invoker.Suspend;
import play.exceptions.JPAException;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA Support
 */
public class JPA {

    public static String DEFAULT = "default";
    static Map<String, EntityManagerFactory> emfs = new HashMap<String, EntityManagerFactory>();
    private static ThreadLocal<Map<String, JPAContext>> ems = new ThreadLocal<Map<String, JPAContext>>();

    public static class JPAContext {
        public EntityManager entityManager;
        public boolean readonly = true;
    }

    static Map<String, JPAContext> get() {
        Map<String, JPAContext> jpaContexts = ems.get();
        if (jpaContexts == null) {
            throw new JPAException("The JPA context is not initialized. JPA Entity Manager automatically start when one or more classes annotated with the @javax.persistence.Entity annotation are found in the application.");
        }
        return jpaContexts;
    }

    public static JPAContext createContext(boolean readonly) {
        EntityManager em = createEntityManager();
        em.setFlushMode(FlushModeType.COMMIT);
        return createContext(em, readonly);
    }

    public static JPAContext createContext(EntityManager entityManager, boolean readonly) {
        Map<String, JPAContext> jpaContexts = ems.get();
        if (jpaContexts == null) {
            ems.set(jpaContexts = new HashMap<String, JPAContext>());
        }
        JPAContext context = new JPAContext();
        context.entityManager = entityManager;
        context.readonly = readonly;
        jpaContexts.put(DEFAULT, context);
        return context;
    }

    public static void clearContext() {
        ems.remove();
    }

    public static JPAContext getContext() {
        Map<String, JPAContext> jpaContexts = ems.get();
        if (jpaContexts == null) {
            return null;
        }
        return jpaContexts.get(DEFAULT);
    }

    public static void addEntityManagerFactory(String dbName, EntityManagerFactory entityManagerFactory) {
        emfs.put(dbName, entityManagerFactory);
    }

    public static void removeEntityManagerFactory(String dbName) {
        emfs.remove(dbName);
    }

    public static boolean isInitialized() {
        return isInitialized(DEFAULT);
    }

    public static boolean isInitialized(String key) {
        Map<String, JPAContext> jpaContexts = ems.get();
        return jpaContexts != null && jpaContexts.containsKey(key);
    }

    public static boolean isEnabled() {
        return isEnabled(DEFAULT);
    }

    public static boolean isEnabled(String key) {
        return emfs.containsKey(key);
    }

    public static EntityManagerFactory emf() {
        return emfs.get(DEFAULT);
    }

    public static EntityManagerFactory emf(String key) {
        return emfs.get(key);
    }

    public static EntityManager em() {
        return em(DEFAULT);
    }

    public static EntityManager em(String key) {
        return get().get(key).entityManager;
    }

    public static EntityManager createEntityManager() {
        return createEntityManager(DEFAULT);
    }

    public static EntityManager createEntityManager(String key) {
        EntityManagerFactory emf = emfs.get(key);
        if (emf == null) {
            throw new JPAException("The JPA EntityManagerFactory for unit \"" + key + "\" not found.");
        }
        return emf.createEntityManager();
    }

    public static void setRollbackOnly() {
        setRollbackOnly(DEFAULT);
    }

    public static void setRollbackOnly(String key) {
        em(key).getTransaction().setRollbackOnly();
    }

    public static int execute(String query) {
        return execute(DEFAULT, query);
    }

    public static int execute(String key, String query) {
        return em(key).createQuery(query).executeUpdate();
    }

    public static boolean isInsideTransaction() {
        return isInsideTransaction(DEFAULT);
    }

    public static boolean isInsideTransaction(String name) {
        return isInitialized() && em(name).getTransaction() != null;
    }

    public static <T> T withinFilter(play.libs.F.Function0<T> block) throws Throwable {
        InvocationContext ic = InvocationContext.current();
        if (ic.getAnnotation(NoTransaction.class) != null) {
            return block.apply();
        }
        boolean readOnly = false;
        Transactional tx = ic.getAnnotation(Transactional.class);
        if (tx != null) {
            readOnly = tx.readOnly();
        }
        return withTransaction(readOnly, block);
    }

    public static String getDBName(Class clazz) {
        PersistenceUnit pu = (PersistenceUnit) clazz.getAnnotation(PersistenceUnit.class);
        return pu != null ? pu.name() : DEFAULT;
    }

    public static <T> T withTransaction(boolean readonly, play.libs.F.Function0<T> block) throws Throwable {
        if (isEnabled()) {
            boolean needCloseEm = true;
            boolean hasTx = false;
            Map<String, JPAContext> jpaContexts = new HashMap<String, JPAContext>();
            try {
                ems.set(jpaContexts);
                for (Map.Entry<String, EntityManagerFactory> entry : emfs.entrySet()) {
                    EntityManager em = entry.getValue().createEntityManager();
                    em.setFlushMode(FlushModeType.COMMIT);
                    JPAContext context = new JPAContext();
                    context.entityManager = em;
                    context.readonly = readonly;
                    jpaContexts.put(entry.getKey(), context);
                    if (!readonly) {
                        EntityTransaction tx = em.getTransaction();
                        tx.begin();
                        hasTx = true;
                    }
                }

                T result = block.apply();

                boolean rollbackAll = false;
                for (Map.Entry<String, JPAContext> entry : jpaContexts.entrySet()) {
                    EntityTransaction tx = entry.getValue().entityManager.getTransaction();
                    if (tx.isActive() && tx.getRollbackOnly()) {
                        rollbackAll = true;
                    }
                }
                for (JPAContext jpaContext : jpaContexts.values()) {
                    EntityTransaction tx = jpaContext.entityManager.getTransaction();
                    if (tx.isActive()) {
                        if (rollbackAll || readonly) {
                            tx.rollback();
                        } else {
                            tx.commit();
                        }
                    }
                }

                return result;
            } catch (Suspend e) {
                needCloseEm = false;
                throw e;
            } catch (Throwable t) {
                if (hasTx) {
                    for (JPAContext jpaContext : jpaContexts.values()) {
                        EntityTransaction tx = jpaContext.entityManager.getTransaction();
                        try {
                            if (tx.isActive()) {
                                tx.rollback();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                throw t;
            } finally {
                if (needCloseEm) {
                    for (JPAContext jpaContext : jpaContexts.values()) {
                        EntityManager em = jpaContext.entityManager;
                        if (em.isOpen()) {
                            em.close();
                        }
                    }
                }
                ems.remove();
            }
        } else {
            return block.apply();
        }
    }
}
