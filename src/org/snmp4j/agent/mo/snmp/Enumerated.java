/*_############################################################################
  _## 
  _##  SNMP4J-Agent - Enumerated.java  
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

import org.snmp4j.agent.mo.MOMutableColumn;
import org.snmp4j.agent.MOAccess;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.Integer32;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.agent.mo.snmp.smi.EnumerationConstraint;
import org.snmp4j.smi.OctetString;

/**
 * The <code>Enumerated</code> class represents enumerated SMI INTEGER
 * (={@link Integer32}) or an OCTET STRING with enumerated named bits for
 * columnar objects. The latter represents the SMI construct BITS.
 *
 * @author Frank Fock
 * @version 1.2
 */
public class Enumerated extends MOMutableColumn {

  private EnumerationConstraint constraint;

  /**
   * Creates an enumerated INTEGER column with specifying a set of possible
   * values.
   * @param columnID
   *    the column ID (sub-identifier) of the column.
   * @param access
   *    the maximum access for this column.
   * @param defaultValue
   *    the default value used for new rows.
   * @param mutableInService
   *    specifies whether this column can be changed while in service (active).
   * @param allowedValues
   *    an array of possible values for this object.
   */
  public Enumerated(int columnID,
                    MOAccess access,
                    Integer32 defaultValue,
                    boolean mutableInService,
                    int[] allowedValues) {
    super(columnID, SMIConstants.SYNTAX_INTEGER,
          access, defaultValue, mutableInService);
    this.constraint = new EnumerationConstraint(allowedValues);
  }

  /**
   * Creates an enumerated INTEGER column. To constraint the possible values
   * assignable to this object, you will have to set the corrsponding
   * {@link EnumerationConstraint} with {@link #setConstraint} or use an
   * appropriate value validation listener.
   * @param columnID
   *    the column ID (sub-identifier) of the column.
   * @param access
   *    the maximum access for this column.
   * @param defaultValue
   *    the default value used for new rows.
   * @param mutableInService
   *    specifies whether this column can be changed while in service (active).
   */
  public Enumerated(int columnID,
                    MOAccess access,
                    Integer32 defaultValue,
                    boolean mutableInService) {
    super(columnID, SMIConstants.SYNTAX_INTEGER,
          access, defaultValue, mutableInService);
  }

  /**
   * Creates an enumerated INTEGER column. To constraint the possible values
   * assignable to this object, you will have to set the corrsponding
   * {@link EnumerationConstraint} with {@link #setConstraint} or use an
   * appropriate value validation listener.
   * @param columnID
   *    the column ID (sub-identifier) of the column.
   * @param access
   *    the maximum access for this column.
   * @param defaultValue
   *    the default value used for new rows.
   */
  public Enumerated(int columnID,
                    MOAccess access,
                    Integer32 defaultValue) {
    super(columnID, SMIConstants.SYNTAX_INTEGER,
          access, defaultValue);
  }

  /**
   * Creates a BITS column with specifying a set of possible bit values.
   * @param columnID
   *    the column ID (sub-identifier) of the column.
   * @param access
   *    the maximum access for this column.
   * @param defaultValue
   *    the default value used for new rows.
   * @param mutableInService
   *    specifies whether this column can be changed while in service (active).
   * @param allowedValues
   *    an array of possible bit values for this object.
   * @since 1.2
   */
  public Enumerated(int columnID,
                    MOAccess access,
                    OctetString defaultValue,
                    boolean mutableInService,
                    int[] allowedValues) {
    super(columnID, SMIConstants.SYNTAX_OCTET_STRING,
          access, defaultValue, mutableInService);
    this.constraint = new EnumerationConstraint(allowedValues);
  }

  /**
   * Creates a BITS column. To constraint the possible values
   * assignable to this object, you will have to set the corrsponding
   * {@link EnumerationConstraint} with {@link #setConstraint} or use an
   * appropriate value validation listener.
   * @param columnID
   *    the column ID (sub-identifier) of the column.
   * @param access
   *    the maximum access for this column.
   * @param defaultValue
   *    the default value used for new rows.
   * @param mutableInService
   *    specifies whether this column can be changed while in service (active).
   * @since 1.2
   */
  public Enumerated(int columnID,
                    MOAccess access,
                    OctetString defaultValue,
                    boolean mutableInService) {
    super(columnID, SMIConstants.SYNTAX_OCTET_STRING,
          access, defaultValue, mutableInService);
  }

  /**
   * Creates a BITS column. To constraint the possible values
   * assignable to this object, you will have to set the corrsponding
   * {@link EnumerationConstraint} with {@link #setConstraint} or use an
   * appropriate value validation listener.
   * @param columnID
   *    the column ID (sub-identifier) of the column.
   * @param access
   *    the maximum access for this column.
   * @param defaultValue
   *    the default value used for new rows.
   */
  public Enumerated(int columnID,
                    MOAccess access,
                    OctetString defaultValue) {
    super(columnID, SMIConstants.SYNTAX_OCTET_STRING,
          access, defaultValue);
  }

  public synchronized int validate(Variable newValue, Variable oldValue) {
    int result = super.validate(newValue, oldValue);
    if ((constraint != null) &&
        (result == SnmpConstants.SNMP_ERROR_SUCCESS)) {
      return constraint.validate(newValue);
    }
    return result;
  }

  protected void setConstraint(EnumerationConstraint constraint) {
    this.constraint = constraint;
  }

}
