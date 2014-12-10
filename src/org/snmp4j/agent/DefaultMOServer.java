/*_############################################################################
  _##
  _##  SNMP4J-Agent - DefaultMOServer.java
  _##
  _##  Copyright (C) 2005-2009  Frank Fock (SNMP4J.org)
  _##
  _##  Licensed under the Apache License, Version 2.0 (the "License");
  _##  you may not use this file except in compliance with the License.
  _##  You may obtain a copy of the License at
  _##
  _##      http://www.apache.org/licenses/LICENSE-2.0
  _##
  _##  Unless required by applicable law or agreed to in writing, software
  _##  distributed under the License is distributed on an "AS IS" BASIS,
  _##  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  _##  See the License for the specific language governing permissions and
  _##  limitations under the License.
  _##
  _##########################################################################*/

package org.snmp4j.agent;

import java.util.*;
import java.util.Map.Entry;

import org.snmp4j.log.*;
import org.snmp4j.smi.*;
import org.snmp4j.agent.request.SnmpRequest;
import org.snmp4j.agent.mo.MOTableRowListener;
import org.snmp4j.agent.mo.MOTable;

/**
 * The default MO server implementation uses a sorted map for the managed object
 * registry.
 *
 * @author Frank Fock
 * @version 1.4
 */
public class DefaultMOServer implements MOServer {

  private static final LogAdapter logger =
      LogFactory.getLogger(DefaultMOServer.class);

  private Set contexts;
  private SortedMap registry;
  private Map lockList;
  private Map lookupListener;
  private transient Vector contextListeners;
  private UpdateStrategy updateStrategy;


  public DefaultMOServer() {
    this.registry =
        Collections.synchronizedSortedMap(new TreeMap(new MOScopeComparator()));
    this.contexts = new LinkedHashSet(10);
    this.lockList = new Hashtable(10);
  }


  public ManagedObject lookup(MOQuery query) {
    return lookup(query, false);
  }

  private ManagedObject lookup(MOQuery query, boolean specificRegistrationsOnly) {
    SortedMap scope = registry.tailMap(query);
    Iterator it = scope.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry entry = (Entry) it.next();
      MOScope key = (MOScope) entry.getKey();
      if (!DefaultMOContextScope.isContextMatching(query.getScope(), key)) {
        continue;
      }
      if ((specificRegistrationsOnly) && (((key instanceof MOContextScope) &&
          (((MOContextScope)key).getContext() == null)) ||
          !(key instanceof MOContextScope))) {
        continue;
      }
      Object o = entry.getValue();
      if (o instanceof ManagedObject) {
        ManagedObject mo = (ManagedObject) o;
        MOScope moScope = mo.getScope();
        if (query.getScope().isOverlapping(moScope)) {
          fireQueryEvent(mo, query);
          if (mo instanceof UpdatableManagedObject) {
            checkForUpdate((UpdatableManagedObject)mo, query);
          }
          if (query.matchesQuery(mo)) {
            fireLookupEvent(mo, query);
            return mo;
          }
        }
      }
      else if (logger.isWarnEnabled()) {
        logger.warn("Object looked up is not a ManagedObject: "+o);
      }
    }
    return null;
  }

  /**
   * Checks {@link #updateStrategy} whether the queried managed object needs
   * to be updated. This method is called on behalf of
   * {@link #lookup(MOQuery query)} after {@link #fireQueryEvent} and before
   * {@link #fireLookupEvent} is being called.
   *
   * @param mo
   *    an UpdatableManagedObject instance.
   * @param query
   *    the query that is interested in content of <code>mo</code>.
   * @since 1.2
   */
  protected void checkForUpdate(UpdatableManagedObject mo, MOQuery query) {
    if (updateStrategy != null) {
      if (updateStrategy.isUpdateNeeded(this, mo, query)) {
        if (logger.isDebugEnabled()) {
          logger.debug("Updating UpdatableManagedObject "+mo+
                       " on behalf of query "+query);
        }
        mo.update(query);
      }
    }
  }

  /**
   * Returns the <code>ManagedObject</code> with the specified <code>OID</code>
   * and registered in the supplied context.
   * <p>
   * Note: The query used to lookup the managed object will indicate an intended
   * read-only access for the {@link MOServerLookupEvent}s fired on behalf of
   * this method.
   * @param key
   *    the OID identifying the key (lower bound) of the
   *    <code>ManagedObject</code>.
   * @param context
   *    the optional context to look in. A <code>null</code> value searches
   *    in all contexts.
   * @return
   *    the <code>ManagedObject</code> instance or <code>null</code> if such
   *    an instance does not exists.
   * @since 1.1
   */
  public ManagedObject getManagedObject(OID key, OctetString context) {
    MOContextScope scope =
        new DefaultMOContextScope(context, key, true, key, true);
    return lookup(new DefaultMOQuery(scope));
  }

  /**
   * Returns the value of a particular MIB object instance using the
   * {@link ManagedObjectValueAccess} interface. If a {@link ManagedObject}
   * does not support this interface, its value cannot be returned and
   * <code>null</code> will be returned instead.
   * @param server
   *    the <code>MOServer</code> where to lookup the value.
   * @param context
   *    the optional context to look in. A <code>null</code> value searches
   *    in all contexts.
   * @param key
   *    the OID identifying the variable instance to return.
   * @return
   *    the </code>Variable<code> associated with <code>OID</code> and
   *    <code>context</code> in <code>server</code> or <code>null</code> if
   *    no such variable exists.
   * @since 1.4
   */
  public static Variable getValue(MOServer server, OctetString context,
                                  OID key) {

    MOContextScope scope =
        new DefaultMOContextScope(context, key, true, key, true);
    ManagedObject mo = server.lookup(new DefaultMOQuery(scope));
    if (mo instanceof ManagedObjectValueAccess) {
      return ((ManagedObjectValueAccess)mo).getValue(key);
    }
    return null;
  }

  /**
   * Sets the value of a particular MIB object instance using the
   * {@link ManagedObjectValueAccess} interface. If a {@link ManagedObject}
   * does not support this interface, its value cannot be set and
   * <code>false</code> will be returned.
   * @param server
   *    the <code>MOServer</code> where to lookup the value.
   * @param context
   *    the optional context to look in. A <code>null</code> value searches
   *    in all contexts.
   * @param newValueAndKey
   *    the OID identifying the variable instance to set and its new value.
   * @return
   *    the </code>true<code> if the value has been set successfully,
   *    <code>false</code> otherwise.
   * @since 1.4
   */
  public static boolean setValue(MOServer server, OctetString context,
                                 VariableBinding newValueAndKey) {
    OID key = newValueAndKey.getOid();
    MOContextScope scope =
        new DefaultMOContextScope(context, key, true, key, true);
    ManagedObject mo = server.lookup(new DefaultMOQuery(scope));
    if (mo instanceof ManagedObjectValueAccess) {
      return ((ManagedObjectValueAccess)mo).setValue(newValueAndKey);
    }
    return false;
  }

  protected void fireLookupEvent(ManagedObject mo, MOQuery query) {
    if (lookupListener != null) {
      List l = (List) lookupListener.get(mo);
      if (l != null) {
        MOServerLookupEvent event = new MOServerLookupEvent(this, mo, query);
        // avoid concurrent modification exception
        l = new ArrayList(l);
        for (Iterator it = l.iterator(); it.hasNext(); ) {
          MOServerLookupListener item = (MOServerLookupListener) it.next();
          item.lookupEvent(event);
        }
      }
    }
  }

  protected void fireQueryEvent(ManagedObject mo, MOQuery query) {
    if (lookupListener != null) {
      List l = (List) lookupListener.get(mo);
      if (l != null) {
        MOServerLookupEvent event = new MOServerLookupEvent(this, mo, query);
        // avoid concurrent modification exception
        l = new ArrayList(l);
        for (Iterator it = l.iterator(); it.hasNext(); ) {
          MOServerLookupListener item = (MOServerLookupListener) it.next();
          item.queryEvent(event);
        }
      }
    }
  }

  public OctetString[] getContexts() {
    return (OctetString[]) contexts.toArray(new OctetString[0]);
  }

  public boolean isContextSupported(OctetString context) {
    if ((context == null) || (context.length() == 0)) {
      return true;
    }
    return contexts.contains(context);
  }

  public SortedMap getRegistry() {
    return registry;
  }

  /**
   * Gets the update strategy for {@link UpdatableManagedObject}s. If the
   * strategy is <code>null</code> no updates will be performed on behalf
   * of calls to {@link #lookup}.
   *
   * @return
   *    the current UpdateStrategy instance or <code>null</code> if no
   *    strategy is active.
   * @since 1.2
   * @see #lookup
   */
  public UpdateStrategy getUpdateStrategy() {
    return updateStrategy;
  }

  /**
   * Sets the update strategy for {@link UpdatableManagedObject}s. If the
   * strategy is <code>null</code> no updates will be performed on behalf
   * of calls to {@link #lookup}.
   *
   * @param updateStrategy
   *    the new UpdateStrategy instance or <code>null</code> if no
   *    updates should be performed.
   * @since 1.2
   * @see #lookup
   */
  public void setUpdateStrategy(UpdateStrategy updateStrategy) {
    this.updateStrategy = updateStrategy;
  }

  public void register(ManagedObject mo, OctetString context)
      throws DuplicateRegistrationException
  {
    if (context == null) {
      MOContextScope contextScope =
          new DefaultMOContextScope(null, mo.getScope());
      ManagedObject other = lookup(new DefaultMOQuery(contextScope));
      if (other != null) {
        throw new DuplicateRegistrationException(contextScope,
                                                 other.getScope());
      }
      registry.put(mo.getScope(), mo);
      if (logger.isInfoEnabled()) {
        logger.info("Registered MO "+mo+" in default context with scope "+
                     mo.getScope());
      }
    }
    else {
      DefaultMOContextScope contextScope =
          new DefaultMOContextScope(context, mo.getScope());
      ManagedObject other = lookup(new DefaultMOQuery(contextScope), true);
      if (other != null) {
        throw new DuplicateRegistrationException(contextScope, other.getScope());
      }
      registry.put(contextScope, mo);
      if (logger.isInfoEnabled()) {
        logger.info("Registered MO "+mo+" in context "+context+" with scope "+
                     contextScope);
      }
    }
  }

  public void unregister(ManagedObject mo, OctetString context) {
    MOScope key;
    if (context == null) {
      key = mo.getScope();
    }
    else {
      key = new DefaultMOContextScope(context, mo.getScope());
    }
    Object r = registry.remove(key);
    if (r == null) {
      // OK, may be the upper bound of the scope has been adjusted so we need to
      // check that by iterating
      SortedMap tailMap = registry.tailMap(key);
      for (Iterator it = tailMap.entrySet().iterator(); it.hasNext();) {
        Map.Entry entry = (Entry) it.next();
        MOScope entryKey = (MOScope) entry.getKey();
        if ((entry.getValue().equals(mo)) &&
            (((context != null) &&
              (entryKey instanceof MOContextScope) &&
              (context.equals(((MOContextScope)entryKey).getContext()))) ||
             (context == null))) {
          r = entry.getValue();
          it.remove();
          break;
        }
      }
    }
    if (logger.isInfoEnabled()) {
      if (r != null) {
        logger.info("Removed registration " + r + " for " + mo +
                    " in context '" + context + "' sucessfully");
      }
      else {
        logger.warn("Removing registration failed for " + mo +
                    " in context '" + context + "'");
      }
    }
  }

  public void addContext(OctetString context) {
    contexts.add(context);
  }

  public void removeContext(OctetString context) {
    contexts.remove(context);
  }

  public synchronized boolean lock(Object owner, ManagedObject managedObject) {
    return lock(owner, managedObject, 0);
  }

  public boolean lock(Object owner, ManagedObject managedObject,
                      long timeoutMillis) {
    Lock lock;
    long start = System.currentTimeMillis();
    do {
      lock = (Lock) lockList.get(managedObject);
      if (lock == null) {
        lockList.put(managedObject, new Lock(owner));
        if (logger.isDebugEnabled()) {
          logger.debug("Acquired lock on "+managedObject+ " for "+owner);
        }
        return true;
      }
      else if (lock.getOwner() != owner) {
        try {
          while (lock.getCount() > 0) {
            if (logger.isDebugEnabled()) {
              logger.debug("Waiting for lock on " + managedObject);
            }
            if (timeoutMillis <= 0) {
              wait();
            }
            else {
              wait(Math.max(timeoutMillis -
                            (System.currentTimeMillis() - start), 1));
            }
          }
        }
        catch (InterruptedException ex) {
          logger.warn("Waiting for lock on "+managedObject+
                      " has been interrupted!");
          break;
        }
      }
      else {
        lock.add();
        if (logger.isDebugEnabled()) {
          logger.debug("Added lock on "+managedObject+ " for "+owner);
        }
        return true;
      }
    }
    while ((lock != null) &&
           ((timeoutMillis <= 0) ||
            (System.currentTimeMillis() - start < timeoutMillis)));
    return false;
  }

  public synchronized void unlock(Object owner, ManagedObject managedObject) {
    Lock lock = (Lock) lockList.get(managedObject);
    if (lock == null) {
      return;
    }
    if (lock.getOwner() != owner) {
      if (logger.isDebugEnabled()) {
        logger.debug("Object '" + owner + "' is not owner of lock: " + lock);
      }
      return;
    }
    if (lock.remove()) {
      lockList.remove(managedObject);
      if (logger.isDebugEnabled()) {
        logger.debug("Removed lock on "+managedObject+ " by "+owner);
      }
      notify();
    }
  }

  public Iterator iterator() {
    SortedMap m = getRegistry();
    synchronized (m) {
      SortedMap r = new TreeMap(new MOScopeComparator());
      r.putAll(m);
      return r.entrySet().iterator();
    }
  }

  public synchronized void addLookupListener(MOServerLookupListener listener,
                                             ManagedObject mo) {
    if (lookupListener == null) {
      lookupListener = Collections.synchronizedMap(new HashMap());
    }
    List l = (List) lookupListener.get(mo);
    if (l == null) {
      l = Collections.synchronizedList(new LinkedList());
      lookupListener.put(mo, l);
    }
    l.add(listener);
  }

  public synchronized boolean removeLookupListener(MOServerLookupListener listener,
      ManagedObject mo)
  {
    if (lookupListener != null) {
      List l = (List) lookupListener.get(mo);
      if (l != null) {
        return l.remove(listener);
      }
    }
    return false;
  }

  public synchronized void addContextListener(ContextListener l) {
    if (contextListeners == null) {
      contextListeners = new Vector(2);
    }
    contextListeners.add(l);
  }

  public synchronized void removeContextListener(ContextListener l) {
    if (contextListeners != null) {
      contextListeners.remove(l);
    }
  }

  protected void fireContextChanged(ContextEvent event) {
    if (contextListeners != null) {
      Vector listeners = contextListeners;
      int count = listeners.size();
      for (int i = 0; i < count; i++) {
        ((ContextListener) listeners.elementAt(i)).contextChanged(event);
      }
    }
  }

  public String toString() {
    StringBuffer buf = new StringBuffer(getClass().getName());
    buf.append("[contexts="+contexts);
    buf.append("[keys={");
    for (Iterator it = registry.keySet().iterator(); it.hasNext();) {
      MOScope scope = (MOScope) it.next();
      buf.append(scope.getLowerBound());
      if (scope.isLowerIncluded()) {
        buf.append("+");
      }
      buf.append("-");
      buf.append(scope.getUpperBound());
      if (scope.isUpperIncluded()) {
        buf.append("+");
      }
      if (scope instanceof MOContextScope) {
        buf.append("("+((MOContextScope)scope).getContext()+")");
      }
      if (it.hasNext()) {
        buf.append(",");
      }
    }
    buf.append("}");
    buf.append(",registry="+registry);
    buf.append(",lockList="+lockList);
    buf.append(",lookupListener="+lookupListener);
    buf.append("]");
    return buf.toString();
  }

  public OctetString[] getRegisteredContexts(ManagedObject managedObject) {
    Set contextSet = new HashSet();
    MOQuery query = new DefaultMOQuery(new DefaultMOContextScope(null,
        managedObject.getScope()));
    SortedMap scope = registry.tailMap(query);
    Iterator it = scope.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry entry = (Entry) it.next();
      MOScope key = (MOScope) entry.getKey();
      Object o = entry.getValue();
      if (managedObject.equals(o)) {
        if (key instanceof MOContextScope) {
          contextSet.add(((MOContextScope)key).getContext());
        }
        else {
          contextSet.add(null);
        }
      }
    }
    return (OctetString[])
        contextSet.toArray(new OctetString[contextSet.size()]);
  }

  /**
   * Register a single {@link MOTableRowListener} with all tables in the
   * specified {@link MOServer}. This overall registration can be used,
   * for example, to apply table size limits to all tables in an agent.
   * See {@link org.snmp4j.agent.mo.util.MOTableSizeLimit} for details.
   * <p>
   * Note: The server must not change its registration content while this
   * method is being called, otherwise a
   * {@link java.util.ConcurrentModificationException} might be thrown.
   *
   * @param server
   *    a <code>MOServer</code> instance.
   * @param listener
   *    the <code>MOTableRowListener</code> instance to register.
   * @since 1.4
   */
  public static void registerTableRowListener(MOServer server,
                                              MOTableRowListener listener) {
    for (Iterator it = server.iterator(); it.hasNext(); ) {
      ManagedObject mo = (ManagedObject) ((Entry)it.next()).getValue();
      if (mo instanceof MOTable) {
        ((MOTable)mo).addMOTableRowListener(listener);
      }
    }
  }

  /**
   * Unregister a single {@link MOTableRowListener} with all tables in the
   * specified {@link MOServer}. This overall unregistration can be used,
   * for example, to remove table size limits from all tables in an agent.
   * See {@link org.snmp4j.agent.mo.util.MOTableSizeLimit} for details.
   * <p>
   * Note: The server must not change its registration content while this
   * method is being called, otherwise a
   * {@link java.util.ConcurrentModificationException} might be thrown.
   *
   * @param server
   *    a <code>MOServer</code> instance.
   * @param listener
   *    the <code>MOTableRowListener</code> instance to unregister.
   * @since 1.4
   */
  public static void unregisterTableRowListener(MOServer server,
                                                MOTableRowListener listener) {
    for (Iterator it = server.iterator(); it.hasNext(); ) {
      ManagedObject mo = (ManagedObject) ((Entry)it.next()).getValue();
      if (mo instanceof MOTable) {
        ((MOTable)mo).removeMOTableRowListener(listener);
      }
    }
  }

  static class Lock {
    private Object owner;
    private long creationTime;
    private int count = 0;

    private Lock() {
      this.creationTime = System.currentTimeMillis();
      this.count = 0;
    }

    Lock(Object owner) {
      this();
      this.owner = owner;
    }

    public long getCreationTime() {
      return creationTime;
    }

    public int getCount() {
      return count;
    }

    public synchronized void add() {
      count++;
    }

    public synchronized boolean remove() {
      return (--count) <= 0;
    }

    public Object getOwner() {
      return owner;
    }
  }

}
