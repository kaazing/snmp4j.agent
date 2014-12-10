/*_############################################################################
  _## 
  _##  SNMP4J-Agent - MOScopeComparator.java  
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

import java.util.Comparator;
import org.snmp4j.smi.OctetString;

/**
 * The <code>MOScopeComparator</code> compares two scopes with each other or
 * it compares a scope and a {@link MOQuery} with each other.
 * <p>
 * Two scopes are compared by their context (if both are {@link MOContextScope}
 * instances) first and then by their lower bound.
 * <p>
 * A scope is compared with a query by comparing the scope with the queries
 * scope and then if both are deemed to be equal, the upper bound of the scope
 * is checked. If it is unbounded (upper bound is <code>null</code), then
 * the scoped is deemed to be greater than the query. Otherwise, the upper bound
 * of the scope is compared with the lower bound of the query. Scope and query
 * are deemd to be equal if both bounds are equal and are both included.
 * Otherwise the scope is deemed to be less than the query.
 *
 * @author Frank Fock
 * @version 1.0
 */
public class MOScopeComparator implements Comparator {

  public MOScopeComparator() {
  }

  /**
   * Compares a scope with another scope or query. See also the class
   * description how comparison is done.
   * @param o1
   *   a MOscope or MOQuery instance.
   * @param o2
   *   a MOscope or MOQuery instance.
   * @return
   *   an integer less than zero if <code>o1</code> is less than <code>o2</code>
   *   and zero if both values are deemed to be equal and a value greater than
   *   zero if <code>o1</code> is greater than <code>o2</code>.
   */
  public int compare(Object o1, Object o2) {
    if (o1 == o2) {
      return 0; // ensure identity is equal
    }
    int result = 0;
    if (o2 instanceof MOQuery) {
      if (!(o1 instanceof MOScope)) {
        result = compare(((MOQuery)o1).getScope(), ((MOQuery)o2).getScope());
      }
      else {
        result = compareScopeAndQuery((MOScope) o1, (MOQuery) o2);
      }
    }
    else if (o1 instanceof MOQuery) {
      result = -compareScopeAndQuery((MOScope)o2, (MOQuery)o1);
    }
    else {
      MOScope s1 = (MOScope) o1;
      MOScope s2 = (MOScope) o2;
      if ((s1 instanceof MOContextScope) &&
          (s2 instanceof MOContextScope)) {
        OctetString c1 = ((MOContextScope) s1).getContext();
        OctetString c2 = ((MOContextScope) s2).getContext();
        if ((c1 != null) && (c2 != null)) {
          result = c1.compareTo(c2);
        }
      }
      if (result == 0) {
        result = s1.getLowerBound().compareTo(s2.getLowerBound());
        if (result == 0) {
          if (s1 instanceof MOContextScope) {
            result = -1;
          }
          else {
            result = 1;
          }
        }
      }
    }
    return result;
  }

  private static int compareScopeAndQuery(MOScope scope, MOQuery query) {
    int result = 0;
    if (scope instanceof MOContextScope) {
      OctetString c1 = ((MOContextScope)scope).getContext();
      OctetString c2 = query.getScope().getContext();
      if ((c1 != null) && (c2 != null)) {
        result = c1.compareTo(c2);
      }
    }
    if (result != 0) {
      return result;
    }
    if (scope.getUpperBound() == null) {
      return 1;
    }
    else {
      result =
          scope.getUpperBound().compareTo(query.getScope().getLowerBound());
      if (result == 0) {
        if ((!scope.isUpperIncluded()) ||
            (!query.getScope().isLowerIncluded())) {
          return -1;
        }
      }
    }
    return result;
  }

  public boolean equals(Object obj) {
    return (this == obj);
  }

  public int hashCode() {
    return super.hashCode();
  }
}
