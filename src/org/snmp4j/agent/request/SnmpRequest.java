/*_############################################################################
  _##
  _##  SNMP4J-Agent - SnmpRequest.java
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

import java.util.*;

import org.snmp4j.*;
import org.snmp4j.agent.security.*;
import org.snmp4j.mp.*;
import org.snmp4j.smi.*;
import org.snmp4j.agent.DefaultMOContextScope;
import org.snmp4j.agent.MOScope;
import org.snmp4j.agent.ManagedObject;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import org.snmp4j.agent.mo.snmp.CoexistenceInfo;
import org.snmp4j.agent.MOQuery;

/**
 * The <code>SnmpRequest</code> class implements requests from a SNMP source.
 *
 * @author Frank Fock
 * @version 1.2
 */
public class SnmpRequest extends AbstractRequest {

  private static final LogAdapter logger =
      LogFactory.getLogger(SnmpRequest.class);

  public static final OctetString DEFAULT_CONTEXT = new OctetString();

  private CommandResponderEvent requestEvent;
  private CoexistenceInfo coexistenceInfo;

  private PDU response;
  private OctetString viewName;

  private static int nextTransactionID = 0;

  protected Map processingUserObjects;

  public SnmpRequest(CommandResponderEvent request, CoexistenceInfo cinfo) {
    this.requestEvent = request;
    this.coexistenceInfo = cinfo;
    correctRequestValues();
    this.transactionID = nextTransactionID();
  }

  public static synchronized int nextTransactionID() {
    return nextTransactionID++;
  }

  protected synchronized void setupSubRequests() {
    int capacity = requestEvent.getPDU().size();
    int totalRepetitions = (requestEvent.getPDU() instanceof PDUv1) ? 0 :
        repeaterRowSize*requestEvent.getPDU().getMaxRepetitions();
    subrequests = new ArrayList(capacity + totalRepetitions);
    if (response == null) {
      response = createResponse();
    }
    for (int i=0; i<requestEvent.getPDU().size(); i++) {
      SnmpSubRequest subReq =
          new SnmpSubRequest(requestEvent.getPDU().get(i), i);
      addSubRequest(subReq);
    }
    if (logger.isDebugEnabled()) {
      logger.debug("SnmpSubRequests initialized: "+subrequests);
    }
  }

  /**
   * Returns the number of repetitions that are complete.
   * @return
   *    the minimum <code>r</code> for which all
   *    <code>i&lt;r*(pduSize-nonRepeaters)</code> {@link SubRequest}s
   *    returned by {@link #get(int i)} return true on
   *    {@link SubRequest#isComplete()}.
   */
  public synchronized int getCompleteRepetitions() {
    int i = 0;
    for (Iterator it = subrequests.iterator(); it.hasNext(); i++) {
      SnmpSubRequest sreq = (SnmpSubRequest) it.next();
      if (!sreq.isComplete()) {
        return i/getRepeaterCount();
      }
    }
    return i/getRepeaterCount();
  }

  public int getMaxRepetitions() {
    return requestEvent.getPDU().getMaxRepetitions();
  }

  public int getNonRepeaters() {
    return requestEvent.getPDU().getNonRepeaters();
  }

  private void addSubRequest(SubRequest subReq) {
    subrequests.add(subReq);
    response.add(subReq.getVariableBinding());
  }

  protected int getMaxPhase() {
    return (is2PC()) ? PHASE_2PC_CLEANUP : PHASE_1PC;
  }

/*
  public int indexOf(OID oid) {
    /**@todo Diese org.snmp4j.agent.request.Request-Methode implementieren/
    throw new java.lang.UnsupportedOperationException("Methode indexOf() noch nicht implementiert.");
  }

  public int indexOf(OID oid, int startFrom) {

  }
*/

  public int size() {
    return requestEvent.getPDU().size();
  }

  public Object getSource() {
    return requestEvent;
  }

  public CommandResponderEvent getInitiatingEvent() {
    return requestEvent;
  }

  public void setRequestEvent(CommandResponderEvent requestEvent) {
    this.requestEvent = requestEvent;
  }

  protected void assignErrorStatus2Response() {
    int errStatus = getErrorStatus();
    if (requestEvent.getMessageProcessingModel() == MessageProcessingModel.MPv1) {
      switch (errStatus) {
        case SnmpConstants.SNMP_ERROR_NOT_WRITEABLE:
        case SnmpConstants.SNMP_ERROR_NO_ACCESS:
        case SnmpConstants.SNMP_ERROR_NO_CREATION:
        case SnmpConstants.SNMP_ERROR_INCONSISTENT_NAME: {
          response.setErrorStatus(SnmpConstants.SNMP_ERROR_NO_SUCH_NAME);
          break;
        }
        case SnmpConstants.SNMP_ERROR_AUTHORIZATION_ERROR:
        case SnmpConstants.SNMP_ERROR_RESOURCE_UNAVAILABLE:
        case SnmpConstants.SNMP_ERROR_COMMIT_FAILED:
        case SnmpConstants.SNMP_ERROR_UNDO_FAILED: {
          response.setErrorStatus(SnmpConstants.SNMP_ERROR_GENERAL_ERROR);
          break;
        }
        case SnmpConstants.SNMP_ERROR_WRONG_VALUE:
        case SnmpConstants.SNMP_ERROR_WRONG_LENGTH:
        case SnmpConstants.SNMP_ERROR_INCONSISTENT_VALUE:
        case SnmpConstants.SNMP_ERROR_WRONG_TYPE: {
          response.setErrorStatus(SnmpConstants.SNMP_ERROR_BAD_VALUE);
          break;
        }
        default: {
          response.setErrorStatus(errStatus);
        }
      }
      for (int i=0; i<response.size(); i++) {
        VariableBinding vb = response.get(i);
        if (vb.isException()) {
          response.setErrorStatus(PDU.noSuchName);
          response.setErrorIndex(i+1);
          response.set(i, new VariableBinding(vb.getOid()));
          return;
        }
      }
    }
    else {
      response.setErrorStatus(errStatus);
    }
    response.setErrorIndex(getErrorIndex());
  }

  private PDU createResponse() {
    PDU resp = (PDU) requestEvent.getPDU().clone();
    resp.clear();
    resp.setType(PDU.RESPONSE);
    resp.setRequestID(requestEvent.getPDU().getRequestID());
    resp.setErrorIndex(0);
    resp.setErrorStatus(PDU.noError);
    return resp;
  }

  private void correctRequestValues() {
    PDU request = requestEvent.getPDU();
    if (!(request instanceof PDUv1)) {
      if (request.getMaxRepetitions() < 0) {
        request.setMaxRepetitions(0);
      }
      if (request.getNonRepeaters() < 0) {
        request.setNonRepeaters(0);
      }
      repeaterStartIndex = request.getNonRepeaters();
      repeaterRowSize =
          Math.max(request.size() - repeaterStartIndex, 0);
    }
    else {
      repeaterStartIndex = 0;
      repeaterRowSize = request.size();
    }
  }

  public PDU getResponsePDU() {
    return (PDU) getResponse();
  }

  public Object getResponse() {
    if (response == null) {
      response = createResponse();
    }
    assignErrorStatus2Response();
    return response;
  }

  /**
   * iterator
   *
   * @return Iterator
   */
  public Iterator iterator() {
    initSubRequests();
    return new SnmpSubRequestIterator();
  }

  protected boolean is2PC() {
    return (requestEvent.getPDU().getType() == PDU.SET);
  }

  public OctetString getContext() {
    if (coexistenceInfo != null) {
      return coexistenceInfo.getContextName();
    }
    else if (requestEvent.getPDU() instanceof ScopedPDU) {
      return ((ScopedPDU)requestEvent.getPDU()).getContextName();
    }
    return DEFAULT_CONTEXT;
  }

  public OctetString getViewName() {
    return viewName;
  }

  public void setViewName(OctetString viewName) {
    this.viewName = viewName;
  }

  public int getSecurityLevel() {
    return requestEvent.getSecurityLevel();
  }

  public int getSecurityModel() {
    return requestEvent.getSecurityModel();
  }

  public OctetString getSecurityName() {
    if (coexistenceInfo != null) {
      return coexistenceInfo.getSecurityName();
    }
    return new OctetString(requestEvent.getSecurityName());
  }

  public int getViewType() {
    return getViewType(requestEvent.getPDU().getType());
  }

  /**
   * Returns the VACM view type for the supplied PDU type.
   * @param pduType
   *    a PDU type.
   * @return
   *    the corresponding VACM view type.
   */
  public static final int getViewType(int pduType) {
    switch (pduType) {
      case PDU.GETNEXT:
      case PDU.GET:
      case PDU.GETBULK: {
        return VACM.VIEW_READ;
      }
      case PDU.INFORM:
      case PDU.TRAP:
      case PDU.V1TRAP: {
        return VACM.VIEW_NOTIFY;
      }
      default: {
        return VACM.VIEW_WRITE;
      }
    }
  }

  protected synchronized void addRepeaterSubRequest() {
    int predecessorIndex = subrequests.size() - repeaterRowSize;
    SnmpSubRequest sreq =
        new SnmpSubRequest((SnmpSubRequest)subrequests.get(predecessorIndex),
                           subrequests.size());
    addSubRequest(sreq);
    if (logger.isDebugEnabled()) {
      logger.debug("Added sub request '"+sreq+"' to response '"+response+"'");
    }
  }

  public int getErrorIndex() {
    if (errorStatus == SnmpConstants.SNMP_ERROR_SUCCESS) {
      return 0;
    }
    initSubRequests();
    int index = 1;
    for (Iterator it = subrequests.iterator(); it.hasNext(); index++) {
      SubRequest sreq = (SubRequest) it.next();
      if (sreq.getStatus().getErrorStatus() != SnmpConstants.SNMP_ERROR_SUCCESS) {
        return index;
      }
    }
    return 0;
  }


  public int getTransactionID() {
    return transactionID;
  }

  public CoexistenceInfo getCoexistenceInfo() {
    return coexistenceInfo;
  }

  /**
   * Returns the last repetition row that is complete (regarding the number
   * of elements in the row) before the given subrequest index.
   * @param upperBoundIndex
   *    the maximum sub-request index within the row to return.
   * @return
   *    a sub list of the sub-requests list that contains the row's elements.
   *    If no such row exists <code>null</code> is returned.
   */
  private List lastRow(int upperBoundIndex) {
    if ((repeaterRowSize == 0) || (upperBoundIndex <= repeaterStartIndex)) {
      return null;
    }
    int rows = (upperBoundIndex - repeaterStartIndex) / repeaterRowSize;
    int startIndex = repeaterStartIndex + (repeaterRowSize*(rows-1));
    int endIndex = repeaterStartIndex + (repeaterRowSize*rows);
    if ((startIndex < repeaterStartIndex) || (endIndex > subrequests.size())) {
      return null;
    }
    return subrequests.subList(startIndex, endIndex);
  }

  public int getMessageProcessingModel() {
    return this.requestEvent.getMessageProcessingModel();
  }

  public int getRepeaterCount() {
    PDU reqPDU = requestEvent.getPDU();
    return Math.max(reqPDU.size() - reqPDU.getNonRepeaters(), 0);
  }

  public boolean isPhaseComplete() {
    if (errorStatus == SnmpConstants.SNMP_ERROR_SUCCESS) {
      initSubRequests();
      for (Iterator it = subrequests.iterator(); it.hasNext(); ) {
        SubRequest subreq = (SubRequest) it.next();
        RequestStatus status = subreq.getStatus();
        if (status.getErrorStatus() != SnmpConstants.SNMP_ERROR_SUCCESS) {
          return true;
        }
        else if (!status.isPhaseComplete()) {
          return false;
        }
      }
    }
    if (requestEvent.getPDU().getType() == PDU.GETBULK) {
      SnmpSubRequestIterator it =
          new SnmpSubRequestIterator(subrequests.size(), 1);
      return !it.hasNext();
    }
    return true;
  }

  public boolean isBulkRequest() {
    return (requestEvent.getPDU().getType() == PDU.GETBULK);
  }

  public synchronized Object getProcessingUserObject(Object key) {
    if (processingUserObjects != null) {
      return processingUserObjects.get(key);
    }
    return null;
  }

  public synchronized Object setProcessingUserObject(Object key, Object value) {
    if (processingUserObjects == null) {
      processingUserObjects = new HashMap(5);
    }
    return processingUserObjects.put(key, value);
  }

  /**
   *
   * @author Frank Fock
   * @version 1.0
   */
  public class SnmpSubRequestIterator implements SubRequestIterator {

    private int cursor = 0;
    private int increment = 1;
    private boolean noAppending;

    protected SnmpSubRequestIterator() {
      this.cursor = 0;
    }

    protected SnmpSubRequestIterator(int offset, int increment) {
      this.cursor = offset;
      this.increment = increment;
    }

    protected void setNoAppending(boolean noAppending) {
      this.noAppending = noAppending;
    }

    /**
     * hasNext
     *
     * @return boolean
     */
    public boolean hasNext() {
      synchronized (SnmpRequest.this) {
        PDU reqPDU = requestEvent.getPDU();
        if (reqPDU.getType() == PDU.GETBULK) {
          if (noAppending && (cursor >= subrequests.size())) {
            return false;
          }
          if (cursor < Math.min(reqPDU.size(), reqPDU.getNonRepeaters())) {
            return true;
          }
          else {
            if (cursor < reqPDU.getNonRepeaters() +
                reqPDU.getMaxRepetitions() * getRepeaterCount()) {
              List lastRow = lastRow(cursor);
              if (lastRow != null) {
                boolean allEndOfMibView = true;
                SubRequest sreq = null;
                for (Iterator it = lastRow.iterator(); it.hasNext(); ) {
                  sreq = (SubRequest) it.next();
                  if (sreq.getVariableBinding().getSyntax() !=
                      SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
                    allEndOfMibView = false;
                    break;
                  }
                }
                if (allEndOfMibView) {
                  // truncate request if already more elements are there
                  if ((sreq != null) &&
                      (sreq.getIndex() < subrequests.size())) {
                    int lastElementIndex = sreq.getIndex();
                    List tail = subrequests.subList(lastElementIndex + 1,
                        subrequests.size());
                    tail.clear();
                    tail = response.getVariableBindings().
                        subList(lastElementIndex + 1, response.size());
                    tail.clear();
                  }
                  return false;
                }
              }
              return (response.getBERLength() <
                      requestEvent.getMaxSizeResponsePDU());
            }
            else if ((reqPDU.getNonRepeaters() == 0) &&
                     (reqPDU.getMaxRepetitions() == 0)) {
              SnmpRequest.this.subrequests.clear();
              if (response != null) {
                while (response.size() > 0) {
                  response.remove(0);
                }
              }
            }
          }
          return false;
        }
        return (cursor < reqPDU.size());
      }
    }

    public SubRequest nextSubRequest() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if ((requestEvent.getPDU().getType() == PDU.GETBULK) &&
          (cursor >= subrequests.size())) {
        while (cursor >= subrequests.size()) {
          addRepeaterSubRequest();
        }
      }
      SubRequest sreq = (SubRequest) subrequests.get(cursor);
      cursor += increment;
      return sreq;
    }

    public void remove() {
      throw new UnsupportedOperationException("Remove is not supported "+
                                              "on sub-requests");
    }

    public Object next() {
      return nextSubRequest();
    }

    public boolean equals(Object other) {
      if (other instanceof Request) {
        return ((Request)other).getTransactionID() == getTransactionID();
      }
      return false;
    }

    public int hashCode() {
      return getTransactionID();
    }
  }


  /**
   *
   * @author Frank Fock
   * @version 1.0
   */
  public class SnmpSubRequest
      implements org.snmp4j.agent.request.SnmpSubRequest, RequestStatusListener {

    private RequestStatus status;
    private VariableBinding vb;
    private Object undoValue;
    private MOScope scope;
    private ManagedObject targetMO;
    private MOQuery query;
    private int index;

    private volatile Object userObject;

    protected SnmpSubRequest(VariableBinding subrequest, int index) {
      this.vb = subrequest;
      this.index = index;
      switch (requestEvent.getPDU().getType()) {
        case PDU.GETBULK:
        case PDU.GETNEXT: {
          this.scope = getNextScope(new OID(this.vb.getOid()));
          break;
        }
        default: {
          OID oid = this.vb.getOid();
          this.scope = new DefaultMOContextScope(getContext(),
                                                 oid, true, oid, true);
        }
      }
      status = new RequestStatus();
      status.addRequestStatusListener(this);
      if (logger.isDebugEnabled()) {
        logger.debug("Created subrequest "+index+" with scope "+scope+
                     " from "+subrequest);
      }
    }

    protected MOScope getNextScope(OID previousOID) {
      return new DefaultMOContextScope(getContext(), previousOID, false,
                                       null, false);
    }

    protected SnmpSubRequest(SnmpSubRequest predecessor, int index) {
      this(new VariableBinding(predecessor.getVariableBinding().getOid()),
           index);
//    Do not copy queries because they need to be updated externally only!
//    this.query = predecessor.getQuery();
    }

    public Request getRequest() {
      return SnmpRequest.this;
    }

    public RequestStatus getStatus() {
      return status;
    }

    public VariableBinding getVariableBinding() {
      return vb;
    }

    public void setStatus(RequestStatus status) {
      this.status = status;
    }

    public Object getUndoValue() {
      return undoValue;
    }

    public void setUndoValue(Object undoInformation) {
      this.undoValue = undoInformation;
    }

    public void requestStatusChanged(RequestStatusEvent event) {
      int newStatus = event.getStatus().getErrorStatus();
      setErrorStatus(newStatus);
      if (logger.isDebugEnabled() &&
          (newStatus != SnmpConstants.SNMP_ERROR_SUCCESS)) {
        new Exception("Error '"+
                      PDU.toErrorStatusText(event.getStatus().getErrorStatus())+
                      "' generated at: "+vb).printStackTrace();
      }
    }

    public MOScope getScope() {
      return scope;
    }

    public void completed() {
      status.setPhaseComplete(true);
    }

    public boolean hasError() {
      return getStatus().getErrorStatus() != SnmpConstants.SNMP_ERROR_SUCCESS;
    }

    public boolean isComplete() {
      return status.isPhaseComplete();
    }

    public void setTargetMO(ManagedObject managedObject) {
      this.targetMO = managedObject;
    }

    public ManagedObject getTargetMO() {
      return targetMO;
    }

    public SnmpRequest getSnmpRequest() {
      return SnmpRequest.this;
    }

    public void setErrorStatus(int errorStatus) {
      SnmpRequest.this.setErrorStatus(errorStatus);
    }

    public int getIndex() {
      return index;
    }

    public void setQuery(MOQuery query) {
      this.query = query;
    }

    public MOQuery getQuery() {
      return query;
    }

    public String toString() {
      return getClass().getName()+"[scope="+scope+
          ",vb="+vb+",status="+status+",query="+query+",index="+index+
          ",targetMO="+targetMO+"]";
    }

    public SubRequestIterator repetitions() {
      return repetitions(false);
    }

    private SubRequestIterator repetitions(boolean noAppending) {
      initSubRequests();
      if (isBulkRequest()) {
        int repeaters = requestEvent.getPDU().size() -
            requestEvent.getPDU().getNonRepeaters();
        SnmpSubRequestIterator it =
            new SnmpSubRequestIterator(getIndex(), repeaters);
        it.setNoAppending(noAppending);
        return it;
      }
      return new SubRequestIteratorSupport(Collections.EMPTY_LIST.iterator());
    }

    public void updateNextRepetition() {
      if (!isBulkRequest()) {
        return;
      }
      this.query = null;
      SubRequestIterator repetitions = repetitions(true);
      // skip this one
      repetitions.next();
      while (repetitions.hasNext()) {
        SnmpSubRequest nsreq = (SnmpSubRequest) repetitions.nextSubRequest();
        if ((getStatus().getErrorStatus() == PDU.noError) &&
            (!this.vb.isException())) {
          nsreq.query = null;
          nsreq.scope = getNextScope(this.vb.getOid());
          nsreq.getVariableBinding().setOid(this.vb.getOid());
        }
        else if (this.vb.isException()) {
            nsreq.query = null;
            nsreq.getVariableBinding().setOid(this.vb.getOid());
            nsreq.getVariableBinding().setVariable(this.vb.getVariable());
            nsreq.getStatus().setPhaseComplete(true);
        }
      }
    }

    public final int getErrorStatus() {
      return getStatus().getErrorStatus();
    }

    public Object getUserObject() {
      return userObject;
    }

    public void setUserObject(Object userObject) {
      this.userObject = userObject;
    }

  }

}

