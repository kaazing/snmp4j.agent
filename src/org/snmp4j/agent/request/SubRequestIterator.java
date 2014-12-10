/*_############################################################################
  _## 
  _##  SNMP4J-Agent - SubRequestIterator.java  
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


package org.snmp4j.agent.request;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Frank Fock
 * @version 1.0
 */

public interface SubRequestIterator extends Iterator {

  /**
   * Gets the next sub-request that is pending.
   * @return
   *    an unprocessed <code>SnmpSubRequest</code> instance.
   */
  public SubRequest nextSubRequest() throws NoSuchElementException;

  /**
   * Returns <code>true</code> if there are more sub-requests to process.
   * In other words, returns <code>true</code> if next would return an element
   * rather than throwing an exception.
   * @return
   *    <code>true</code> if there are more sub-requests.
   */
  public boolean hasNext();

}
