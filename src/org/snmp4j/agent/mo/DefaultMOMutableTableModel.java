/*_############################################################################
  _## 
  _##  SNMP4J-Agent - DefaultMOMutableTableModel.java  
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



package org.snmp4j.agent.mo;

import java.util.*;

import org.snmp4j.smi.*;

public class DefaultMOMutableTableModel extends DefaultMOTableModel
    implements MOMutableTableModel
{

  protected MOTableRowFactory rowFactory;
  private transient Vector moTableModelListeners;

  public MOTableRowFactory getRowFactory() {
    return rowFactory;
  }

  /**
   * Returns a lexicographic ordered list of the rows in the specified index
   * range.
   * @param lowerBound
   *    the lower bound index (inclusive) for the rows in the returned list.
   * @param upperBoundEx
   *    the upper bound index (exclusive) for the rows in the returned list.
   * @return
   *    the possibly empty lexicographically ordered <code>List</code>
   *    of rows of this table model in the specified index range. Modifications
   *    to the list will not affect the underlying table model, although
   *    modifications to the row elements will.
   */
  public synchronized List getRows(OID lowerBound, OID upperBoundEx) {
    List view = new ArrayList(getView(lowerBound, upperBoundEx).values());
    return view;
  }

  /**
   * Returns a lexicographic ordered list of the rows in the specified index
   * range that match the supplied filter.
   * @param lowerBound
   *    the lower bound index (inclusive) for the rows in the returned list.
   * @param upperBoundEx
   *    the upper bound index (exclusive) for the rows in the returned list.
   * @param filter
   *    the filter to exclude rows in the range from the returned
   * @return
   *    the possibly empty lexicographically ordered <code>List</code>
   *    of rows of this table model in the specified index range. Modifications
   *    to the list will not affect the underlying table model, although
   *    modifications to the row elements will.
   */
  public synchronized List getRows(OID lowerBound, OID upperBoundEx,
                                   MOTableRowFilter filter) {
    LinkedList result = new LinkedList();
    SortedMap view = getView(lowerBound, upperBoundEx);
    for (Iterator it = view.values().iterator();
         it.hasNext(); ) {
      MOTableRow row = (MOTableRow) it.next();
      if (filter.passesFilter(row)) {
        result.add(row);
      }
    }
    return result;
  }

  private SortedMap getView(OID lowerBound, OID upperBoundEx) {
    SortedMap view;
    if ((lowerBound == null) && (upperBoundEx == null)) {
      view = rows;
    }
    else if (lowerBound == null) {
      view = rows.headMap(upperBoundEx);
    }
    else if (upperBoundEx == null) {
      view = rows.tailMap(lowerBound);
    }
    else {
      view = rows.subMap(lowerBound, upperBoundEx);
    }
    return view;
  }

  public synchronized MOTableRow removeRow(OID index) {
    MOTableRow row = (MOTableRow) rows.remove(index);
    if ((row != null) && (moTableModelListeners != null)) {
      MOTableModelEvent event =
         new MOTableModelEvent(this, MOTableModelEvent.ROW_REMOVED, row);
      fireTableModelChanged(event);
    }
    return row;
  }

  public synchronized void removeRows(OID lowerBoundIncl,
                                      OID upperBoundExcl) {
    Map m = (lowerBoundIncl == null) ? rows : rows.tailMap(lowerBoundIncl);
    for (Iterator it = m.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry item = (Map.Entry) it.next();
      if (upperBoundExcl == null ||
          upperBoundExcl.compareTo(item.getKey()) > 0) {
        if (moTableModelListeners != null) {
          MOTableModelEvent event =
             new MOTableModelEvent(this, MOTableModelEvent.ROW_REMOVED,
                                   (MOTableRow)item.getValue());
          fireTableModelChanged(event);
        }
        it.remove();
      }
      else {
        break;
      }
    }
  }

  public synchronized void clear() {
    fireTableModelChanged(new MOTableModelEvent(this,
                                                MOTableModelEvent.TABLE_CLEAR));
    rows.clear();
  }

  /**
   * Remove all rows that do not match the given filter criteria
   * from the model.
   * @param filter
   *    the <code>MOTableRowFilter</code> that filters out the rows to
   *    delete.
   */
  public synchronized void clear(MOTableRowFilter filter) {
    for (Iterator it = rows.values().iterator(); it.hasNext();) {
      MOTableRow row = (MOTableRow) it.next();
      if (!filter.passesFilter(row)) {
        if (moTableModelListeners != null) {
          MOTableModelEvent event =
             new MOTableModelEvent(this, MOTableModelEvent.ROW_REMOVED, row);
          fireTableModelChanged(event);
        }
        it.remove();
      }
    }
  }

  /**
   * Returns an iterator over all rows in this table that pass the
   * given filter. If the table might be modified while the iterator
   * is used, it is recommended to synchronize on this model while
   * iterating.
   * @param filter
   *    a MOTableRowFilter instance that defines the rows to return.
   * @return
   *    an Iterator.
   */
  public synchronized Iterator iterator(MOTableRowFilter filter) {
    return new FilteredRowIterator(filter);
  }

  /**
   * Create a new row and return it. The new row will not be added to the
   * table. To add it to the model use the {@link #addRow} method.
   * If this mutable table does not support row creation, it should
   * throw an {@link UnsupportedOperationException}.
   * @param index
   *    the index OID for the new row.
   * @param values
   *    the values to be contained in the new row.
   * @return
   *    the created <code>MOTableRow</code>.
   * @throws java.lang.UnsupportedOperationException
   *    if the specified row cannot be created.
   */
  public MOTableRow createRow(OID index, Variable[] values)
      throws UnsupportedOperationException
  {
    if (rowFactory == null) {
      throw new UnsupportedOperationException("No row factory");
    }
    return rowFactory.createRow(index, values);
  }

  public void setRowFactory(MOTableRowFactory rowFactory) {
    this.rowFactory = rowFactory;
  }

  public void setColumnCount(int columnCount) {
    this.columnCount = columnCount;
  }

  public void freeRow(MOTableRow row) {
    if (rowFactory != null) {
      rowFactory.freeRow(row);
    }
  }

  public synchronized void addMOTableModelListener(MOTableModelListener l) {
    if (moTableModelListeners == null) {
      moTableModelListeners = new Vector(2);
    }
    moTableModelListeners.add(l);
  }

  public synchronized void removeMOTableModelListener(MOTableModelListener l) {
    if (moTableModelListeners != null) {
      moTableModelListeners.remove(l);
    }
  }

  protected void fireTableModelChanged(MOTableModelEvent event) {
    if (moTableModelListeners != null) {
      Vector listeners = moTableModelListeners;
      int count = listeners.size();
      for (int i = 0; i < count; i++) {
        ((MOTableModelListener) listeners.get(i)).tableModelChanged(event);
      }
    }
  }

  public class FilteredRowIterator implements Iterator {

    private Iterator iterator;
    private MOTableRowFilter filter;
    private MOTableRow next;

    FilteredRowIterator(MOTableRowFilter filter) {
      this.filter = filter;
      this.iterator = iterator();
    }

    public void remove() {
      iterator.remove();
    }

    public boolean hasNext() {
      if (next != null) {
        return true;
      }
      findNext();
      return (next != null);
    }

    private void findNext() {
      while (iterator.hasNext()) {
        next = (MOTableRow) iterator.next();
        if (filter.passesFilter(next)) {
          break;
        }
        else {
          next = null;
        }
      }
    }

    public Object next() {
      if (next == null) {
        findNext();
      }
      if (next != null) {
        Object retval = next;
        next = null;
        return retval;
      }
      throw new NoSuchElementException();
    }

  }

  public MOTableRow addRow(MOTableRow row) {
    MOTableRow newRow = super.addRow(row);
    if (moTableModelListeners != null) {
      MOTableModelEvent event =
         new MOTableModelEvent(this, MOTableModelEvent.ROW_ADDED, row);
      fireTableModelChanged(event);
    }
    return newRow;
  }

}
