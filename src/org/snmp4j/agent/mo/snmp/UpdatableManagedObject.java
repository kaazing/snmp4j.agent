/*_############################################################################
  _## 
  _##  SNMP4J-Agent - UpdatableManagedObject.java  
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

package org.snmp4j.agent.mo.snmp;

import org.snmp4j.agent.*;

/**
 * An updatable ManagedObject can be externally triggered to update its state
 * by calling its {@link #update()} method.
 *
 * @author Frank Fock
 * @version 1.0
 *
 * @deprecated This interface had not a clear definition and had not used by
 * SNMP4J-Agent at all. For updatable managed objects use the new interface
 * {@link org.snmp4j.agent.UpdatableManagedObject} instead.
 */
public interface UpdatableManagedObject extends ManagedObject {

  /**
   * Updates the internal state of the managed object, for example to
   * before a request is processed or if an external event needs to update
   * the data. Another example is updating a TimeStamp object.
   */
  void update();

}
