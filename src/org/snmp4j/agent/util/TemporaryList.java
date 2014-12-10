/*_############################################################################
  _## 
  _##  SNMP4J-Agent - TemporaryList.java  
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

package org.snmp4j.agent.util;

import java.util.LinkedList;
import java.util.Iterator;

/**
 * The <code>TemporaryList</code> implements a list whose items are
 * automatically removed after a predefined timeout. When an item is
 * removed by timeout, listener can be called to handle the remove.
 *
 * @author Frank Fock
 */
public class TemporaryList {

  // Default timeout is 5min.
  public static final int DEFAULT_ITEM_TIMEOUT = 300000;

  // default timeout for entries in 1/1000 seconds.
  private int timeout = DEFAULT_ITEM_TIMEOUT;
  private LinkedList list = new LinkedList();

  public TemporaryList() {
  }

  public TemporaryList(int timeout) {
    this.timeout = timeout;
  }

  public synchronized void add(Object o) {
    long now = System.currentTimeMillis();
    if ((list.size() > 0) &&
        (((TemporaryListItem)list.getFirst()).atMaturity(now))) {
      list.removeFirst();
    }
    if ((list.size() > 0) &&
        (((TemporaryListItem)list.getLast()).atMaturity(now))) {
      list.removeLast();
    }
    list.addFirst(new TemporaryListItem(o));
  }

  public synchronized boolean contains(Object o) {
    for (Iterator it = list.iterator(); it.hasNext(); ) {
      TemporaryListItem item = (TemporaryListItem) it.next();
      if (item.getItem().equals(o)) {
        return true;
      }
    }
    return false;
  }

  public synchronized boolean remove(Object o) {
    long now = System.currentTimeMillis();
    for (Iterator it = list.iterator(); it.hasNext(); ) {
      TemporaryListItem item = (TemporaryListItem)it.next();
      if (item.getItem().equals(o)) {
        it.remove();
        return true;
      }
      else if (item.atMaturity(now)) {
        it.remove();
      }
    }
    return false;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  public int getTimeout() {
    return timeout;
  }

  public Iterator iterator() {
    return new TemporaryListIterator();
  }

  public int size() {
    return list.size();
  }


  public synchronized void clear() {
    list.clear();
  }


  class TemporaryListItem {
    private Object item;
    private long timeOfMaturity;

    public TemporaryListItem(Object item) {
      this.item = item;
      this.timeOfMaturity = System.currentTimeMillis() + timeout;
    }

    public long getTimeOfMaturity() {
      return timeOfMaturity;
    }

    public Object getItem() {
      return item;
    }

    public boolean equals(Object obj) {
      return item.equals(obj);
    }

    public int hashCode() {
      return item.hashCode();
    }

    public boolean atMaturity(long referenceTime) {
      return (referenceTime > timeOfMaturity);
    }
  }

  class TemporaryListIterator implements Iterator {

    private Iterator iterator;

    public TemporaryListIterator() {
      iterator = list.iterator();
    }

    public boolean hasNext() {
      return iterator.hasNext();
    }

    public Object next() {
      return ((TemporaryListItem)iterator.next()).getItem();
    }

    public void remove() {
      iterator.remove();
    }

  }
}
